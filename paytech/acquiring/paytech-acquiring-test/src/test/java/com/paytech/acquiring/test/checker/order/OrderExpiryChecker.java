package com.paytech.acquiring.test.checker.order;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.acquiring.core.dal.dataobject.checkout.CheckoutSession;
import com.paytech.acquiring.core.dal.dataobject.order.CommerceOrder;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.acquiring.domain.webhook.ClientWebhookEvent;
import com.paytech.acquiring.test.dal.CheckoutPaymentTestMapper;
import com.paytech.acquiring.test.dal.OrderCancellationTestMapper;
import com.paytech.acquiring.test.dal.OrderExpiryTestMapper;

import lombok.AllArgsConstructor;

/** 独立读取数据库中的订单、会话、投影状态与原因码；只读断言。 */
@TestComponent
@AllArgsConstructor
public class OrderExpiryChecker {
    private final OrderExpiryTestMapper expiries;
    private final CheckoutPaymentTestMapper payments;
    private final OrderCancellationTestMapper orders;

    /** 订单必须真实存在，不能让缺行的字段断言静默通过。 */
    public CommerceOrder order(long orderId) {
        var row = orders.order(orderId);
        assertNotNull(row, "过期测试订单必须存在");
        return row;
    }

    /** 实际关联的会话；Direct Pay 可为空。 */
    public CheckoutSession session(long orderId) {
        return orders.session(orderId);
    }

    /**
     * 过期终态同时落到订单与实际关联会话：原因码、终止意图已写，不写取消与支付时刻
     *
     * @param writtenAt 本次写入时刻，即订单与会话的更新时间
     */
    public void assertExpired(long orderId, boolean hasSession, Instant writtenAt) {
        var order = order(orderId);
        assertAll(() -> assertEquals(OrderStatus.EXPIRED, order.getOrderStatus()),
            () -> assertEquals(ClientWebhookEvent.EXPIRY_REACHED_REASON, expiries.statusReasonCode(orderId)),
            () -> assertNotNull(order.getTerminationRequestedAt()), () -> assertNull(order.getCancelledAt()),
            () -> assertNull(order.getPaidAt()), () -> assertEquals(writtenAt, order.getUpdateTime()));
        var session = session(orderId);
        if (!hasSession) {
            assertNull(session);
            return;
        }
        assertNotNull(session);
        assertAll(() -> assertEquals(CheckoutSessionStatus.EXPIRED, session.getSessionStatus()),
            () -> assertNull(session.getCancelledAt()), () -> assertEquals(writtenAt, session.getUpdateTime()));
    }

    /** 订单保持原状态、未写过期原因码；按场景确认终止意图是否已写。 */
    public void assertNotExpired(long orderId, OrderStatus status, boolean terminationRequested) {
        var order = order(orderId);
        assertAll(() -> assertEquals(status, order.getOrderStatus()),
            () -> assertNotEquals(ClientWebhookEvent.EXPIRY_REACHED_REASON, expiries.statusReasonCode(orderId)),
            () -> assertEquals(terminationRequested, order.getTerminationRequestedAt() != null,
                "终止意图写入状态"));
    }

    /** 订单原因码。 */
    public void assertReasonCode(long orderId, String reasonCode) {
        assertEquals(reasonCode, expiries.statusReasonCode(orderId));
    }

    /** 会话状态。 */
    public void assertSessionStatus(long orderId, CheckoutSessionStatus status) {
        var session = session(orderId);
        assertNotNull(session, "会话必须存在");
        assertEquals(status, session.getSessionStatus());
    }

    /** 支付投影必须存在且意图状态符合预期。 */
    public void assertPaymentStatus(long orderId, PaymentIntentStatus status) {
        var payment = payments.payment(orderId);
        assertNotNull(payment, "支付投影必须存在");
        assertEquals(status, payment.getIntentStatus());
    }

    /** 没有支付投影，终止链路不补写。 */
    public void assertNoPayment(long orderId) {
        assertNull(payments.payment(orderId), "不应写入支付投影");
    }
}
