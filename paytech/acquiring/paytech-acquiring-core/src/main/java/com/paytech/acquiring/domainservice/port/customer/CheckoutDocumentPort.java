package com.paytech.commerce.domainservice.port.customer;

import com.paytech.commerce.domain.customer.CustomerCheckoutModels.*;

import java.time.Instant;

/** Customer 依赖的窄 Owner 边界；实际 Wire 接入在后续阶段完成。 */
public interface CheckoutDocumentPort {
    /** 按原命令幂等冻结个性化合同，不存可变模板链接。 */
    RenderedContract render(InitializationRequest request, CommercialFacts facts, TaxFacts tax);

    /** 获取准确冻结文件的 HTTPS 链接；不得重渲染或延长 Checkout 授权。 */
    FileLink link(long fileId, String contentHash, Instant checkoutExpiresAt);
}
