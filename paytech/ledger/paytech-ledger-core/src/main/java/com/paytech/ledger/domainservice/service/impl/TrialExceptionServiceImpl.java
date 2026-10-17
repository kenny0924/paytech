package com.paytech.ledger.domainservice.service.impl;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.ledger.core.dal.dataobject.Account;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.core.dal.dataobject.TrialBalanceException;
import com.paytech.ledger.core.enums.NormalSide;
import com.paytech.ledger.core.enums.TrialExceptionStatus;
import com.paytech.ledger.domain.ProjectionRebuildContentHash;
import com.paytech.ledger.domain.ProjectionSnapshot;
import com.paytech.ledger.domain.view.AccountJournalTotalsView;
import com.paytech.ledger.domainservice.repository.AccountRepository;
import com.paytech.ledger.domainservice.repository.CurrencySpecRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.repository.TrialBalanceExceptionRepository;
import com.paytech.ledger.domainservice.service.TrialExceptionService;

import lombok.AllArgsConstructor;

/** 投影重建的本地保存；状态迁移以原状态为条件，投影以余额版本 CAS 写回。 */
@Service
@AllArgsConstructor
public class TrialExceptionServiceImpl implements TrialExceptionService {
    /** 投影重建的解决方式代码。 */
    static final String PROJECTION_REBUILD = "PROJECTION_REBUILD";

    private final Clock clock;
    private final AccountRepository accountRepository;
    private final TransactionTemplate transactionTemplate;
    private final LedgerBookRepository ledgerBookRepository;
    private final CurrencySpecRepository currencySpecRepository;
    private final TrialBalanceExceptionRepository trialBalanceExceptionRepository;

    /** 单行条件更新：OPEN → UNDER_REVIEW。 */
    @Override
    public void startProjectionRebuild(TrialBalanceException exception, long makerId) {
        exception.setResolutionType(PROJECTION_REBUILD);
        exception.setResolutionMakerId(makerId);
        trialBalanceExceptionRepository.updateResolution(exception, TrialExceptionStatus.UNDER_REVIEW);
    }

    /** 锁账户 → 重算 → CAS 写回投影 → 冻结摘要 → UNDER_REVIEW → RESOLVED，同一事务。 */
    @Override
    public void approveProjectionRebuild(TrialBalanceException exception, long checkerId, int minorUnit) {
        transactionTemplate.executeWithoutResult(status -> {
            Account account = accountRepository.lockById(exception.getReferenceId());
            if (account == null) {
                throw new ErrorException("投影重建的账户不存在: " + exception.getReferenceId());
            }
            ProjectionSnapshot before = ProjectionSnapshot.of(account);
            ProjectionSnapshot after = recompute(account);
            accountRepository.rebuildProjection(account, after.getDebitTotal(), after.getCreditTotal(),
                after.getNetBalance(), after.getLastJournalSequence());
            exception.setResolutionRefId(account.getId());
            exception.setResolutionCheckerId(checkerId);
            exception.setResolutionTime(clock.instant().truncatedTo(ChronoUnit.MICROS));
            exception.setResolutionContentHash(ProjectionRebuildContentHash.compute(exception, before, after, minorUnit));
            trialBalanceExceptionRepository.updateResolution(exception, TrialExceptionStatus.RESOLVED);
        });
    }

    /** 从账户全部分录行重算四个投影值，净额按账户冻结的正常方向计算。 */
    ProjectionSnapshot recompute(Account account) {
        AccountJournalTotalsView totals = accountRepository.findJournalTotals(account.getId());
        BigDecimal debit = totals.getDebitTotal();
        BigDecimal credit = totals.getCreditTotal();
        BigDecimal net = account.getNormalSide() == NormalSide.DEBIT ? debit.subtract(credit) : credit.subtract(debit);
        return new ProjectionSnapshot(debit, credit, net, totals.getLastJournalSequence());
    }

    /** 账户 → 账簿 → 币种规则。 */
    @Override
    public int accountMinorUnit(long accountId) {
        Account account = accountRepository.findById(accountId);
        if (account == null) {
            throw new ErrorException("投影重建的账户不存在: " + accountId);
        }
        LedgerBook book = ledgerBookRepository.findById(account.getLedgerBookId());
        return currencySpecRepository.findByCurrency(book.getCurrency()).getMinorUnit();
    }
}
