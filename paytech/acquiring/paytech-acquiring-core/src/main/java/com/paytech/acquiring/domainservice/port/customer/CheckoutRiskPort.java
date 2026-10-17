package com.paytech.commerce.domainservice.port.customer;

import com.paytech.commerce.domain.customer.CustomerCheckoutModels.*;

/** 结账风控的窄 Owner 边界，由 Risk 统一评估（CHECKOUT_PREPARE）实现。 */
public interface CheckoutRiskPort {
    /** 事务外取得放行当前订单的评估 ID；未放行时抛业务错误，结果未知或依赖不可用时抛系统错误。 */
    long assess(InitializationRequest request, CommercialFacts facts);
}
