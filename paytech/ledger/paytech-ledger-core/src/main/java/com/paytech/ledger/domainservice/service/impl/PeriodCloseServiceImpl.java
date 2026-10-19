package com.paytech.ledger.domainservice.service.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.account.api.enums.BalanceBucket;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.api.enums.LedgerOwnerType;
import com.paytech.ledger.core.dal.dataobject.AccountingInstruction;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.BookSequence;
import com.paytech.ledger.core.dal.dataobject.ChartAccount;
import com.paytech.ledger.core.dal.dataobject.CloseApproval;
import com.paytech.ledger.core.dal.dataobject.CloseStep;
import com.paytech.ledger.core.dal.dataobject.InstructionLine;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.PeriodClose;
import com.paytech.ledger.core.dal.dataobject.PeriodCloseBook;
import com.paytech.ledger.core.enums.AccountStatus;
import com.paytech.ledger.core.enums.CloseStepStatus;
import com.paytech.ledger.core.enums.CloseStepType;
import com.paytech.ledger.core.enums.PeriodCloseStatus;
import com.paytech.ledger.core.enums.PeriodStatus;
import com.paytech.ledger.domain.PeriodCloseContentHash;
import com.paytech.ledger.domain.PeriodCloseContext;
import com.paytech.ledger.domain.PeriodReopenContentHash;
import com.paytech.ledger.domain.PeriodReopenContext;
import com.paytech.ledger.domain.PeriodReopenPlan;
import com.paytech.ledger.domain.view.PeriodCloseDetailView;
import com.paytech.ledger.domain.view.PeriodCloseView;
import com.paytech.ledger.domainservice.repository.AccountingInstructionRepository;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.BookSequenceRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.CloseApprovalRepository;
import com.paytech.ledger.domainservice.repository.CloseStepRepository;
import com.paytech.ledger.domainservice.repository.InstructionLineRepository;
import com.paytech.ledger.domainservice.repository.JournalEntryRepository;
import com.paytech.ledger.domainservice.repository.LedgerAccountRepository;
import com.paytech.ledger.domainservice.repository.PeriodCloseBookRepository;
import com.paytech.ledger.domainservice.repository.PeriodCloseRepository;
import com.paytech.ledger.domainservice.service.PeriodCloseService;
import com.paytech.ledger.domainservice.validator.account.AccountIdentityRule;

import lombok.AllArgsConstructor;

/** 关账与重开流程的本地保存与查询；所有状态迁移带期望旧状态与版本。 */
@Service
@AllArgsConstructor
public class PeriodCloseServiceImpl implements PeriodCloseService {
    private static final int ACTIVE_SLOT = 1;

    private final Clock clock;
    private final IdGenerator idGenerator;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final CloseStepRepository closeStepRepository;
    private final TransactionTemplate transactionTemplate;
    private final PeriodCloseRepository periodCloseRepository;
    private final BookSequenceRepository bookSequenceRepository;
    private final ChartAccountRepository chartAccountRepository;
    private final JournalEntryRepository journalEntryRepository;
    private final CloseApprovalRepository closeApprovalRepository;
    private final InstructionLineRepository instructionLineRepository;
    private final PeriodCloseBookRepository periodCloseBookRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final AccountingInstructionRepository accountingInstructionRepository;

