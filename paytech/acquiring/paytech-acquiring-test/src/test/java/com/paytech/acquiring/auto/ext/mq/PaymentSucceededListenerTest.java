package com.paytech.acquiring.auto.ext.mq;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domain.enums.CheckoutSessionStatus;
import com.paytech.acquiring.domain.enums.FundsAvailabilityStatus;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.PaymentAttemptStatus;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.acquiring.ext.mq.PaymentSucceededListener;
import com.paytech.acquiring.test.base.order.AbstractPaymentResultTest;
import com.paytech.acquiring.test.dal.PaymentResultTestMapper;
import com.paytech.acquiring.test.mocker.local.CheckoutPaymentDataMocker;

/**
 * Payment 支付成功事件消费：测试作为 Payment 以 paytech-messaging 发布到真实本机 RabbitMQ 的 payment.events，经 acquiring 分区队列、
 * 消费幂等拦截器与 Handler 在一个业务事务推进订单五项事实；消费登记写入 t_consumed_event / t_consumed_aggregate_version。
 *
 * @see PaymentSucceededListener#consume
 */
class PaymentSucceededListenerTest extends AbstractPaymentResultTest {
    private static final String PAID_TIME = "2026-09-08T00:00:01Z";
    private static final long ATTEMPT_ID = 8_000_001L;

    @Autowired
    CheckoutPaymentDataMocker data;
    @Autowired
    PaymentResultTestMapper mapper;
    private CreateCheckoutContext fixture;

    /** 准备已绑定 Intent 的开放订单；分区队列与 DLQ 由基类清空。 */
    @BeforeEach
    void setUpPaymentResult() {
        fixture = boundOrder();
    }

    /** 正常推进五项事实；同一事件重复投递不重复执行。 */
    @Test
    void test_normalFlow() throws Exception {
        var payload = payload(fixture, ATTEMPT_ID);

        publish(payload);

        awaitPaid(orderId());
        assertEquals(OrderStatus.PAID.getCode(), mapper.order(orderId()).get("order_status"));
        assertEquals(PAID_TIME, mapper.order(orderId()).get("paid_at"));
        assertEquals(CheckoutSessionStatus.COMPLETED.getCode(), mapper.session(orderId()).get("session_status"));
        assertEquals(PAID_TIME, mapper.session(orderId()).get("completed_at"));
        var payment = mapper.payment(orderId());
        assertEquals(PaymentIntentStatus.SUCCEEDED.getCode(), payment.get("intent_status"));
        assertEquals(PaymentAttemptStatus.SUCCEEDED.getCode(), payment.get("last_attempt_status"));
        assertEquals(ATTEMPT_ID, number(payment, "last_attempt_id"));
        assertEquals(ATTEMPT_ID, number(payment, "successful_attempt_id"));
        assertEquals(0, new BigDecimal("1.00").compareTo((BigDecimal)payment.get("captured_amount")));
        assertEquals(3L, number(payment, "attempt_source_version"));
        assertEquals(eventKey(payload), payment.get("last_attempt_event_key"));
        assertEquals(eventKey(payload), payment.get("last_intent_event_key"));
        assertEquals(payloadHash(payload), payment.get("last_attempt_payload_hash"));
        assertEquals(payloadHash(payload), payment.get("last_intent_payload_hash"));
        assertEquals("2026-09-08T00:00:02Z", payment.get("last_event_time"));
        assertEquals(2L, number(payment, "version"));
        assertEquals(0, new BigDecimal("1.00").compareTo((BigDecimal)mapper.refund(orderId()).get("refundable_base_amount")));
        assertEquals(FundsAvailabilityStatus.PENDING.getCode(), mapper.funds(orderId()).get("funds_availability_status"));

        var order = mapper.order(orderId());
        var session = mapper.session(orderId());
        var refund = mapper.refund(orderId());
        double replayed = paymentEvents.outcome("replayed");
        publish(payload);
        paymentEvents.awaitOutcome("replayed", replayed, 1);
        var marker = boundOrder();
        publish(payload(marker, ATTEMPT_ID + 1));
        awaitPaid(marker.getOrder().getOrderId());
        assertEquals(order, mapper.order(orderId()));
        assertEquals(session, mapper.session(orderId()));
        assertEquals(refund, mapper.refund(orderId()));
        assertEquals(2L, number(mapper.payment(orderId()), "version"));
        assertEquals(1, paymentEvents.consumedEvents(ATTEMPT_ID));
        assertEquals(SOURCE_VERSION, paymentEvents.lastVersion(ATTEMPT_ID));
        assertNull(paymentEvents.receiveDeadLetter(200));
    }

    /** 重放跳过：同一 eventId 再次投递由消费登记确认跳过，Handler 不再执行，消费登记仍只有一行。 */
    @Test
    void test_replayedEventSkipped() throws Exception {
        var payload = payload(fixture, ATTEMPT_ID);
        publish(payload);
        awaitPaid(orderId());
        var payment = mapper.payment(orderId());
        double replayed = paymentEvents.outcome("replayed");
        double handled = paymentEvents.outcome("handled");

        publish(payload);

        paymentEvents.awaitOutcome("replayed", replayed, 1);
        assertEquals(handled, paymentEvents.outcome("handled"));
        assertEquals(payment, mapper.payment(orderId()));
        assertEquals(1, paymentEvents.consumedEvents(ATTEMPT_ID));
        assertNull(paymentEvents.receiveDeadLetter(200));
    }

