package com.paytech.acquiring.auto.ext.mq;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.domain.enums.OrderStatus;
import com.paytech.acquiring.domain.enums.PaymentIntentStatus;
import com.paytech.acquiring.domain.order.OrderPaymentSucceededPayload;
import com.paytech.acquiring.ext.job.OrderPaymentSucceededHandler;
import com.paytech.acquiring.ext.mq.PaymentSucceededListener;
import com.paytech.acquiring.test.base.order.AbstractPaymentResultTest;
import com.paytech.acquiring.test.checkout.CompensationCoordinatorHold;
import com.paytech.acquiring.test.dal.PaymentResultTestMapper;
import com.paytech.acquiring.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.foundation.compensation.model.CompensationEventStatus;
import com.paytech.foundation.messaging.api.AggregateKey;

import tools.jackson.databind.json.JsonMapper;

/**
 * PaymentSucceeded 落地后的 OrderPaymentSucceeded 登记与发布：首次推进 PAID 在同一事务登记 ORDER_PAYMENT_SUCCEEDED（payloadJson 只含
 * 支付事实），提交后发布到 acquiring.events；已关闭订单补记与重复事件不登记；老报文缺渠道交易引用与卡属性时以 null 转发。
 *
 * @see PaymentSucceededListener#consume
 */
class PaymentSucceededOrderEventTest extends AbstractPaymentResultTest {
    private static final long ATTEMPT_ID = 8_200_001L;
    private static final long CHANNEL_TRANSACTION_REF_ID = 8_300_001L;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    CheckoutPaymentDataMocker data;
    @Autowired
    PaymentResultTestMapper mapper;
    @Autowired
    CompensationCoordinatorHold coordinatorHold;
    private CreateCheckoutContext fixture;

    /** 准备已绑定 Intent 的开放订单；分区队列与 DLQ 由基类清空。 */
    @BeforeEach
    void setUpOrderEvent() {
        fixture = data.mockPrepared(true);
        mapper.insertPayment(orderId(), orderId(), fixture.getOrder().getCreateTime());
    }

    /** 当前报文：订单进入 PAID 后发布一条信封，版本为推进后的订单版本，支付组携带渠道交易引用与卡属性。 */
    @Test
    void test_normalFlow() {
        publish(currentPayload());

        awaitPaid();
        orderPaymentEvents.awaitEnvelope(orderId()).envelope(orderId(), orderVersion(), Instant.parse(SUCCEEDED_TIME))
            .fields(expectedPayment(true));
    }

    /** 老报文缺少渠道交易引用与卡属性：照常推进并发布，对应字段为 JSON null。 */
    @Test
    void test_legacyPayloadWithoutNewFields() {
        publish(payload(fixture, ATTEMPT_ID));

        awaitPaid();
        orderPaymentEvents.awaitEnvelope(orderId()).envelope(orderId(), orderVersion(), Instant.parse(SUCCEEDED_TIME))
            .fields(expectedPayment(false));
    }

    /**
     * 登记合同：暂停补偿认领后观察到恰好一条 INIT 任务，事件键为订单与推进后版本，payloadJson 只含支付事实；释放后照常发布
     * <li>不依赖 acquiring.events 是否有其他服务的队列：任务在认领前被观察，不需要制造发布失败。</li>
     */
    @Test
    void test_registeredPaymentFactsOnly() {
        coordinatorHold.hold();
        try {
            publish(currentPayload());

            awaitPaid();
            List<Map<String, Object>> tasks = orderPaymentEvents.tasks(orderId());
            assertEquals(1, tasks.size());
            var task = tasks.getFirst();
            assertEquals(CompensationEventStatus.INIT.getCode(), task.get("execute_status"));
            assertEquals(AggregateKey.of(OrderPaymentSucceededPayload.AGGREGATE_TYPE, Long.toString(orderId()))
                .eventKey(OrderPaymentSucceededPayload.EVENT_TYPE, orderVersion()), task.get("event_key"));
            assertEquals(OrderPaymentSucceededHandler.EVENT_TYPE, task.get("event_type"));
            var facts = JSON.readTree((String)task.get("payload_json"));
            // payload_json 为 MySQL JSON 列，存储时会重排键，只比较字段集合。
            assertEquals(Set.of("paymentAttemptId", "capturedAmount", "paymentSucceededTime", "paymentMethodCode",
                "channelId", "channelAccountId", "midId", "channelTransactionRef", "channelTransactionRefId",
                "channelReferenceType", "cardBrand", "fundingType", "cardLevel"), Set.copyOf(facts.propertyNames()));
            assertEquals(Long.toString(ATTEMPT_ID), facts.path("paymentAttemptId").asString());
            assertEquals(Long.toString(CHANNEL_TRANSACTION_REF_ID), facts.path("channelTransactionRefId").asString());
            assertEquals("VISA", facts.path("cardBrand").asString());
        } finally {
            coordinatorHold.release();
        }

        orderPaymentEvents.awaitEnvelope(orderId()).envelope(orderId(), orderVersion(), Instant.parse(SUCCEEDED_TIME));
    }

