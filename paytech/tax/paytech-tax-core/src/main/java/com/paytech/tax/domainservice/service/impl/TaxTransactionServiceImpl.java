package com.paytech.tax.domainservice.service.impl;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.compensation.api.CompensationEventRegistrar;
import com.paytech.tax.domain.registration.RegisterTaxTransactionContext;
import com.paytech.tax.domainservice.repository.TaxReportingEntryRepository;
import com.paytech.tax.domainservice.repository.TaxTransactionRepository;
import com.paytech.tax.domainservice.service.TaxTransactionService;

import lombok.AllArgsConstructor;

/** TaxTransactionService 实现：交易先插入以由源身份唯一键拦截并发登记；补偿任务与事实同一事务提交，提交后才执行。 */
@Service
@AllArgsConstructor
public class TaxTransactionServiceImpl implements TaxTransactionService {
    private final TransactionTemplate transactionTemplate;
    private final TaxTransactionRepository taxTransactionRepository;
    private final CompensationEventRegistrar compensationEventRegistrar;
    private final TaxReportingEntryRepository taxReportingEntryRepository;

    /** 交易、底稿、开票任务原子提交。 */
    @Override
    public void saveTransaction(RegisterTaxTransactionContext context) {
        transactionTemplate.executeWithoutResult(status -> {
            taxTransactionRepository.insert(context.getTransaction());
            taxReportingEntryRepository.insertAll(context.getEntries());
            if (context.getDocumentIssueEvent() != null) {
                compensationEventRegistrar.register(context.getDocumentIssueEvent());
            }
        });
    }
}
