package com.paytech.finance.accounting.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.finance.accounting.core.dal.dataobject.Account;
import com.paytech.finance.accounting.core.dal.dataobject.BookSequence;
import com.paytech.finance.accounting.core.dal.dataobject.FinanceEntity;
import com.paytech.finance.accounting.core.dal.dataobject.LedgerBook;
import com.paytech.finance.accounting.domainservice.repository.AccountRepository;
import com.paytech.finance.accounting.domainservice.repository.BookSequenceRepository;
import com.paytech.finance.accounting.domainservice.repository.FinanceEntityRepository;
import com.paytech.finance.accounting.domainservice.repository.LedgerBookRepository;
import com.paytech.finance.accounting.domainservice.service.LedgerSetupService;

import lombok.AllArgsConstructor;

/** 财务主体、账簿和账户的本地保存。 */
@Service
@AllArgsConstructor
public class LedgerSetupServiceImpl implements LedgerSetupService {
    private final AccountRepository accountRepository;
    private final TransactionTemplate transactionTemplate;
    private final LedgerBookRepository ledgerBookRepository;
    private final BookSequenceRepository bookSequenceRepository;
    private final FinanceEntityRepository financeEntityRepository;

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

    /** 单行插入，账户身份唯一键保护重复。 */
    @Override
    public void createAccount(Account account) {
        accountRepository.insert(account);
    }
}
