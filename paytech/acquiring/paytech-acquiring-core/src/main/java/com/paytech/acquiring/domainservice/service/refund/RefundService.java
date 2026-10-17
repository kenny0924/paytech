package com.paytech.commerce.domainservice.service.refund;

import java.time.Instant;
import com.paytech.commerce.domain.refund.RefundCreationContext;
import com.paytech.commerce.integration.payment.PaymentRefundClient;

/** 退款金额和状态不变量的本库业务边界。 */
public interface RefundService {
    /** REQUIRED 事务原子保存退款、预占和提交任务；只操作本地数据库。 */
    void saveCreateRefundContext(RefundCreationContext context);

    /** 在调用方短事务内恢复提交；终态返回 null。 */
    PaymentRefundClient.Request prepare(String refundRequestNo, Instant now);
    /** 在调用方短事务内绑定渠道号、消费或释放预占；未决返回 false。 */
    boolean complete(PaymentRefundClient.Request request, PaymentRefundClient.Result result, Instant now);
}
