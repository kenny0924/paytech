package com.paytech.acquiring.core.dal.dataobject.order;

import java.math.BigDecimal;

import com.paytech.acquiring.domain.customer.CustomerCheckoutModels.IntentResult;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;

import lombok.Getter;
import lombok.Setter;

/** 订单支付投影表中取消与退款判断所需的本库事实，不关联其他表。 */
@Getter
@Setter
public class OrderPaymentProjection {
    /** 本库支付投影主键。 */
    private long orderPaymentProjectionId;

    /** 所属订单主键。 */
    private long orderId;

    /** Payment 拥有的原支付意图标识。 */
    private long paymentIntentId;

    /** 本地已观察到的支付意图状态。 */
    private PaymentIntentStatus intentStatus;

    /** 已成功的支付尝试；没有成功事实时为空。 */
    private Long successfulAttemptId;

    /** 已确认捕获的金额，以订单币种主单位表示。 */
    private BigDecimal capturedAmount;

    /** 支付投影金额币种。 */
    private String currency;

    /** 条件写入核对的支付投影版本。 */
    private long version;

    /** 已消费的 Payment 来源事件版本；创建响应不含版本，未消费事件时为 0。 */
    private long intentSourceVersion;

    /**
     * 最近处理的支付尝试事件幂等键
     * <li>尚未消费任何尝试事件时为空。</li>
     */
    private String lastAttemptEventKey;

    /**
     * 最近处理的支付尝试事件载荷 SHA-256
     * <li>64 位小写十六进制，不含算法前缀；与事件键同时为空或同时存在。</li>
     */
    private String lastAttemptPayloadHash;

    /** 首次绑定时间。 */
    private java.time.Instant createTime;

    /** 从已验证的远端结果建立尚未支付的本地投影。 */
    public static OrderPaymentProjection created(long id, CommerceOrder order, IntentResult result, java.time.Instant now) {
        var payment = new OrderPaymentProjection();
        payment.setOrderPaymentProjectionId(id);
        payment.setOrderId(order.getOrderId());
        payment.setPaymentIntentId(result.intentId());
        payment.setIntentStatus(PaymentIntentStatus.REQUIRES_PAYMENT);
        payment.setIntentSourceVersion(0L);
        payment.setCapturedAmount(BigDecimal.ZERO);
        payment.setCurrency(order.getCurrency());
        payment.setVersion(1L);
        payment.setCreateTime(now);
        return payment;
    }

    /** 判断已加载投影是否存在成功支付事实；任一成功标识或正捕获金额即成立。 */
    public boolean hasSuccessfulPayment() {
        return successfulAttemptId != null || capturedAmount.signum() > 0
            || intentStatus == PaymentIntentStatus.SUCCEEDED;
    }
}
