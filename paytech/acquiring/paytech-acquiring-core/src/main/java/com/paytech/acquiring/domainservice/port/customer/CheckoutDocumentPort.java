package com.paytech.acquiring.domainservice.port.customer;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.*;

import java.time.Instant;

/** Customer 依赖的窄 Owner 边界；实际 Wire 接入在后续阶段完成。 */
public interface CheckoutDocumentPort {
    /**
     * 按订单与税务计算幂等冻结个性化合同，不存可变模板链接
     * <li>建单按暂估计算渲染；消费者资料提交后报价变化时按新计算重新渲染，同一计算重复调用返回同一文件。</li>
     *
     * @param request 订单商品与金额
     * @param seller 冻结的卖方公开披露
     * @param tax 合同所依据的税务计算
     * @return 合同文件身份与摘要
     */
    RenderedContract render(InitializationRequest request, Seller seller, TaxCalculation tax);

    /** 获取准确冻结文件的 HTTPS 链接；不得重渲染或延长 Checkout 授权。 */
    FileLink link(long fileId, String contentHash, Instant checkoutExpiresAt);
}
