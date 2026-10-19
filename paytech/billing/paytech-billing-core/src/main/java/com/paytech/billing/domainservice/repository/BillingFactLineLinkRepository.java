package com.paytech.billing.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.billing.core.dal.dataobject.BillingFactLineLink;

/** t_billing_fact_line_link：明细行链接读取。 */
public interface BillingFactLineLinkRepository {

    /** 以给定明细行为原行的链接，按 ID 升序；集合为空时返回空列表。 */
    List<BillingFactLineLink> findBySourceLineIds(Collection<Long> lineIds);

    /** 以给定明细行为新行的链接，按 ID 升序；集合为空时返回空列表。 */
    List<BillingFactLineLink> findByTargetLineIds(Collection<Long> lineIds);
}
