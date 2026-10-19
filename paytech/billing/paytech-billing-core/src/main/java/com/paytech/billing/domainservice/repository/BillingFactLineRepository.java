package com.paytech.billing.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.billing.core.dal.dataobject.BillingFactLine;

/** t_billing_fact_line：计费事实明细行的登记与读取。 */
public interface BillingFactLineRepository {

    /** 一次插入全部明细行；影响行数不等于行数时为系统错误。 */
    void insertAll(List<BillingFactLine> lines);

    /** 一条事实的全部明细行，按行号升序。 */
    List<BillingFactLine> findByFactId(long factId);

    /** 一组事实的全部明细行，按事实与行号升序；集合为空时返回空列表。 */
    List<BillingFactLine> findByFactIds(Collection<Long> factIds);
}
