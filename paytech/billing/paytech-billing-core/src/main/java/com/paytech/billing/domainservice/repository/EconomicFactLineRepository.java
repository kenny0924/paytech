package com.paytech.billing.domainservice.repository;

import java.util.List;

import com.paytech.billing.core.dal.dataobject.EconomicFactLine;

/** t_economic_fact_line：经济事实明细行读取。 */
public interface EconomicFactLineRepository {

    /** 交易经济结果的全部组件，按组件序号升序。 */
    List<EconomicFactLine> findByEconomics(long transactionEconomicsId);
}
