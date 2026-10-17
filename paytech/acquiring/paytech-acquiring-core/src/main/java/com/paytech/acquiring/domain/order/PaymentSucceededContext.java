package com.paytech.commerce.domain.order;

import java.math.BigDecimal;
import java.time.Instant;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.order.OrderPaymentProjection;
import com.paytech.commerce.core.dal.dataobject.order.OrderRefundPosition;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已核对归属、金额与幂等的支付成功事件上下文，持有本次读取的本库对象与需落地的事件事实。 */
@Getter
@AllArgsConstructor
public class PaymentSucceededContext {
    /** 事件所属订单。 */
    private final CommerceOrder order;

    /** 订单关联的 Checkout 会话；Direct Pay 或只记录投影时为空。 */
    private final CheckoutSession session;

    /** 订单唯一支付投影。 */
    private final OrderPaymentProjection payment;

    /** 订单退款金额位置；只记录投影时为空。 */
    private final OrderRefundPosition refundPosition;

    /** 成功的支付尝试 ID。 */
    private final long attemptId;

    /** 已捕获金额，已核对等于订单总额。 */
    private final BigDecimal capturedAmount;

    /** 事件幂等键。 */
    private final String eventKey;

    /** 载荷 SHA-256 小写十六进制。 */
    private final String payloadHash;

    /** 支付尝试来源版本。 */
    private final long attemptSourceVersion;

    /** 事件发生时间，作为投影最近应用事实时间。 */
    private final Instant occurredAt;

    /** 支付成功的业务时间，作为订单支付与会话完成时刻。 */
    private final Instant paidAt;
}
