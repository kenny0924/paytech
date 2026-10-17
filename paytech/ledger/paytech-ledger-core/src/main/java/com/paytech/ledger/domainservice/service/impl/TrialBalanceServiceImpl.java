package com.paytech.ledger.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.id.IdGenerator;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.ledger.core.dal.dataobject.Account;
import com.paytech.ledger.core.dal.dataobject.AccountingPeriod;
import com.paytech.ledger.core.dal.dataobject.BookSequence;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.LedgerPeriodBalance;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceLine;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceRun;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.TrialBalanceStatus;
import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.core.enums.TrialExceptionType;
import com.paytech.ledger.core.enums.TrialReferenceType;
import com.paytech.ledger.domain.TrialBalanceContentHash;
import com.paytech.ledger.domain.TrialBalanceContext;
import com.paytech.ledger.domain.view.AccountProjectionCheckView;
import com.paytech.ledger.domain.view.TrialBalanceAggregateView;
import com.paytech.ledger.domain.view.TrialBalanceDetailView;
import com.paytech.ledger.domain.view.TrialBalanceFindingView;
import com.paytech.ledger.domain.view.TrialBalanceLineView;
import com.paytech.ledger.domain.view.TrialBalanceRunView;
import com.paytech.ledger.domainservice.repository.AccountRepository;
import com.paytech.ledger.domainservice.repository.AccountingPeriodRepository;
import com.paytech.ledger.domainservice.repository.BookSequenceRepository;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.ledger.domainservice.repository.JournalLineRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.repository.LedgerPeriodBalanceRepository;
import com.paytech.ledger.domainservice.repository.TrialBalanceExceptionRepository;
import com.paytech.ledger.domainservice.repository.TrialBalanceLineRepository;
import com.paytech.ledger.domainservice.repository.TrialBalanceRunRepository;
import com.paytech.ledger.domainservice.service.TrialBalanceService;

import lombok.AllArgsConstructor;

/**
 * 试算平衡执行与查询
 * <li>执行：序号行共享锁 → 聚合科目余额 → 核对账户投影 → 计算借贷合计与异常 → 冻结摘要 → 写入终态；只在 SUCCEEDED 时刷新期间余额。</li>
 * <li>查询：不加锁、不开事务，金额按账簿币种精度补齐。</li>
 */
@Service
@AllArgsConstructor
public class TrialBalanceServiceImpl implements TrialBalanceService {
    private final Clock clock;
    private final IdGenerator idGenerator;
    private final AccountRepository accountRepository;
    private final TransactionTemplate transactionTemplate;
    private final LedgerBookRepository ledgerBookRepository;
    private final JournalLineRepository journalLineRepository;
    private final BookSequenceRepository bookSequenceRepository;
    private final CurrencySpecRepository currencySpecRepository;
    private final TrialBalanceRunRepository trialBalanceRunRepository;
    private final AccountingPeriodRepository accountingPeriodRepository;
    private final TrialBalanceLineRepository trialBalanceLineRepository;
    private final LedgerPeriodBalanceRepository ledgerPeriodBalanceRepository;
    private final TrialBalanceExceptionRepository trialBalanceExceptionRepository;

    /** 全部步骤在一个事务内完成，任一步失败整体回滚。 */
    @Override
    public void run(TrialBalanceContext context) {
        transactionTemplate.executeWithoutResult(
            status -> execute(context, lockWatermark(context.getBook().getId()), false));
    }

    /** 与 run 相同的计算，只是水位由调用方给出，并排除水位之后已有过账的账户的投影核对。 */
    @Override
    public void runAt(TrialBalanceContext context, long asOfSequence) {
        transactionTemplate.executeWithoutResult(status -> execute(context, asOfSequence, true));
    }

