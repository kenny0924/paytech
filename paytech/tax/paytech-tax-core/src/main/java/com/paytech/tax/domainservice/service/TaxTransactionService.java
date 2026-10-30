package com.paytech.tax.domainservice.service;

import com.paytech.tax.domain.registration.RegisterTaxTransactionContext;

/** 正式税务交易的本地保存。 */
public interface TaxTransactionService {

    /**
     * 一个本地事务写入正式交易、底稿，并登记开票补偿任务（需要时）
     *
     * @param context 已构造完整的登记
     * @throws org.springframework.dao.DuplicateKeyException 同一源事实已被并发登记（事务已回滚）
     */
    void saveTransaction(RegisterTaxTransactionContext context);
}
