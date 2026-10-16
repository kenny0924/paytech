package com.paytech.finance.accounting.domainservice.repository;

import java.util.List;

import com.paytech.finance.accounting.core.dal.dataobject.TransactionEconomicComponent;

/** t_transaction_economic_component：交易经济组件读取。 */
public interface TransactionEconomicComponentRepository {

    /** 交易经济结果的全部组件，按组件序号升序。 */
    List<TransactionEconomicComponent> findByEconomics(long transactionEconomicsId);
}
