package com.paytech.commerce.domainservice.port.customer;

import com.paytech.commerce.domain.customer.CustomerCheckoutModels.*;

/** Customer 依赖的窄 Owner 边界；实际 Wire 接入在后续阶段完成。 */
public interface CheckoutPaymentPort {
    /** 只查询现有概要，不创建 Action，不回调 Commerce 访问校验。 */
    PaymentStatus query(long orderId, long intentId);
}