    /** 聚合科目余额、核对账户投影、计算合计与异常并保存终态；必须在事务内调用。 */
    void execute(TrialBalanceContext context, long asOfSequence, boolean skipAdvancedAccounts) {
        Instant now = clock.instant().truncatedTo(ChronoUnit.MICROS);
        AccountingPeriod period = context.getPeriod();
        List<TrialBalanceAggregateView> aggregates = journalLineRepository.findTrialAggregates(
            context.getBook().getId(), asOfSequence, period.getStartDate(), period.getEndDate());
        List<TrialBalanceLine> lines = buildLines(context, aggregates, now);
        List<TrialBalanceException> exceptions = projectionExceptions(context, asOfSequence, now);
        if (skipAdvancedAccounts) {
            exceptions = excludeAdvancedAccounts(exceptions, asOfSequence);
        }
        TrialBalanceRun run = buildRun(context, asOfSequence, aggregates, lines, exceptions, now);
        saveRun(context, run, lines, exceptions);
    }

    /** 去掉最后过账序号已超过水位的账户异常：其投影包含水位之后的分录，无法在该水位比较。 */
    List<TrialBalanceException> excludeAdvancedAccounts(List<TrialBalanceException> exceptions, long asOfSequence) {
        List<Long> accountIds = exceptions.stream().map(TrialBalanceException::getReferenceId).toList();
        if (accountIds.isEmpty()) {
            return exceptions;
        }
        Set<Long> advanced = accountRepository.findByIds(accountIds).stream()
            .filter(account -> account.getLastJournalSequence() > asOfSequence).map(Account::getId)
            .collect(Collectors.toSet());
        return new ArrayList<>(exceptions.stream().filter(e -> !advanced.contains(e.getReferenceId())).toList());
    }

    /** 以共享锁读取账簿序号行，水位为下一可分配序号减一。 */
    long lockWatermark(long ledgerBookId) {
        BookSequence sequence = bookSequenceRepository.lockSharedByLedgerBook(ledgerBookId);
        if (sequence == null) {
            throw new ErrorException("账簿缺少序号行: " + ledgerBookId);
        }
        return sequence.getNextValue() - 1;
    }

    /** 由科目聚合结果构造试算行，期末 = 期初 + 正常方向净额；金额按币种精度规范。 */
    List<TrialBalanceLine> buildLines(TrialBalanceContext context, List<TrialBalanceAggregateView> aggregates,
        Instant now) {
        List<TrialBalanceLine> lines = new ArrayList<>();
        for (TrialBalanceAggregateView aggregate : aggregates) {
            TrialBalanceLine line = new TrialBalanceLine();
            line.setId(idGenerator.nextId());
            line.setChartAccountId(aggregate.getChartAccountId());
            line.setOpeningBalance(scale(aggregate.getOpeningBalance(), context));
            line.setPeriodDebit(scale(aggregate.getPeriodDebit(), context));
            line.setPeriodCredit(scale(aggregate.getPeriodCredit(), context));
            line.setClosingBalance(line.getOpeningBalance().add(
                net(aggregate.getNormalSide(), line.getPeriodDebit(), line.getPeriodCredit())));
            line.setCreateTime(now);
            lines.add(line);
        }
        return lines;
    }

    /** 账户投影与水位内全量分录重算不一致时记录总账异常，差额取三项绝对差的最大值。 */
    List<TrialBalanceException> projectionExceptions(TrialBalanceContext context, long asOfSequence, Instant now) {
        List<TrialBalanceException> exceptions = new ArrayList<>();
        for (AccountProjectionCheckView check : accountRepository.findProjectionChecks(context.getBook().getId(),
            asOfSequence)) {
            BigDecimal netDiff = check.getNetBalance()
                .subtract(net(check.getNormalSide(), check.getJournalDebit(), check.getJournalCredit())).abs();
            BigDecimal difference = check.getDebitTotal().subtract(check.getJournalDebit()).abs()
                .max(check.getCreditTotal().subtract(check.getJournalCredit()).abs()).max(netDiff);
            if (difference.signum() != 0) {
                exceptions.add(exception(TrialReferenceType.ACCOUNT, check.getAccountId(), difference, context, now));
            }
        }
        return exceptions;
    }

