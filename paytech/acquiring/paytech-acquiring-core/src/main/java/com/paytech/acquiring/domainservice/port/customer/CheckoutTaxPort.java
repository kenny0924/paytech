package com.paytech.commerce.domainservice.port.customer;

import com.paytech.commerce.domain.customer.CustomerCheckoutModels.*;

/** Customer 依赖的窄 Owner 边界；实际 Wire 接入在后续阶段完成。 */
public interface CheckoutTaxPort {
    /** 事务外以原准备身份计算最终税事实；相同身份必须稳定。 */
    TaxFacts calculate(InitializationRequest request, CommercialFacts facts);
}
