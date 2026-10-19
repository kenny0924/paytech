package com.paytech.billing.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.billing.api.enums.FactRefType;
import com.paytech.billing.core.dal.dataobject.BillingFactRef;

/** t_billing_fact_ref：计费事实外部引用的登记与读取。 */
public interface BillingFactRefRepository {

    /** 一次插入全部引用；列表为空时不发出 SQL，影响行数不符时为系统错误。 */
    void insertAll(List<BillingFactRef> refs);

    /** 一条事实的全部引用，按引用类型升序。 */
    List<BillingFactRef> findByFactId(long factId);

    /** 一组事实的全部引用，按事实与引用类型升序；集合为空时返回空列表。 */
    List<BillingFactRef> findByFactIds(Collection<Long> factIds);

    /** 引用给定外部对象且 ID 大于游标的事实 ID，按 ID 升序取 limit 条。 */
    List<Long> findFactIdsByRef(FactRefType refType, String refId, long fromFactId, int limit);
}