    /** 期间 OPEN → CLOSING 与流程登记、水位冻结同一事务提交。 */
    @Override
    public void start(PeriodCloseContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            AccountingPeriod period = lockPeriod(context.getPeriod().getId());
            if (period.getStatus() != PeriodStatus.OPEN) {
                throw new BizCheckFailException("只有 OPEN 期间可以发起关账: " + period.getId() + ", 状态 "
                    + period.getStatus().getCode());
            }
            context.getClose().setCloseNo(periodCloseRepository.nextCloseNo(period.getId()));
            periodCloseRepository.insert(context.getClose());
            accountingPeriodRepository.updateStatus(context.getPeriod(), PeriodStatus.CLOSING);
            for (PeriodCloseBook book : context.getCloseBooks()) {
                book.setAsOfSequence(lockWatermark(book.getLedgerBookId()));
            }
            periodCloseBookRepository.insertBatch(context.getCloseBooks());
            closeStepRepository.insertBatch(context.getSteps());
            closeStepRepository.complete(context.step(CloseStepType.LOCK_SEQUENCE), CloseStepStatus.SUCCEEDED,
                PeriodCloseContentHash.sha256(watermarkSummary(context.getCloseBooks())));
        });
    }

    /** 以排他锁读取期间；不存在视为系统错误（事务前已确认存在）。 */
    AccountingPeriod lockPeriod(long periodId) {
        AccountingPeriod period = accountingPeriodRepository.lockById(periodId);
        if (period == null) {
            throw new ErrorException("会计期间在关账过程中消失: " + periodId);
        }
        return period;
    }

    /** 以共享锁读取账簿序号行，等待在途过账提交后取水位。 */
    long lockWatermark(long ledgerBookId) {
        BookSequence sequence = bookSequenceRepository.lockSharedByLedgerBook(ledgerBookId);
        if (sequence == null) {
            throw new ErrorException("账簿缺少序号行: " + ledgerBookId);
        }
        return sequence.getNextValue() - 1;
    }

    /** 步骤 1 的结果摘要文本：按账簿主键升序的水位。 */
    String watermarkSummary(List<PeriodCloseBook> closeBooks) {
        StringBuilder summary = new StringBuilder("LOCK_SEQUENCE");
        closeBooks.forEach(book -> summary.append('|').append(book.getLedgerBookId()).append('=')
            .append(book.getAsOfSequence()));
        return summary.toString();
    }

    /** 单步骤迁移，条件更新保护。 */
    @Override
    public void succeedStep(CloseStep step, String resultSummary) {
        closeStepRepository.complete(step, CloseStepStatus.SUCCEEDED, PeriodCloseContentHash.sha256(resultSummary));
    }

    /** 步骤、流程与期间三项状态同一事务回退。 */
    @Override
    public void fail(PeriodCloseContext context, CloseStep step, String resultSummary) {
        transactionTemplate.executeWithoutResult(status -> {
            lockPeriod(context.getPeriod().getId());
            closeStepRepository.complete(step, CloseStepStatus.FAILED, PeriodCloseContentHash.sha256(resultSummary));
            periodCloseRepository.updateStatus(context.getClose(), PeriodCloseStatus.FAILED, null, null, null);
            accountingPeriodRepository.updateStatus(context.getPeriod(), PeriodStatus.OPEN);
        });
    }

    /** 主体自身的本期损益账户；身份唯一键保护并发创建。 */
    @Override
    public LedgerAccount ensureResultAccount(LedgerBook book, long resultChartAccountId) {
        requireTransaction();
        LedgerOwnerType ownerType = AccountIdentityRule.bookOwnerType(book);
        LedgerAccount existing = ledgerAccountRepository.findByIdentity(book.getId(), resultChartAccountId, ownerType,
            book.getEntityId(), BalanceBucket.NONE);
        if (existing != null) {
            return existing;
        }
        LedgerAccount account = newAccount(book, chartAccountRepository.findById(resultChartAccountId), ownerType);
        ledgerAccountRepository.insert(account);
        return account;
    }

    /** 构造 ACTIVE 账户身份，正常方向取自科目；余额由分录聚合得出。 */
    LedgerAccount newAccount(LedgerBook book, ChartAccount chartAccount, LedgerOwnerType ownerType) {
        Instant now = now();
        LedgerAccount account = new LedgerAccount();
        account.setId(idGenerator.nextId());
        account.setLedgerBookId(book.getId());
        account.setChartAccountId(chartAccount.getId());
        account.setOwnerType(ownerType);
        account.setOwnerId(book.getEntityId());
        account.setBalanceBucket(BalanceBucket.NONE);
        account.setNormalSide(chartAccount.getNormalSide());
        account.setAccountStatus(AccountStatus.ACTIVE);
        account.setCreateTime(now);
        account.setUpdateTime(now);
        return account;
    }

    /** 唯一键（来源系统、事件键、指令类型）阻止同一流程同一账簿重复生成结转指令。 */
    @Override
    public void saveProfitCloseInstruction(AccountingInstruction instruction, List<InstructionLine> lines) {
        requireTransaction();
        accountingInstructionRepository.insert(instruction);
        instructionLineRepository.insertBatch(lines);
    }

    /** 只允许 LOCKED 且尚无结转指令的账簿证据被推进。 */
    @Override
    public void recordProfitClosePosting(PeriodCloseBook book, long journalSequence, long instructionId) {
        requireTransaction();
        periodCloseBookRepository.recordPosting(book, journalSequence, instructionId);
    }

    /** 条件更新保护。 */
    @Override
    public void finalizeBook(PeriodCloseBook book, long finalTrialRunId) {
        periodCloseBookRepository.finalizeBook(book, finalTrialRunId);
    }

    /** 流程迁移与 Maker 记录同一事务；活动槽保持占用。 */
    @Override
    public void freeze(PeriodCloseContext context, String closeHash, CloseApproval makerApproval) {
        transactionTemplate.executeWithoutResult(status -> {
            periodCloseRepository.updateStatus(context.getClose(), PeriodCloseStatus.PENDING_CHECK, ACTIVE_SLOT,
                closeHash, null);
            closeApprovalRepository.insert(makerApproval);
        });
    }

    /** 锁期间后复验账簿证据，再完成流程并关闭期间。 */
    @Override
    public void approve(PeriodCloseContext context, CloseApproval checkerApproval) {
        transactionTemplate.executeWithoutResult(status -> {
            lockPeriod(context.getPeriod().getId());
            if (periodCloseBookRepository.countInvalidFinalBooks(context.getClose().getId()) > 0) {
                throw new ErrorException("关账账簿证据不完整，不能完成关账: " + context.getClose().getId());
            }
            closeApprovalRepository.insert(checkerApproval);
            periodCloseRepository.updateStatus(context.getClose(), PeriodCloseStatus.COMPLETED, null,
                context.getClose().getCloseHash(), checkerApproval.getActorId());
            accountingPeriodRepository.updateStatus(context.getPeriod(), PeriodStatus.CLOSED);
        });
    }

    /** 驳回记录、流程终态与期间回退同一事务。 */
    @Override
    public void reject(PeriodCloseContext context, CloseApproval checkerApproval) {
        transactionTemplate.executeWithoutResult(status -> {
            lockPeriod(context.getPeriod().getId());
            closeApprovalRepository.insert(checkerApproval);
            periodCloseRepository.updateStatus(context.getClose(), PeriodCloseStatus.REJECTED, null,
                context.getClose().getCloseHash(), checkerApproval.getActorId());
            accountingPeriodRepository.updateStatus(context.getPeriod(), PeriodStatus.OPEN);
        });
    }

    /** 原结转指令行不可变，按账簿主键升序逐个反向。 */
    @Override
    public Map<Long, List<InstructionLine>> planReopenReversal(long reopensCloseId) {
        Map<Long, List<InstructionLine>> plan = new TreeMap<>();
        for (PeriodCloseBook book : periodCloseBookRepository.findByClose(reopensCloseId)) {
            Long instructionId = book.getPostingInstructionId();
            plan.put(book.getLedgerBookId(), instructionId == null ? List.of()
                : PeriodReopenPlan.reverse(instructionLineRepository.findByInstruction(instructionId)));
        }
        return plan;
    }

    /** 期间 CLOSED → REOPENING、流程登记、水位冻结、步骤 1—2 与摘要冻结同一事务提交。 */
    @Override
    public void startReopen(PeriodReopenContext context, CloseApproval makerApproval) {
        transactionTemplate.executeWithoutResult(status -> {
            AccountingPeriod period = lockPeriod(context.getPeriod().getId());
            if (period.getStatus() != PeriodStatus.CLOSED) {
                throw new BizCheckFailException("只有 CLOSED 期间可以发起重开: " + period.getId() + ", 状态 "
                    + period.getStatus().getCode());
            }
            insertReopen(context);
            freezeReopen(context, makerApproval);
        });
    }

    /** 插入重开流程根、迁移期间、冻结水位，插入账簿证据与步骤并完成步骤 1—2。 */
    void insertReopen(PeriodReopenContext context) {
        context.getClose().setCloseNo(periodCloseRepository.nextCloseNo(context.getPeriod().getId()));
        periodCloseRepository.insert(context.getClose());
        accountingPeriodRepository.updateStatus(context.getPeriod(), PeriodStatus.REOPENING);
        for (PeriodCloseBook book : context.getCloseBooks()) {
            book.setAsOfSequence(lockWatermark(book.getLedgerBookId()));
        }
        periodCloseBookRepository.insertBatch(context.getCloseBooks());
        closeStepRepository.insertBatch(context.getSteps());
        closeStepRepository.complete(context.step(CloseStepType.LOCK_SEQUENCE), CloseStepStatus.SUCCEEDED,
            PeriodCloseContentHash.sha256(watermarkSummary(context.getCloseBooks())));
        closeStepRepository.complete(context.step(CloseStepType.PLAN_REOPEN_REVERSAL), CloseStepStatus.SUCCEEDED,
            PeriodCloseContentHash.sha256(PeriodReopenContentHash.planText(context.getReversalLines())));
    }

    /** 冻结重开摘要：流程 RUNNING → PENDING_CHECK（活动槽保持占用），Maker 记录确认同一摘要。 */
    void freezeReopen(PeriodReopenContext context, CloseApproval makerApproval) {
        String closeHash = PeriodReopenContentHash.compute(context);
        periodCloseRepository.updateStatus(context.getClose(), PeriodCloseStatus.PENDING_CHECK, ACTIVE_SLOT, closeHash,
            null);
        makerApproval.setApprovedCloseHash(closeHash);
        closeApprovalRepository.insert(makerApproval);
    }

    /** 锁后当前读复验 REOPENING；期间状态与上下文不一致视为系统错误。 */
    @Override
    public void lockReopeningPeriod(PeriodReopenContext context) {
        requireTransaction();
        requireReopening(lockPeriod(context.getPeriod().getId()));
    }

    /** 只允许 LOCKED 且尚无冲回指令的账簿证据被冻结。 */
    @Override
    public void finalizeReopenBook(PeriodCloseBook book, Long instructionId) {
        requireTransaction();
        periodCloseBookRepository.finalizeReopenBook(book, instructionId);
    }

    /** 步骤、审批记录、流程终态与期间重开计数一起写入调用方事务。 */
    @Override
    public void completeReopen(PeriodReopenContext context, CloseApproval checkerApproval, String postSummary) {
        requireTransaction();
        closeStepRepository.complete(context.step(CloseStepType.POST_REOPEN_REVERSAL), CloseStepStatus.SUCCEEDED,
            PeriodCloseContentHash.sha256(postSummary));
        closeApprovalRepository.insert(checkerApproval);
        periodCloseRepository.updateStatus(context.getClose(), PeriodCloseStatus.COMPLETED, null,
            context.getClose().getCloseHash(), checkerApproval.getActorId());
        accountingPeriodRepository.completeReopen(context.getPeriod());
    }

    /** 驳回记录、步骤跳过、流程终态与期间回到 CLOSED 同一事务。 */
    @Override
    public void rejectReopen(PeriodCloseContext context, CloseApproval checkerApproval) {
        transactionTemplate.executeWithoutResult(status -> {
            requireReopening(lockPeriod(context.getPeriod().getId()));
            closeApprovalRepository.insert(checkerApproval);
            closeStepRepository.complete(context.step(CloseStepType.POST_REOPEN_REVERSAL), CloseStepStatus.SKIPPED, null);
            periodCloseRepository.updateStatus(context.getClose(), PeriodCloseStatus.REJECTED, null,
                context.getClose().getCloseHash(), checkerApproval.getActorId());
            accountingPeriodRepository.updateStatus(context.getPeriod(), PeriodStatus.CLOSED);
        });
    }

    /** 待审批的重开流程所在期间必须处于 REOPENING。 */
    void requireReopening(AccountingPeriod period) {
        if (period.getStatus() != PeriodStatus.REOPENING) {
            throw new ErrorException("待审批重开流程的期间不在 REOPENING: " + period.getId() + ", 状态 "
                + period.getStatus().getCode());
        }
    }

    /** 复核只需要期间、流程根、账簿证据与步骤。 */
    @Override
    public PeriodCloseContext loadForReview(long periodId, int closeNo) {
        AccountingPeriod period = getPeriod(periodId);
        PeriodClose close = periodCloseRepository.findByPeriodAndNo(periodId, closeNo);
        if (close == null) {
            throw new BizCheckFailException("关账流程不存在: 期间 " + periodId + ", 序号 " + closeNo);
        }
        return new PeriodCloseContext(period, List.of(), Map.of(), close,
            periodCloseBookRepository.findByClose(close.getId()), closeStepRepository.findByClose(close.getId()));
    }

    /** 未过账指令计数。 */
    @Override
    public long countPendingInstructions(PeriodCloseContext context) {
        return accountingInstructionRepository.countPendingByBooks(context.bookIds());
    }

    /** 借贷不平或无行的分录计数。 */
    @Override
    public long countUnbalancedEntries(PeriodCloseContext context) {
        return journalEntryRepository.countUnbalancedInPeriod(context.getPeriod().getId(), context.bookIds());
    }

    /** 待执行或可重试的补偿任务计数。 */
    @Override
    public long countPendingCompensationEvents() {
        return periodCloseRepository.countPendingCompensationEvents();
    }

    /** 期间存在后分页读取。 */
    @Override
    public PagingResponseBody<PeriodCloseView> pageCloses(long periodId, PagingRequestBody paging) {
        getPeriod(periodId);
        List<PeriodCloseView> items = periodCloseRepository.findViewPage(periodId, paging.size(), paging.offset());
        return new PagingResponseBody<>(items, paging.page(), paging.size(), periodCloseRepository.countByPeriod(periodId));
    }

    /** 组合流程摘要、步骤、账簿证据与审批记录。 */
    @Override
    public PeriodCloseDetailView getClose(long periodId, int closeNo) {
        getPeriod(periodId);
        PeriodCloseView summary = periodCloseRepository.findView(periodId, closeNo);
        if (summary == null) {
            throw new BizCheckFailException("关账流程不存在: 期间 " + periodId + ", 序号 " + closeNo);
        }
        PeriodCloseDetailView detail = new PeriodCloseDetailView();
        detail.setSummary(summary);
        detail.setSteps(closeStepRepository.findByClose(summary.getPeriodCloseId()).stream().map(this::step).toList());
        detail.setBooks(periodCloseBookRepository.findByClose(summary.getPeriodCloseId()).stream().map(this::book).toList());
        detail.setApprovals(
            closeApprovalRepository.findByClose(summary.getPeriodCloseId()).stream().map(this::approval).toList());
        return detail;
    }

    /** 复制步骤字段。 */
    PeriodCloseDetailView.Step step(CloseStep source) {
        PeriodCloseDetailView.Step step = new PeriodCloseDetailView.Step();
        step.setStepNo(source.getStepNo());
        step.setStepType(source.getStepType());
        step.setStatus(source.getStatus());
        step.setResultHash(source.getResultHash());
        step.setCompletedTime(source.getCompletedTime());
        return step;
    }

    /** 复制账簿证据字段。 */
    PeriodCloseDetailView.Book book(PeriodCloseBook source) {
        PeriodCloseDetailView.Book book = new PeriodCloseDetailView.Book();
        book.setLedgerBookId(source.getLedgerBookId());
        book.setAsOfSequence(source.getAsOfSequence());
        book.setFinalTrialRunId(source.getFinalTrialRunId());
        book.setPostingInstructionId(source.getPostingInstructionId());
        book.setStatus(source.getStatus());
        return book;
    }

    /** 复制审批记录字段。 */
    PeriodCloseDetailView.Approval approval(CloseApproval source) {
        PeriodCloseDetailView.Approval approval = new PeriodCloseDetailView.Approval();
        approval.setApprovalRole(source.getApprovalRole());
        approval.setActorId(source.getActorId());
        approval.setDecision(source.getDecision());
        approval.setApprovedCloseHash(source.getApprovedCloseHash());
        approval.setDecisionTime(source.getDecisionTime());
        return approval;
    }

    /** 读取期间，不存在时业务拒绝。 */
    AccountingPeriod getPeriod(long periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId);
        if (period == null) {
            throw new BizCheckFailException("会计期间不存在: " + periodId);
        }
        return period;
    }

    /** 参与型写入只能加入调用方事务。 */
    void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new ErrorException("关账结转写入必须加入调用方事务");
        }
    }

    /** 微秒精度的当前 UTC 时间。 */
    Instant now() {
        return clock.instant().truncatedTo(ChronoUnit.MICROS);
    }
}