    /** 旧版本跳过：同一尝试已处理版本 3 后，到达的版本 2 被确认跳过，投影与消费登记不变。 */
    @Test
    void test_staleVersionSkipped() throws Exception {
        var payload = payload(fixture, ATTEMPT_ID);
        publish(payload);
        awaitPaid(orderId());
        var payment = mapper.payment(orderId());
        double stale = paymentEvents.outcome("stale");

        publish(payload, SOURCE_VERSION - 1);

        paymentEvents.awaitOutcome("stale", stale, 1);
        assertEquals(payment, mapper.payment(orderId()));
        assertEquals(eventKey(payload), payment.get("last_attempt_event_key"));
        assertEquals(1, paymentEvents.consumedEvents(ATTEMPT_ID));
        assertEquals(SOURCE_VERSION, paymentEvents.lastVersion(ATTEMPT_ID));
        assertNull(paymentEvents.receiveDeadLetter(200));
    }

    /** 载荷摘要被篡改：进入死信队列，订单与投影不变。 */
    @Test
    void test_invalidParameter() throws Exception {
        var payment = mapper.payment(orderId());

        publishTampered(payload(fixture, ATTEMPT_ID));

        assertNotNull(paymentEvents.receiveDeadLetter(5000));
        assertUnchanged(payment);
        assertEquals(0, paymentEvents.consumedEvents(ATTEMPT_ID));
    }

    /** 未知订单：进入死信队列。 */
    @Test
    void test_noDataFound() throws Exception {
        var payload = payload(fixture, ATTEMPT_ID);
        long unknown = checkoutDataMocker.reserveOrderId();
        payload.put("orderId", Long.toString(unknown));
        payload.put("paymentIntentId", Long.toString(unknown));

        publish(payload);

        assertNotNull(paymentEvents.receiveDeadLetter(5000));
        assertUnchanged(mapper.payment(orderId()));
        assertEquals(0, paymentEvents.consumedEvents(ATTEMPT_ID));
    }

    /** 金额与订单冻结总额不符：进入死信队列，订单与投影不变。 */
    @Test
    void test_businessRuleViolation() throws Exception {
        var payment = mapper.payment(orderId());
        var payload = payload(fixture, ATTEMPT_ID);
        payload.put("amount", "2.00");
        payload.put("capturedAmount", "2.00");

        publish(payload);

        assertNotNull(paymentEvents.receiveDeadLetter(5000));
        assertUnchanged(payment);
        assertEquals(0, paymentEvents.consumedEvents(ATTEMPT_ID));
    }

    /** 已取消订单：只记录支付投影，订单与会话保持取消，退款与资金投影不变；消息确认而不进入死信队列。 */
    @Test
    void test_edgeCase() throws Exception {
        mapper.cancelOrder(orderId(), fixture.getOrder().getCreateTime());
        var payload = payload(fixture, ATTEMPT_ID);

        publish(payload);

        await().atMost(Duration.ofSeconds(10))
            .until(() -> PaymentIntentStatus.SUCCEEDED.getCode().equals(mapper.payment(orderId()).get("intent_status")));
        var payment = mapper.payment(orderId());
        assertEquals(ATTEMPT_ID, number(payment, "successful_attempt_id"));
        assertEquals(eventKey(payload), payment.get("last_attempt_event_key"));
        assertEquals(OrderStatus.CANCELLED.getCode(), mapper.order(orderId()).get("order_status"));
        assertNull(mapper.order(orderId()).get("paid_at"));
        assertEquals(CheckoutSessionStatus.CANCELLED.getCode(), mapper.session(orderId()).get("session_status"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal)mapper.refund(orderId()).get("refundable_base_amount")));
        assertEquals(FundsAvailabilityStatus.NOT_APPLICABLE.getCode(),
            mapper.funds(orderId()).get("funds_availability_status"));
        assertNull(paymentEvents.receiveDeadLetter(500));
        assertEquals(1, paymentEvents.consumedEvents(ATTEMPT_ID));
    }

    /** 已绑定 Intent（ID 同订单）的开放订单。 */
    CreateCheckoutContext boundOrder() {
        var context = data.mockPrepared(true);
        long orderId = context.getOrder().getOrderId();
        mapper.insertPayment(orderId, orderId, context.getOrder().getCreateTime());
        return context;
    }

    /** 等待订单进入 PAID。 */
    void awaitPaid(long orderId) {
        await().atMost(Duration.ofSeconds(10))
            .until(() -> OrderStatus.PAID.getCode().equals(mapper.order(orderId).get("order_status")));
    }

    /** 拒绝的消息不改变订单、会话、投影、退款与资金投影。 */
    void assertUnchanged(Map<String, Object> payment) {
        assertEquals(payment, mapper.payment(orderId()));
        assertEquals(OrderStatus.OPEN.getCode(), mapper.order(orderId()).get("order_status"));
        assertEquals(CheckoutSessionStatus.OPEN.getCode(), mapper.session(orderId()).get("session_status"));
        assertEquals(0, BigDecimal.ZERO.compareTo((BigDecimal)mapper.refund(orderId()).get("refundable_base_amount")));
        assertEquals(FundsAvailabilityStatus.NOT_APPLICABLE.getCode(),
            mapper.funds(orderId()).get("funds_availability_status"));
    }

    /** 数值列转 long。 */
    static long number(Map<String, Object> row, String column) {
        return ((Number)row.get(column)).longValue();
    }

    /** 本例订单 ID。 */
    long orderId() {
        return fixture.getOrder().getOrderId();
    }
}
