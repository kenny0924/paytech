package com.paytech.ledger.service.processor;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.ledger.core.constant.AccountingCodes;
import com.paytech.ledger.core.constant.AccountingLocks;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.CloseAccountRule;
import com.paytech.ledger.core.dal.dataobject.CloseApproval;
import com.paytech.ledger.core.dal.dataobject.CloseStep;
import com.paytech.ledger.core.dal.dataobject.FiscalCalendar;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.JournalEntry;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.PeriodClose;
import com.paytech.ledger.core.dal.dataobject.PeriodCloseBook;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceRun;
import com.paytech.ledger.core.enums.ApprovalDecision;
import com.paytech.ledger.core.enums.ApprovalRole;
import com.paytech.ledger.core.enums.CloseBookStatus;
import com.paytech.ledger.core.enums.CloseStepStatus;
import com.paytech.ledger.core.enums.CloseStepType;
import com.paytech.ledger.core.enums.InstructionOrigin;
import com.paytech.ledger.core.enums.InstructionStatus;
import com.paytech.ledger.core.enums.JournalEntryType;
import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodCloseType;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.core.enums.TrialBalanceStatus;
import com.paytech.ledger.domain.JournalPostingContext;
import com.paytech.ledger.domain.PeriodCloseContentHash;
import com.paytech.ledger.domain.PeriodCloseContext;
import com.paytech.ledger.domain.ProfitClosePlan;
import com.paytech.ledger.domain.TrialBalanceContext;
import com.paytech.ledger.domain.view.PeriodCloseView;
import com.paytech.ledger.domain.view.ProfitCloseBalanceView;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.CloseAccountRuleRepository;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.ledger.domainservice.repository.FiscalCalendarRepository;
import com.paytech.ledger.domainservice.repository.JournalLineRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.repository.TrialBalanceExceptionRepository;
import com.paytech.ledger.domainservice.service.JournalPostingService;
import com.paytech.ledger.domainservice.service.PeriodCloseService;
import com.paytech.ledger.domainservice.service.TrialBalanceService;
import com.paytech.ledger.service.processor.request.StartPeriodCloseRequest;

import lombok.AllArgsConstructor;

/**
 * 发起期间关账并同步执行 7 个固定步骤
 * <li>事务前：期间存在且 OPEN、同日历更早期间全部 CLOSED（按顺序关账）、枚举期间主体的全部账簿。</li>
 * <li>步骤 1 在一个事务内推进 CLOSING、占用活动槽并冻结水位；步骤 2—7 各自短事务，任一步失败即流程 FAILED、期间回到 OPEN。</li>
 * <li>步骤 6 的损益结转在一个事务内生成 AUTO 指令（来源 FINANCE / PERIOD_CLOSE:{关账ID}:{账簿ID}，无规则 Bundle）并以 CLOSING 分录过账；财年最后期间同一指令追加本期损益转入留存收益的两行；没有任何结转行的账簿不生成指令。</li>
 * <li>全部通过后冻结 close_hash，流程进入 PENDING_CHECK，并写入 Maker 记录。</li>
 * <li>与审批、驳回共用期间资源锁；数据库期间行锁、活动槽唯一键与状态 CAS 仍是最终防线。</li>
 */
@Component
@AllArgsConstructor
public class StartPeriodCloseProcessor extends AbstractLockProcessor<StartPeriodCloseRequest, PeriodCloseView> {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final PeriodCloseService periodCloseService;
    private final TransactionTemplate transactionTemplate;
    private final TrialBalanceService trialBalanceService;
    private final LedgerBookRepository ledgerBookRepository;
    private final JournalLineRepository journalLineRepository;
    private final JournalPostingService journalPostingService;
    private final CurrencySpecRepository currencySpecRepository;
    private final FiscalCalendarRepository fiscalCalendarRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final CloseAccountRuleRepository closeAccountRuleRepository;
    private final TrialBalanceExceptionRepository trialBalanceExceptionRepository;

    /** 与同一期间的审批、驳回共用资源锁，未取得立即返回并发冲突。 */
    @Override
    protected LockCommand lockCommand(StartPeriodCloseRequest request) {
        return LockCommand.tryLock(AccountingLocks.periodClose(request.getPeriodId()));
    }

