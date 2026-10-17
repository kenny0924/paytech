package com.paytech.commerce.service.processor.customer.response;

import java.time.Instant;

import com.paytech.commerce.customer.api.v1.enums.CustomerPaymentStatus;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentReference;
import com.paytech.commerce.domain.customer.CheckoutSubmissionModels.PaymentResult;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付提交和查询共用的内部结果，不依赖 HTTP 状态或响应体。 */
@Getter
@AllArgsConstructor
public class CustomerPaymentResponse {
    /** 当前支付的业务身份，不读取命令或响应快照。 */
    private final PaymentReference submission;
    /** 原订单的访问期限。 */
    private final Instant expiresAt;
    /** Payment 当前结果，传输未知时为空。 */
    private final PaymentResult payment;

    /** 本地受理或 Payment 尚无确定结果时继续轮询。 */
    public boolean isPending() {
        return payment == null || CustomerPaymentStatus.PROCESSING.getCode().equals(payment.status())
            || CustomerPaymentStatus.UNKNOWN.getCode().equals(payment.status());
    }
}
