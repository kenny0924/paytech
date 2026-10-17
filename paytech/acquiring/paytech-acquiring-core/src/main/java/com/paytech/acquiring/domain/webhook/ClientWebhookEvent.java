package com.paytech.commerce.domain.webhook;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.commerce.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.commerce.core.dal.dataobject.order.CommerceOrder;
import com.paytech.commerce.core.dal.dataobject.refund.RefundRequest;
import com.paytech.commerce.domain.enums.ClientWebhookEventType;
import com.paytech.commerce.domain.enums.OrderPaymentStatus;
import com.paytech.commerce.domain.order.CommerceDecimalRules;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 待冻结的 Client Webhook 事件：在业务写入成功、内存对象已同步后由同一事务构造。
 * <li>data.object 只含合同公开字段，金额为币种精度字符串，时间为 UTC Z 字符串；不含内部 ID、渠道编号或个人信息。</li>
 * <li>createdAt 为本次业务写入时刻截断到秒；sourceVersion 为对象 version。</li>
 */
@Getter
@AllArgsConstructor(access = AccessLevel.PRIVATE)
public class ClientWebhookEvent {
    /** 本期唯一的订单取消来源；以后增加取消来源时须把原因码补到订单对象上。 */
    public static final String CLIENT_REQUESTED_REASON = "CLIENT_REQUESTED";

    /** 公开事件类型。 */
    private final ClientWebhookEventType eventType;

    /** 资源公开编号，订单号或退款号。 */
    private final String resourceNo;

    /** 事件归属 Client。 */
    private final long clientId;

    /** 来源资源版本，与 data.object.version 相同。 */
    private final long sourceVersion;

    /** 事件生成时刻，秒精度 UTC。 */
    private final Instant createdAt;

    /** 公开 data.object，保持字段顺序且不可修改。 */
    private final Map<String, Object> object;

    /**
     * 订单首次支付成功；调用方已执行 markPaid，订单状态、支付时间与版本均为写入后的值。
     *
     * @param order 已同步为 PAID 的订单
     * @param paidAmount 本次已捕获金额，已核对等于订单总额
     * @param now 本次写入时刻
     * @return 待冻结事件
     */
    public static ClientWebhookEvent orderPaid(CommerceOrder order, BigDecimal paidAmount, Instant now) {
        var amounts = new LinkedHashMap<String, Object>();
        amounts.put("totalAmount", CommerceDecimalRules.toAmountString(order.getCurrency(), order.getTotalAmount()));
        amounts.put("paidAmount", CommerceDecimalRules.toAmountString(order.getCurrency(), paidAmount));
        var object = new LinkedHashMap<String, Object>();
        object.put("orderNo", order.getOrderNo());
        object.put("clientOrderId", order.getClientOrderId());
        object.put("orderStatus", order.getOrderStatus().getCode());
        // paymentStatus 不落库；首次进入 PAID 时与 GET 的推导结果一致。
        object.put("paymentStatus", OrderPaymentStatus.SUCCEEDED.getCode());
        object.put("currency", order.getCurrency());
        object.put("amounts", Collections.unmodifiableMap(amounts));
        object.put("paidAt", order.getPaidAt().toString());
        object.put("version", order.getVersion());
        return orderEvent(ClientWebhookEventType.ORDER_PAID, order, now, object);
    }

    /**
     * 订单首次取消完成；字段名与取消接口 200 响应对齐，没有会话的 Direct Pay 省略 checkoutStatus。
     *
     * @param order 已同步为 CANCELLED 的订单
     * @param session 已同步为 CANCELLED 的会话，没有会话时为空
     * @param now 本次写入时刻
     * @return 待冻结事件
     */
    public static ClientWebhookEvent orderCancelled(CommerceOrder order, CheckoutSession session, Instant now) {
        var object = new LinkedHashMap<String, Object>();
        object.put("orderNo", order.getOrderNo());
        object.put("clientOrderId", order.getClientOrderId());
        object.put("orderStatus", order.getOrderStatus().getCode());
        if (session != null) {
            object.put("checkoutStatus", session.getSessionStatus().getCode());
        }
        object.put("reasonCode", CLIENT_REQUESTED_REASON);
        object.put("cancelledAt", order.getCancelledAt().toString());
        object.put("version", order.getVersion());
        return orderEvent(ClientWebhookEventType.ORDER_CANCELLED, order, now, object);
    }

    /**
     * 退款请求首次受理；对象与 GET Refund 表示逐字段相同。
     *
     * @param refund 本事务刚插入的退款
     * @param orderNo 归属订单的公开编号
     * @return 待冻结事件，createdAt 取退款写入时刻
     */
    public static ClientWebhookEvent refundCreated(RefundRequest refund, String orderNo) {
        var object = new LinkedHashMap<String, Object>();
        object.put("refundRequestNo", refund.getRefundRequestNo());
        object.put("clientRefundId", refund.getClientRefundId());
        object.put("requestOrigin", refund.getRequestOrigin().getCode());
        object.put("orderNo", orderNo);
        object.put("amount", CommerceDecimalRules.toAmountString(refund.getCurrency(), refund.getRefundAmount()));
        object.put("currency", refund.getCurrency());
        object.put("refundStatus", refund.getRefundStatus().getCode());
        object.put("reasonCode", refund.getReasonCode());
        object.put("statusReasonCode", refund.getStatusReasonCode());
        object.put("createdAt", refund.getCreateTime().toString());
        object.put("completedAt", refund.getCompletedAt() == null ? null : refund.getCompletedAt().toString());
        object.put("updatedAt", refund.getUpdateTime().toString());
        object.put("version", refund.getVersion());
        return new ClientWebhookEvent(ClientWebhookEventType.REFUND_CREATED, refund.getRefundRequestNo(),
            refund.getClientId(), refund.getVersion(), seconds(refund.getUpdateTime()),
            Collections.unmodifiableMap(object));
    }

    /** 永久事件 ID，重放不变。 */
    public String eventId() {
        return eventType.eventId(resourceNo);
    }

    /** 订单类事件的公共身份。 */
    static ClientWebhookEvent orderEvent(ClientWebhookEventType type, CommerceOrder order, Instant now,
        Map<String, Object> object) {
        return new ClientWebhookEvent(type, order.getOrderNo(), order.getClientId(), order.getVersion(), seconds(now),
            Collections.unmodifiableMap(object));
    }

    /** Envelope createdAt 使用秒精度。 */
    static Instant seconds(Instant instant) {
        return instant.truncatedTo(ChronoUnit.SECONDS);
    }
}