    @Override
    protected PeriodCloseView processWithinLock(StartPeriodCloseRequest request) {
        // 校验期间与关账顺序
        AccountingPeriod period = getOpenPeriod(request.getPeriodId());
        checkPriorPeriodsClosed(period);

        // 发起关账并执行步骤
        PeriodCloseContext context = buildContext(period, request.getMakerId());
        periodCloseService.start(context);
        if (runSteps(context)) {
            freeze(context);
        }
        return periodCloseService.getClose(period.getId(), context.getClose().getCloseNo()).getSummary();
    }

    /** 期间必须存在且 OPEN。 */
    AccountingPeriod getOpenPeriod(long periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId);
        if (period == null) {
            throw new BizCheckFailException("会计期间不存在: " + periodId);
        }
        if (period.getStatus() != PeriodStatus.OPEN) {
            throw new BizCheckFailException("只有 OPEN 期间可以发起关账: " + periodId + ", 状态 "
                + period.getStatus().getCode());
        }
        return period;
    }

    /** 同一日历中结束日期早于本期开始的期间必须全部 CLOSED。 */
    void checkPriorPeriodsClosed(AccountingPeriod period) {
        AccountingPeriod earlier = accountingPeriodRepository.findFirstUnclosedBefore(period.getCalendarId(),
            period.getStartDate());
        if (earlier != null) {
            throw new BizCheckFailException("前序会计期间尚未关闭: " + earlier.getId());
        }
    }

    /** 枚举期间主体的全部账簿，构造流程根、账簿证据与 7 个步骤。 */
    PeriodCloseContext buildContext(AccountingPeriod period, long makerId) {
        FiscalCalendar calendar = fiscalCalendarRepository.findById(period.getCalendarId());
        List<LedgerBook> books = ledgerBookRepository.findAllByEntity(calendar.getEntityType(), calendar.getEntityId());
        Map<Long, Integer> minorUnits = books.stream().collect(Collectors.toMap(LedgerBook::getId,
            book -> currencySpecRepository.findByCurrency(book.getCurrency()).getMinorUnit()));
        Instant now = now();
        PeriodClose close = buildClose(period, makerId, now);
        List<PeriodCloseBook> closeBooks = books.stream().map(book -> buildCloseBook(close, book, now)).toList();
        List<CloseStep> steps = new ArrayList<>();
        List<CloseStepType> types = CloseStepType.closeSteps();
        for (int i = 0; i < types.size(); i++) {
            steps.add(buildStep(close, i + 1, types.get(i), now));
        }
        return new PeriodCloseContext(period, books, minorUnits, close, closeBooks, steps);
    }

    /** RUNNING 流程根，占用活动槽；序号在发起事务内分配。 */
    PeriodClose buildClose(AccountingPeriod period, long makerId, Instant now) {
        PeriodClose close = new PeriodClose();
        close.setId(idGenerator.nextId());
        close.setPeriodId(period.getId());
        close.setCloseType(PeriodCloseType.CLOSE);
        close.setStatus(PeriodCloseStatus.RUNNING);
        close.setActiveSlot(1);
        close.setMakerId(makerId);
        close.setVersion(0L);
        close.setCreateTime(now);
        close.setUpdateTime(now);
        return close;
    }

    /** LOCKED 账簿证据；水位在发起事务内冻结。 */
    PeriodCloseBook buildCloseBook(PeriodClose close, LedgerBook book, Instant now) {
        PeriodCloseBook closeBook = new PeriodCloseBook();
        closeBook.setId(idGenerator.nextId());
        closeBook.setPeriodCloseId(close.getId());
        closeBook.setLedgerBookId(book.getId());
        closeBook.setAsOfSequence(0L);
        closeBook.setStatus(CloseBookStatus.LOCKED);
        closeBook.setCreateTime(now);
        closeBook.setUpdateTime(now);
        return closeBook;
    }

    /** PENDING 步骤。 */
    CloseStep buildStep(PeriodClose close, int stepNo, CloseStepType type, Instant now) {
        CloseStep step = new CloseStep();
        step.setId(idGenerator.nextId());
        step.setPeriodCloseId(close.getId());
        step.setStepNo(stepNo);
        step.setStepType(type);
        step.setStatus(CloseStepStatus.PENDING);
        step.setCreateTime(now);
        step.setUpdateTime(now);
        return step;
    }

    /**
     * 按顺序执行步骤 2—7
     * <li>业务性失败返回 false 并已回退期间；意外异常先回退期间与流程再原样抛出，保留原因。</li>
     *
     * @return 全部步骤成功时为 true
     */
    boolean runSteps(PeriodCloseContext context) {
        for (CloseStepType type : List.of(CloseStepType.CHECK_ASYNC_AND_JOURNAL, CloseStepType.RECONCILE_ACCOUNT_BALANCES,
            CloseStepType.GENERATE_TRIAL_BALANCE, CloseStepType.RESOLVE_EXCEPTIONS, CloseStepType.POST_PROFIT_CLOSE,
            CloseStepType.FINAL_TRIAL_BALANCE)) {
            StepOutcome outcome;
            try {
                outcome = execute(context, type);
            } catch (RuntimeException e) {
                periodCloseService.fail(context, context.step(type), type.getCode() + "|ERROR|" + e.getClass().getSimpleName());
                throw e;
            }
            if (!outcome.passed()) {
                periodCloseService.fail(context, context.step(type), outcome.summary());
                return false;
            }
            periodCloseService.succeedStep(context.step(type), outcome.summary());
        }
        return true;
    }

    /** 分派单个步骤。 */
    StepOutcome execute(PeriodCloseContext context, CloseStepType type) {
        return switch (type) {
            case CHECK_ASYNC_AND_JOURNAL -> checkAsyncAndJournal(context);
            case RECONCILE_ACCOUNT_BALANCES -> reconcileAccountBalances(context);
            case GENERATE_TRIAL_BALANCE -> generateTrialBalances(context);
            case RESOLVE_EXCEPTIONS -> checkExceptionsResolved(context);
            case POST_PROFIT_CLOSE -> postProfitClose(context);
            case FINAL_TRIAL_BALANCE -> finalTrialBalances(context);
            default -> throw new IllegalArgumentException("步骤 1 在发起事务内完成: " + type.getCode());
        };
    }

    /** 步骤 2：未过账指令、借贷不平分录与待执行补偿任务都必须为零。 */
    StepOutcome checkAsyncAndJournal(PeriodCloseContext context) {
        long pending = periodCloseService.countPendingInstructions(context);
        long unbalanced = periodCloseService.countUnbalancedEntries(context);
        long compensation = periodCloseService.countPendingCompensationEvents();
        String summary = "CHECK_ASYNC_AND_JOURNAL|pendingInstructions=" + pending + "|unbalancedEntries=" + unbalanced
            + "|pendingCompensationEvents=" + compensation;
        return new StepOutcome(pending == 0 && unbalanced == 0 && compensation == 0, summary);
    }

    /** 步骤 3：Treasury 未上线，渠道或银行账户存在非零余额即无法核对（Fail-Closed）；否则记录无核对对象。 */
    StepOutcome reconcileAccountBalances(PeriodCloseContext context) {
        long nonZero = periodCloseService.countNonZeroTreasuryAccounts(context);
        String result = nonZero == 0 ? "NO_RECONCILIATION_TARGET" : "TREASURY_RECONCILIATION_UNAVAILABLE";
        return new StepOutcome(nonZero == 0,
            "RECONCILE_ACCOUNT_BALANCES|nonZeroTreasuryAccounts=" + nonZero + "|" + result);
    }

    /** 步骤 4：每个账簿按冻结水位试算。 */
    StepOutcome generateTrialBalances(PeriodCloseContext context) {
        StringBuilder summary = new StringBuilder("GENERATE_TRIAL_BALANCE");
        for (LedgerBook book : context.getBooks()) {
            TrialBalanceRun run = runTrial(context, book);
            context.getTrialRuns().put(book.getId(), run);
            summary.append('|').append(book.getId()).append('=').append(run.getId()).append(':')
                .append(run.getStatus().getCode());
        }
        return new StepOutcome(true, summary.toString());
    }

    /** 步骤 5：步骤 4 的试算全部成功，且没有未解决异常。 */
    StepOutcome checkExceptionsResolved(PeriodCloseContext context) {
        boolean passed = true;
        StringBuilder summary = new StringBuilder("RESOLVE_EXCEPTIONS");
        for (LedgerBook book : context.getBooks()) {
            TrialBalanceRun run = context.getTrialRuns().get(book.getId());
            long unresolved = trialBalanceExceptionRepository.countUnresolvedByRun(run.getId());
            passed = passed && run.getStatus() == TrialBalanceStatus.SUCCEEDED && unresolved == 0;
            summary.append('|').append(book.getId()).append(":unresolved=").append(unresolved);
        }
        return new StepOutcome(passed, summary.toString());
    }

    /** 步骤 6：逐账簿损益结转，财年最后期间另结转本期损益至留存收益；缺少或冲突的结转规则时失败。 */
    StepOutcome postProfitClose(PeriodCloseContext context) {
        boolean yearEnd = isFiscalYearEnd(context.getPeriod());
        StringBuilder summary = new StringBuilder("POST_PROFIT_CLOSE");
        for (LedgerBook book : context.getBooks()) {
            PeriodCloseBook closeBook = context.closeBook(book.getId());
            List<ProfitCloseBalanceView> balances = journalLineRepository.findProfitCloseBalances(book.getId(),
                closeBook.getAsOfSequence(), context.getPeriod().getStartDate(), context.getPeriod().getEndDate());
            Map<Long, Long> resultChartBySource = resultChartBySource(context, book, balances);
            Map<Long, Long> retainedChartByResult = yearEnd ? retainedChartByResult(context, book) : Map.of();
            if (resultChartBySource == null || retainedChartByResult == null) {
                return new StepOutcome(false, summary.append('|').append(book.getId()).append("=MISSING_CLOSE_RULE").toString());
            }
            Long instructionId = transactionTemplate.execute(status -> postBookProfitClose(context, book, closeBook,
                balances, resultChartBySource, retainedChartByResult));
            summary.append('|').append(book.getId()).append('=').append(instructionId == null ? "NONE" : instructionId);
        }
        return new StepOutcome(true, summary.toString());
    }

    /** 财年最后期间：期间结束日的次日是财年起始月的 1 日。 */
    boolean isFiscalYearEnd(AccountingPeriod period) {
        FiscalCalendar calendar = fiscalCalendarRepository.findById(period.getCalendarId());
        LocalDate nextDay = period.getEndDate().plusDays(1);
        return nextDay.getDayOfMonth() == 1 && nextDay.getMonthValue() == calendar.getYearStartMonth();
    }

    /**
     * 年末结转映射：期间结束日生效的全部规则中，本期损益科目 → 留存收益科目
     *
     * @return 按本期损益科目升序的映射；同一本期损益科目映射到多个留存收益科目时返回空
     */
    Map<Long, Long> retainedChartByResult(PeriodCloseContext context, LedgerBook book) {
        Instant at = context.getPeriod().getEndDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<Long, Long> result = new TreeMap<>();
        for (CloseAccountRule rule : closeAccountRuleRepository.findAllEffective(book.getChartId(), at)) {
            Long existing = result.putIfAbsent(rule.getCurrentResultAccountId(), rule.getRetainedEarningsAccountId());
            if (existing != null && !existing.equals(rule.getRetainedEarningsAccountId())) {
                return null;
            }
        }
        return result;
    }

    /** 每个来源科目在期间结束日生效的结转规则的本期损益科目；任一缺失返回空。 */
    Map<Long, Long> resultChartBySource(PeriodCloseContext context, LedgerBook book,
        List<ProfitCloseBalanceView> balances) {
        Instant at = context.getPeriod().getEndDate().atStartOfDay(ZoneOffset.UTC).toInstant();
        Map<Long, Long> result = new HashMap<>();
        for (ProfitCloseBalanceView balance : balances) {
            CloseAccountRule rule = closeAccountRuleRepository.findEffective(book.getChartId(),
                balance.getChartAccountId(), at);
            if (rule == null) {
                return null;
            }
            result.put(balance.getChartAccountId(), rule.getCurrentResultAccountId());
        }
        return result;
    }

    /**
     * 一个事务内：确保本期损益账户、计算结转行（年末含转入留存收益行）、保存 AUTO 指令、以 CLOSING 分录过账并推进账簿证据水位
     *
     * @return 结转指令主键；没有任何结转行时为空
     */
    Long postBookProfitClose(PeriodCloseContext context, LedgerBook book, PeriodCloseBook closeBook,
        List<ProfitCloseBalanceView> balances, Map<Long, Long> resultChartBySource, Map<Long, Long> retainedChartByResult) {
        Map<Long, Long> resultAccountBySource = new HashMap<>();
        resultChartBySource.forEach((source, resultChart) -> resultAccountBySource.put(source,
            periodCloseService.ensureResultAccount(book, resultChart).getId()));
        List<ProfitClosePlan.Line> planned = new ArrayList<>(ProfitClosePlan.lines(balances, resultAccountBySource));
        planned.addAll(retainedEarningsLines(context, book, closeBook, planned, retainedChartByResult));
        if (planned.isEmpty()) {
            return null;
        }
        AccountingInstruction instruction = buildInstruction(context, book, planned);
        List<InstructionLine> lines = buildInstructionLines(instruction, planned);
        periodCloseService.saveProfitCloseInstruction(instruction, lines);
        JournalEntry entry = journalPostingService.post(new JournalPostingContext(instruction, lines,
            context.getPeriod().getId(), context.getPeriod().getEndDate(), JournalEntryType.CLOSING));
        periodCloseService.recordProfitClosePosting(closeBook, entry.getJournalSequence(), instruction.getId());
        return instruction.getId();
    }

    /**
     * 年末转入留存收益行：本期损益账户截至锁定水位的贷减借结余加本次结转行的净影响，非零时生成两行
     * <li>本期损益与留存收益账户均为账簿主体的 NONE 账户，不存在时按身份矩阵创建。</li>
     */
    List<ProfitClosePlan.Line> retainedEarningsLines(PeriodCloseContext context, LedgerBook book,
        PeriodCloseBook closeBook, List<ProfitClosePlan.Line> planned, Map<Long, Long> retainedChartByResult) {
        List<ProfitClosePlan.Line> lines = new ArrayList<>();
        retainedChartByResult.forEach((resultChart, retainedChart) -> {
            long resultAccountId = periodCloseService.ensureResultAccount(book, resultChart).getId();
            BigDecimal balance = journalLineRepository.findCreditNetUpTo(resultAccountId, closeBook.getAsOfSequence(),
                context.getPeriod().getEndDate()).add(ProfitClosePlan.creditNet(planned, resultAccountId));
            if (balance.signum() == 0) {
                return;
            }
            long retainedAccountId = periodCloseService.ensureResultAccount(book, retainedChart).getId();
            lines.addAll(ProfitClosePlan.retainedEarningsLines(resultAccountId, retainedAccountId, balance));
        });
        return lines;
    }

    /** 系统关账 AUTO 指令：来源 FINANCE、事件键 PERIOD_CLOSE:{关账ID}:{账簿ID}、无规则 Bundle，输入摘要覆盖结转行。 */
    AccountingInstruction buildInstruction(PeriodCloseContext context, LedgerBook book, List<ProfitClosePlan.Line> planned) {
        Instant now = now();
        AccountingInstruction instruction = new AccountingInstruction();
        instruction.setId(idGenerator.nextId());
        instruction.setLedgerBookId(book.getId());
        instruction.setInstructionType(AccountingCodes.PERIOD_PROFIT_CLOSE_INSTRUCTION);
        instruction.setOriginType(InstructionOrigin.AUTO);
        instruction.setSourceSystem(AccountingCodes.FINANCE_SOURCE_SYSTEM);
        instruction.setSourceEventKey("PERIOD_CLOSE:" + context.getClose().getId() + ":" + book.getId());
        instruction.setSourceVersion(1L);
        instruction.setInputHash(PeriodCloseContentHash.sha256(planText(planned)));
        instruction.setStatus(InstructionStatus.VALIDATED);
        instruction.setVersion(0L);
        instruction.setCreateTime(now);
        instruction.setUpdateTime(now);
        return instruction;
    }

    /** 结转行规范文本：按顺序拼接“账户|方向|金额”。 */
    String planText(List<ProfitClosePlan.Line> planned) {
        return planned.stream().map(line -> line.getAccountId() + "|" + line.getEntrySide().getCode() + "|"
            + line.getAmount().stripTrailingZeros().toPlainString()).collect(Collectors.joining("\n"));
    }

    /** 由结转行构造指令行，行号从 1 开始。 */
    List<InstructionLine> buildInstructionLines(AccountingInstruction instruction, List<ProfitClosePlan.Line> planned) {
        List<InstructionLine> lines = new ArrayList<>();
        for (int i = 0; i < planned.size(); i++) {
            InstructionLine line = new InstructionLine();
            line.setId(idGenerator.nextId());
            line.setInstructionId(instruction.getId());
            line.setLineNo(i + 1);
            line.setAccountId(planned.get(i).getAccountId());
            line.setEntrySide(planned.get(i).getEntrySide());
            line.setAmount(planned.get(i).getAmount());
            line.setCreateTime(instruction.getCreateTime());
            lines.add(line);
        }
        return lines;
    }

    /** 步骤 7：每个账簿按最终水位试算，必须成功后冻结。 */
    StepOutcome finalTrialBalances(PeriodCloseContext context) {
        StringBuilder summary = new StringBuilder("FINAL_TRIAL_BALANCE");
        for (LedgerBook book : context.getBooks()) {
            TrialBalanceRun run = runTrial(context, book);
            summary.append('|').append(book.getId()).append('=').append(run.getId()).append(':')
                .append(run.getStatus().getCode());
            if (run.getStatus() != TrialBalanceStatus.SUCCEEDED) {
                return new StepOutcome(false, summary.toString());
            }
            periodCloseService.finalizeBook(context.closeBook(book.getId()), run.getId());
            context.getFinalTrialHashes().put(book.getId(), run.getContentHash());
        }
        return new StepOutcome(true, summary.toString());
    }

    /** 按账簿证据当前水位执行一次试算。 */
    TrialBalanceRun runTrial(PeriodCloseContext context, LedgerBook book) {
        TrialBalanceContext trial = new TrialBalanceContext(book, context.getPeriod(),
            context.getMinorUnits().get(book.getId()));
        trialBalanceService.runAt(trial, context.closeBook(book.getId()).getAsOfSequence());
        return trial.getRun();
    }

    /** 冻结关账摘要并写入 Maker 记录。 */
    void freeze(PeriodCloseContext context) {
        String closeHash = PeriodCloseContentHash.compute(context.getClose(), context.getCloseBooks(),
            context.getSteps(), context.getFinalTrialHashes());
        CloseApproval maker = new CloseApproval();
        maker.setId(idGenerator.nextId());
        maker.setPeriodCloseId(context.getClose().getId());
        maker.setApprovalRole(ApprovalRole.MAKER);
        maker.setActorId(context.getClose().getMakerId());
        maker.setDecision(ApprovalDecision.APPROVED);
        maker.setApprovedCloseHash(closeHash);
        maker.setDecisionTime(now());
        maker.setCreateTime(maker.getDecisionTime());
        periodCloseService.freeze(context, closeHash, maker);
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }

    /** 单个步骤的结论与结果摘要文本。 */
    static final class StepOutcome {
        private final boolean passed;
        private final String summary;

        StepOutcome(boolean passed, String summary) {
            this.passed = passed;
            this.summary = summary;
        }

        /** 步骤是否通过。 */
        boolean passed() {
            return passed;
        }

        /** 结果摘要文本，写入步骤前计算 SHA-256。 */
        String summary() {
            return summary;
        }
    }
}
