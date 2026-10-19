package com.paytech.billing.domainservice.repository;

import java.util.Collection;
import java.util.List;

import com.paytech.billing.core.dal.dataobject.BillingFactPricing;

/** t_billing_fact_pricing：PAYMENT 事实定价输入的登记与读取。 */
public interface BillingFactPricingRepository {

    /** 插入定价输入；影响行数不为 1 时为系统错误。 */
    void insert(BillingFactPricing pricing);

    /** 按事实读取定价输入，不存在返回空。 */
    BillingFactPricing findByFactId(long factId);

    /** 按一组事实读取定价输入；集合为空时返回空列表。 */
    List<BillingFactPricing> findByFactIds(Collection<Long> factIds);
}