    /** 计算借贷合计；不等时追加科目表级异常；有异常即 FAILED；冻结内容摘要。 */
    TrialBalanceRun buildRun(TrialBalanceContext context, long asOfSequence, List<TrialBalanceAggregateView> aggregates,
        List<TrialBalanceLine> lines, List<TrialBalanceException> exceptions, Instant now) {
        TrialBalanceRun run = new TrialBalanceRun();
        run.setId(idGenerator.nextId());
        run.setLedgerBookId(context.getBook().getId());
        run.setPeriodId(context.getPeriod().getId());
        run.setRunNo(trialBalanceRunRepository.nextRunNo(run.getLedgerBookId(), run.getPeriodId()));
        run.setAsOfSequence(asOfSequence);
        applyTotals(run, aggregates, lines, context);
        if (run.getTotalDebit().compareTo(run.getTotalCredit()) != 0) {
            exceptions.add(exception(TrialReferenceType.CHART_ACCOUNT, context.getBook().getChartId(),
                run.getTotalDebit().subtract(run.getTotalCredit()).abs(), context, now));
        }
        run.setStatus(exceptions.isEmpty() ? TrialBalanceStatus.SUCCEEDED : TrialBalanceStatus.FAILED);
        lines.forEach(line -> line.setRunId(run.getId()));
        exceptions.forEach(exception -> exception.setRunId(run.getId()));
        run.setContentHash(TrialBalanceContentHash.compute(run, lines, exceptions, context.getMinorUnit()));
        run.setCreateTime(now);
        run.setUpdateTime(now);
        return run;
    }

    /**
     * 期末有符号余额按实际借贷方向拆分：正常方向正余额计入同侧，负余额的绝对值计入对侧
     * <li>行与聚合结果一一对应、顺序相同，正常方向取自聚合结果。</li>
     */
    void applyTotals(TrialBalanceRun run, List<TrialBalanceAggregateView> aggregates, List<TrialBalanceLine> lines,
        TrialBalanceContext context) {
        BigDecimal debit = BigDecimal.ZERO;
        BigDecimal credit = BigDecimal.ZERO;
        for (int i = 0; i < lines.size(); i++) {
            TrialBalanceLine line = lines.get(i);
            boolean debitNormal = aggregates.get(i).getNormalSide() == NormalSide.DEBIT;
            boolean positive = line.getClosingBalance().signum() >= 0;
            if (debitNormal == positive) {
                debit = debit.add(line.getClosingBalance().abs());
            } else {
                credit = credit.add(line.getClosingBalance().abs());
            }
        }
        run.setTotalDebit(scale(debit, context));
        run.setTotalCredit(scale(credit, context));
    }

    /** 保存运行、行与异常；成功时刷新期间余额，并写回 Context。 */
    void saveRun(TrialBalanceContext context, TrialBalanceRun run, List<TrialBalanceLine> lines,
        List<TrialBalanceException> exceptions) {
        trialBalanceRunRepository.insert(run);
        trialBalanceLineRepository.insertBatch(lines);
        trialBalanceExceptionRepository.insertBatch(exceptions);
        if (run.getStatus() == TrialBalanceStatus.SUCCEEDED) {
            ledgerPeriodBalanceRepository.upsert(lines.stream().map(line -> periodBalance(run, line)).toList());
        }
        context.setRun(run);
        context.setLines(lines);
        context.setExceptions(exceptions);
    }

    /** 由成功试算行构造期间余额投影行。 */
    LedgerPeriodBalance periodBalance(TrialBalanceRun run, TrialBalanceLine line) {
        LedgerPeriodBalance balance = new LedgerPeriodBalance();
        balance.setId(idGenerator.nextId());
        balance.setPeriodId(run.getPeriodId());
        balance.setLedgerBookId(run.getLedgerBookId());
        balance.setChartAccountId(line.getChartAccountId());
        balance.setOpeningBalance(line.getOpeningBalance());
        balance.setPeriodDebit(line.getPeriodDebit());
        balance.setPeriodCredit(line.getPeriodCredit());
        balance.setClosingBalance(line.getClosingBalance());
        balance.setAsOfSequence(run.getAsOfSequence());
        balance.setTrialBalanceRunId(run.getId());
        balance.setCreateTime(run.getCreateTime());
        balance.setUpdateTime(run.getUpdateTime());
        return balance;
    }