    /** 已取消订单只补记支付投影，不登记也不发布。 */
    @Test
    void test_closedOrderNotPublished() {
        mapper.cancelOrder(orderId(), fixture.getOrder().getCreateTime());

        publish(currentPayload());

        await().atMost(Duration.ofSeconds(10))
            .until(() -> PaymentIntentStatus.SUCCEEDED.getCode().equals(mapper.payment(orderId()).get("intent_status")));
        orderPaymentEvents.assertNoEnvelope(orderId());
    }

    /** 同一成功尝试以更高版本再次到达（新事件 ID）：业务判定重复，不再登记第二条任务或第二条信封。 */
    @Test
    void test_duplicateNotPublishedAgain() {
        var payload = currentPayload();
        publish(payload);
        awaitPaid();
        orderPaymentEvents.awaitEnvelope(orderId());
        double handled = paymentEvents.outcome("handled");

        publish(payload, SOURCE_VERSION + 1);

        paymentEvents.awaitOutcome("handled", handled, 1);
        orderPaymentEvents.assertNoEnvelope(orderId());
        assertNull(paymentEvents.receiveDeadLetter(200));
    }

    /** 新增字段出现但格式非法（渠道交易引用 ID 非十进制）：按非法报文进入死信队列，订单不推进、不登记。 */
    @Test
    void test_invalidParameter() {
        var payload = currentPayload();
        payload.put("channelTransactionRefId", "ref-" + ATTEMPT_ID);

        publish(payload);

        assertNotNull(paymentEvents.receiveDeadLetter(5000));
        assertEquals(OrderStatus.OPEN.getCode(), mapper.order(orderId()).get("order_status"));
        orderPaymentEvents.assertNoEnvelope(orderId());
    }

    /** Payment 当前版本的完整载荷：v2 末尾依次追加卡属性与渠道交易引用。 */
    Map<String, Object> currentPayload() {
        var payload = payload(fixture, ATTEMPT_ID);
        payload.put("cardBrand", "VISA");
        payload.put("fundingType", "CREDIT");
        payload.put("cardLevel", "CLASSIC");
        payload.put("channelTransactionRefId", Long.toString(CHANNEL_TRANSACTION_REF_ID));
        payload.put("channelReferenceType", "PAY");
        return payload;
    }

    /** 支付组期望值：与发布的 PaymentSucceeded 字段一致，Intent 取支付投影（同订单 ID）；老报文的新增字段为 null。 */
    Map<String, String> expectedPayment(boolean current) {
        Map<String, String> payment = new LinkedHashMap<>();
        payment.put("paymentIntentId", Long.toString(orderId()));
        payment.put("paymentAttemptId", Long.toString(ATTEMPT_ID));
        payment.put("capturedAmount", "1.00");
        payment.put("paymentSucceededTime", SUCCEEDED_TIME);
        payment.put("paymentMethodCode", "CARD_TEST");
        payment.put("channelId", "301");
        payment.put("channelAccountId", "302");
        payment.put("midId", "303");
        payment.put("channelTransactionRef", "ch-ref-" + ATTEMPT_ID);
        payment.put("channelTransactionRefId", current ? Long.toString(CHANNEL_TRANSACTION_REF_ID) : null);
        payment.put("channelReferenceType", current ? "PAY" : null);
        payment.put("cardBrand", current ? "VISA" : null);
        payment.put("fundingType", current ? "CREDIT" : null);
        payment.put("cardLevel", current ? "CLASSIC" : null);
        return payment;
    }

    /** 等待订单进入 PAID。 */
    void awaitPaid() {
        await().atMost(Duration.ofSeconds(10))
            .until(() -> OrderStatus.PAID.getCode().equals(mapper.order(orderId()).get("order_status")));
    }

    /** 推进 PAID 后的订单版本。 */
    long orderVersion() {
        return ((Number)mapper.order(orderId()).get("version")).longValue();
    }

    /** 本例订单 ID。 */
    long orderId() {
        return fixture.getOrder().getOrderId();
    }
}
