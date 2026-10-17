package com.paytech.commerce.domainservice.port.customer;

import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.*;

/** 待接 Payment Card 的专用内部合同，不向普通 Intent 接口增加卡字段。 */
public interface CheckoutCardPaymentPort {
    /** 依据 Intent 当前资金状态执行；Payment 负责同一 Intent 的并发和重复支付保护。 */
    PaymentResult submit(PaymentRequest request);

    /**
     * 只读原提交/动作，校验准确订单与 Intent；不接 Token，不回调 Commerce
     *
     * @param clientId 订单所属 Client，Payment 按其校验 Intent 归属
     * @param reference 本地订单、Intent 与来源提交编号
     * @return Payment 当前结果；Payment 未找到该提交时返回 null，不推断为支付失败
     */
    PaymentResult query(long clientId, PaymentReference reference);
}
