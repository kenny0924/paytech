package com.paytech.acquiring.auto.ext.mq;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.*;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
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
 * Payment 支付成功事件消费：测试作为 Payment 发布到真实本机 RabbitMQ，经监听器在一个事务推进订单五项事实。
 *
 * @see PaymentSucceededListener#onMessage
 */
class PaymentSucceededListenerTest extends AbstractPaymentResultTest {
    private static final String DEAD_LETTER_QUEUE = "cg_acquiring_payment_result_dlq";
    private static final String PAID_TIME = "2026-09-08T00:00:01Z";
    private static final long ATTEMPT_ID = 8_000_001L;

    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    CheckoutPaymentDataMocker data;
    @Autowired
    PaymentResultTestMapper mapper;
    private CreateCheckoutContext fixture;

    /** 本服务队列只保留本例消息；准备已绑定 Intent 的开放订单。 */
    @BeforeEach
    void setUpPaymentResult() {
        amqpAdmin.purgeQueue(PaymentSucceededListener.QUEUE, false);
        amqpAdmin.purgeQueue(DEAD_LETTER_QUEUE, false);
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
        publish(payload);
        var marker = boundOrder();
        publish(payload(marker, ATTEMPT_ID + 1));
        awaitPaid(marker.getOrder().getOrderId());
        assertEquals(order, mapper.order(orderId()));
        assertEquals(session, mapper.session(orderId()));
        assertEquals(refund, mapper.refund(orderId()));
        assertEquals(2L, number(mapper.payment(orderId()), "version"));
        assertNull(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 200));
    }

    /** 载荷摘要被篡改：进入死信队列，订单与投影不变。 */
    @Test
    void test_invalidParameter() throws Exception {
        var payment = mapper.payment(orderId());

        publishRaw(envelope(payload(fixture, ATTEMPT_ID), false));

        assertNotNull(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 5000));
        assertUnchanged(payment);
    }

    /** 未知订单：进入死信队列。 */
    @Test
    void test_noDataFound() throws Exception {
        var payload = payload(fixture, ATTEMPT_ID);
        long unknown = checkoutDataMocker.reserveOrderId();
        payload.put("orderId", Long.toString(unknown));
        payload.put("paymentIntentId", Long.toString(unknown));

        publish(payload);

        assertNotNull(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 5000));
        assertUnchanged(mapper.payment(orderId()));
    }

    /** 金额与订单冻结总额不符：进入死信队列，订单与投影不变。 */
    @Test
    void test_businessRuleViolation() throws Exception {
        var payment = mapper.payment(orderId());
        var payload = payload(fixture, ATTEMPT_ID);
        payload.put("amount", "2.00");
        payload.put("capturedAmount", "2.00");

        publish(payload);

        assertNotNull(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 5000));
        assertUnchanged(payment);
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
        assertNull(rabbitTemplate.receive(DEAD_LETTER_QUEUE, 500));
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
