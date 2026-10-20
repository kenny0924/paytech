package com.paytech.ledger.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.ledger.core.dal.dataobject.BookSequence;
import com.paytech.ledger.core.dal.dataobject.ChartAccountChange;
import com.paytech.ledger.core.dal.dataobject.FinanceEntity;
import com.paytech.ledger.core.dal.dataobject.LedgerAccount;
import com.paytech.ledger.core.dal.dataobject.LedgerBook;
import com.paytech.ledger.domain.ChartAccountChangeContext;
import com.paytech.ledger.domain.LedgerAccountNo;
import com.paytech.ledger.domainservice.repository.AccountNoSequenceRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountChangeRepository;
import com.paytech.ledger.domainservice.repository.ChartAccountRepository;
import com.paytech.ledger.domainservice.repository.BookSequenceRepository;
import com.paytech.ledger.domainservice.repository.FinanceEntityRepository;
import com.paytech.ledger.domainservice.repository.LedgerAccountRepository;
import com.paytech.ledger.domainservice.repository.LedgerBookRepository;
import com.paytech.ledger.domainservice.service.LedgerSetupService;

import lombok.AllArgsConstructor;

/** 财务主体、账簿、账户与科目维护的本地保存。 */
@Service
@AllArgsConstructor
public class LedgerSetupServiceImpl implements LedgerSetupService {
    private final LedgerAccountRepository ledgerAccountRepository;
    private final TransactionTemplate transactionTemplate;
    private final LedgerBookRepository ledgerBookRepository;
    private final BookSequenceRepository bookSequenceRepository;
    private final ChartAccountRepository chartAccountRepository;
    private final FinanceEntityRepository financeEntityRepository;
    private final AccountNoSequenceRepository accountNoSequenceRepository;
    private final ChartAccountChangeRepository chartAccountChangeRepository;

    /** 单行插入，唯一键保护重复。 */
    @Override
    public void createFinanceEntity(FinanceEntity entity) {
        financeEntityRepository.insert(entity);
    }

    /** 账簿与序号行必须同时存在，否则后续过账无法取号。 */
    @Override
    public void createLedgerBook(LedgerBook book, BookSequence sequence) {
        transactionTemplate.executeWithoutResult(status -> {
            ledgerBookRepository.insert(book);
            bookSequenceRepository.insert(sequence);
        });
    }

    /** 序号与账户同事务：账户身份重复时序号随之回滚；账户号唯一键兜底。 */
    @Override
    public void createAccount(LedgerAccount account, LedgerAccountNo accountNo) {
        transactionTemplate.executeWithoutResult(status -> {
            long sequence = accountNoSequenceRepository.next(account.getLedgerBookId(), account.getChartAccountId());
            account.setAccountNo(accountNo.withSequence(sequence));
            ledgerAccountRepository.insert(account);
        });
    }

    /** 科目写入与变更记录同时提交或回滚；变更后的值取自变更记录的 after 字段。 */
    @Override
    public void saveChartAccountChange(ChartAccountChangeContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            saveChartAccount(context);
            chartAccountChangeRepository.insert(context.getChange());
        });
    }

    /** 按变更类型插入科目、修改结构或改变启停状态。 */
    void saveChartAccount(ChartAccountChangeContext context) {
        ChartAccountChange change = context.getChange();
        switch (change.getChangeType()) {
            case CREATE -> chartAccountRepository.insert(context.getAccount());
            case UPDATE -> chartAccountRepository.updateStructure(context.getAccount(), change.getAfterAccountName(),
                change.getAfterAccountNo(), change.getAfterParentAccountId());
            case DISABLE, ENABLE -> chartAccountRepository.updateStatus(context.getAccount(),
                change.getAfterAccountStatus());
        }
    }
}
