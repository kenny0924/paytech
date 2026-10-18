package com.paytech.billing.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.billing.core.dal.dataobject.EconomicFactLine;

/** t_economic_fact_line：经济事实明细行的登记与读取。 */
public interface EconomicFactLineRepository {

    /** 经济事实的全部明细行，按行号升序。 */
    List<EconomicFactLine> findByEconomics(long transactionEconomicsId);

    /** 一组经济事实的全部明细行，按事实与行号升序；集合为空时返回空列表。 */
    List<EconomicFactLine> findByEconomicsIds(Collection<Long> transactionEconomicsIds);

    /** 一次插入全部明细行；影响行数不等于行数时为系统错误。 */
    void insertAll(List<EconomicFactLine> lines);
}
