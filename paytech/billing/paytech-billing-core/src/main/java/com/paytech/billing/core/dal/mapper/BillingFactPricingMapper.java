package com.paytech.billing.core.dal.mapper;

import java.util.Collection;
import java.util.List;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import com.paytech.billing.core.dal.dataobject.BillingFactPricing;

/** t_billing_fact_pricing：PAYMENT 事实定价输入的登记与读取。 */
@Mapper
public interface BillingFactPricingMapper {

    /** 插入定价输入；返回影响行数。 */
    int insert(BillingFactPricing pricing);

    /** 按事实读取定价输入。 */
    BillingFactPricing selectByFactId(@Param("factId") long factId);

    /** 按一组事实读取定价输入。 */
    List<BillingFactPricing> selectByFactIds(@Param("factIds") Collection<Long> factIds);
}