    /** 构造 OPEN 状态的总账异常。 */
    TrialBalanceException exception(TrialReferenceType referenceType, long referenceId, BigDecimal difference,
        TrialBalanceContext context, Instant now) {
        TrialBalanceException exception = new TrialBalanceException();
        exception.setId(idGenerator.nextId());
        exception.setExceptionType(TrialExceptionType.GENERAL_LEDGER_MISMATCH);
        exception.setReferenceType(referenceType);
        exception.setReferenceId(referenceId);
        exception.setDifferenceAmount(scale(difference, context));
        exception.setStatus(TrialExceptionStatus.OPEN);
        exception.setCreateTime(now);
        exception.setUpdateTime(now);
        return exception;
    }

    /** 按正常方向计算净额：借方正常为借减贷，贷方正常为贷减借。 */
    BigDecimal net(NormalSide normalSide, BigDecimal debit, BigDecimal credit) {
        return normalSide == NormalSide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
    }

    /** 按币种精度规范金额；数据库金额由过账内核保证不超精度。 */
    BigDecimal scale(BigDecimal value, TrialBalanceContext context) {
        return value.setScale(context.getMinorUnit());
    }

    /** 账簿存在时返回，否则业务拒绝。 */
    @Override
    public LedgerBook getBook(long ledgerBookId) {
        LedgerBook book = ledgerBookRepository.findById(ledgerBookId);
        if (book == null) {
            throw new BizCheckFailException("账簿不存在: " + ledgerBookId);
        }
        return book;
    }

    /** 期间存在时返回，否则业务拒绝。 */
    @Override
    public AccountingPeriod getPeriod(long periodId) {
        AccountingPeriod period = accountingPeriodRepository.findById(periodId);
        if (period == null) {
            throw new BizCheckFailException("会计期间不存在: " + periodId);
        }
        return period;
    }

    /** 分页读取摘要并规范金额。 */
    @Override
    public PagingResponseBody<TrialBalanceRunView> pageRuns(long ledgerBookId, long periodId, PagingRequestBody paging) {
        int minorUnit = minorUnit(getBook(ledgerBookId));
        getPeriod(periodId);
        List<TrialBalanceRunView> runs = trialBalanceRunRepository.findPage(ledgerBookId, periodId, paging.size(),
            paging.offset());
        runs.forEach(run -> scaleRun(run, minorUnit));
        return new PagingResponseBody<>(runs, paging.page(), paging.size(),
            trialBalanceRunRepository.countPage(ledgerBookId, periodId));
    }

    /** 读取摘要、行与异常并规范金额。 */
    @Override
    public TrialBalanceDetailView getRun(long ledgerBookId, long periodId, int runNo) {
        int minorUnit = minorUnit(getBook(ledgerBookId));
        getPeriod(periodId);
        TrialBalanceRunView run = trialBalanceRunRepository.findView(ledgerBookId, periodId, runNo);
        if (run == null) {
            throw new BizCheckFailException("试算不存在: 账簿 " + ledgerBookId + ", 期间 " + periodId + ", 序号 " + runNo);
        }
        scaleRun(run, minorUnit);
        List<TrialBalanceLineView> lines = trialBalanceLineRepository.findViews(run.getTrialBalanceRunId());
        lines.forEach(line -> {
            line.setOpeningBalance(line.getOpeningBalance().setScale(minorUnit));
            line.setPeriodDebit(line.getPeriodDebit().setScale(minorUnit));
            line.setPeriodCredit(line.getPeriodCredit().setScale(minorUnit));
            line.setClosingBalance(line.getClosingBalance().setScale(minorUnit));
        });
        List<TrialBalanceFindingView> findings = trialBalanceExceptionRepository.findViews(run.getTrialBalanceRunId());
        findings.forEach(finding -> finding.setDifferenceAmount(finding.getDifferenceAmount().setScale(minorUnit)));
        return new TrialBalanceDetailView(run, lines, findings);
    }

    /** 规范摘要金额。 */
    void scaleRun(TrialBalanceRunView run, int minorUnit) {
        run.setTotalDebit(run.getTotalDebit().setScale(minorUnit));
        run.setTotalCredit(run.getTotalCredit().setScale(minorUnit));
    }

    /** 账簿币种小数位。 */
    int minorUnit(LedgerBook book) {
        return currencySpecRepository.findByCurrency(book.getCurrency()).getMinorUnit();
    }
}
