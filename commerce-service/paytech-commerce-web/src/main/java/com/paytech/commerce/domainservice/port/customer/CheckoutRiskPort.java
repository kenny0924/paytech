package com.paytech.commerce.domainservice.port.customer;

import com.paytech.commerce.domain.customer.CustomerCheckoutModels.*;

/** Customer 依赖的窄 Owner 边界；实际 Wire 接入在后续阶段完成。 */
public interface CheckoutRiskPort {
    /** 事务外取得真正允许当前准确 OE/消费者组合的评估 ID；拒绝时抛业务错误。 */
    long assess(InitializationRequest request, CommercialFacts facts);
}
