package com.paytech.commerce.domainservice.port.customer;

import com.paytech.commerce.domain.customer.CustomerCheckoutModels.*;

/** Customer 依赖的窄 Owner 边界；实际 Wire 接入在后续阶段完成。 */
public interface ConsumerProtectionPort {
    /** 事务外保护 Email 与仅含姓名和地址的 Profile；Email Token 必须稳定。 */
    ProtectedConsumer protect(ConsumerDetails details);

    /** 事务外按已有受控引用回填原值。 */
    ConsumerDetails reveal(ProtectedConsumer reference);
}
