package com.paytech.acquiring.domainservice.port.customer;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.*;

/** Customer 依赖的窄 Owner 边界；实际 Wire 接入在后续阶段完成。 */
public interface CheckoutCommercialPort {
    /** 事务外按优先级完整验证候选；不得以默认 OE 或默认合同成功。 */
    CommercialFacts resolve(InitializationRequest request);
}
