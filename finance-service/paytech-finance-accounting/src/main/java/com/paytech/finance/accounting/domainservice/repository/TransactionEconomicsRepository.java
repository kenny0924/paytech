package com.paytech.finance.accounting.domainservice.repository;

import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomics;

/** t_transaction_economics：交易经济结果读取。 */
public interface TransactionEconomicsRepository {

    /** 按主键读取，不存在返回空。 */
    TransactionEconomics findById(long id);
}
