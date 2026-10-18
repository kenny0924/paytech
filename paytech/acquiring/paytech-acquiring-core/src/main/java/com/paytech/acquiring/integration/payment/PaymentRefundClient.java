package com.paytech.acquiring.integration.payment;

import java.math.BigDecimal;

/** Payment 退款边界；TODO(Payment)：接入正式 PAY-I04 和查询契约；本地端口不代表远端接口已实现。 */
public interface PaymentRefundClient {
    /** 重试保持同一 Commerce 退款身份和幂等键，返回该退款最新权威结果。 */
    Result submitOrQuery(Request request);

    record Request(long clientId, String orderNo, long paymentIntentId, long refundRequestId,
            String refundRequestNo, String idempotencyKey, BigDecimal amount, String currency, String reasonCode) {}

    record Result(long paymentIntentId, long refundRequestId, long channelRefundId,
            BigDecimal amount, String currency, Status status) {}

    enum Status {
        PROCESSING,

        SUCCEEDED,

        FAILED,

        CANCELLED
    }
}
