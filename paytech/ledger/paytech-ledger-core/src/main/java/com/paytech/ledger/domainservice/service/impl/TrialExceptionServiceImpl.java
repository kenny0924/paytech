package com.paytech.ledger.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.domain.ProjectionRebuildContentHash;
import com.paytech.ledger.domain.ProjectionSnapshot;
import com.paytech.ledger.domain.view.AccountJournalTotalsView;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.ledger.domainservice.repository.LedgerAccountRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.repository.TrialBalanceExceptionRepository;
import com.paytech.ledger.domainservice.service.TrialExceptionService;

import lombok.AllArgsConstructor;

/**
 * 试算异常处置的本地保存；状态迁移以原状态为条件
 * <li>第 11b 步起 ledger 不保存余额投影（design/04 §4）：批准投影重建只写 t_trial_balance_exception，冻结账户按分录聚合的值；
 * Owner 余额的重算由 account 的 {@code POST /balances/v1/books/{bookId}/rebuild} 执行。</li>
 */
@Service
@AllArgsConstructor
public class TrialExceptionServiceImpl implements TrialExceptionService {
    /** 投影重建的解决方式代码。 */
    static final String PROJECTION_REBUILD = "PROJECTION_REBUILD";

    private final Clock clock;
    private final TransactionTemplate transactionTemplate;
    private final LedgerBookRepository ledgerBookRepository;
    private final CurrencySpecRepository currencySpecRepository;
    private final LedgerAccountRepository ledgerAccountRepository;
    private final TrialBalanceExceptionRepository trialBalanceExceptionRepository;

    /** 单行条件更新：OPEN → UNDER_REVIEW。 */
    @Override
    public void startProjectionRebuild(TrialBalanceException exception, long makerId) {
        exception.setResolutionType(PROJECTION_REBUILD);
        exception.setResolutionMakerId(makerId);
        trialBalanceExceptionRepository.updateResolution(exception, TrialExceptionStatus.UNDER_REVIEW);
    }

    /** 聚合账户分录 → 冻结摘要 → UNDER_REVIEW → RESOLVED，同一事务；不写分录、不写任何余额。 */
    @Override
    public void approveProjectionRebuild(TrialBalanceException exception, long checkerId, int minorUnit) {
        transactionTemplate.executeWithoutResult(status -> {
            LedgerAccount account = ledgerAccountRepository.findById(exception.getReferenceId());
            if (account == null) {
                throw new ErrorException("投影重建的账户不存在: " + exception.getReferenceId());
            }
            exception.setResolutionRefId(account.getId());
            exception.setResolutionCheckerId(checkerId);
            exception.setResolutionTime(clock.instant().truncatedTo(ChronoUnit.MICROS));
            exception.setResolutionContentHash(
                ProjectionRebuildContentHash.compute(exception, journalTotals(account), minorUnit));
            trialBalanceExceptionRepository.updateResolution(exception, TrialExceptionStatus.RESOLVED);
        });
    }

    /** 账户全部分录行聚合的四个值，净额按账户冻结的正常方向计算。 */
    ProjectionSnapshot journalTotals(LedgerAccount account) {
        AccountJournalTotalsView totals = ledgerAccountRepository.findJournalTotals(account.getId());
        BigDecimal debit = totals.getDebitTotal();
        BigDecimal credit = totals.getCreditTotal();
        BigDecimal net = account.getNormalSide() == NormalSide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
        return new ProjectionSnapshot(debit, credit, net, totals.getLastJournalSequence());
    }

    /** 账户 → 账簿 → 币种规则。 */
    @Override
    public int accountMinorUnit(long accountId) {
        LedgerAccount account = ledgerAccountRepository.findById(accountId);
        if (account == null) {
            throw new ErrorException("投影重建的账户不存在: " + accountId);
        }
        LedgerBook book = ledgerBookRepository.findById(account.getLedgerBookId());
        return currencySpecRepository.findByCurrency(book.getCurrency()).getMinorUnit();
    }
}
