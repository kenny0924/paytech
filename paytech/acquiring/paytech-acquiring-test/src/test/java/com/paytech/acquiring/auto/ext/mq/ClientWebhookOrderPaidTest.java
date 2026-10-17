package com.paytech.commerce.auto.ext.mq;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.util.UriComponentsBuilder;

import com.paytech.commerce.domain.checkout.CreateCheckoutContext;
import com.paytech.commerce.domain.enums.ClientWebhookEventType;
import com.paytech.commerce.domain.enums.OrderPaymentStatus;
import com.paytech.commerce.domain.enums.OrderStatus;
import com.paytech.commerce.domain.enums.PaymentIntentStatus;
import com.paytech.commerce.ext.mq.PaymentSucceededListener;
import com.paytech.commerce.test.base.order.AbstractPaymentResultTest;
import com.paytech.commerce.test.checker.webhook.ClientWebhookChecker;
import com.paytech.commerce.test.dal.PaymentResultTestMapper;
import com.paytech.commerce.test.mocker.local.CheckoutPaymentDataMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;

/**
 * 支付成功首次推进订单时冻结 order.paid 并提交 Platform；重复投递与已关闭订单补记不产生事件。
 *
 * @see PaymentSucceededListener#onMessage
 */
class ClientWebhookOrderPaidTest extends AbstractPaymentResultTest {
    private static final String DEAD_LETTER_QUEUE = "cg_commerce_payment_result_dlq";
    private static final long ATTEMPT_ID = 8_100_001L;

    @Autowired
    AmqpAdmin amqpAdmin;
    @Autowired
    CheckoutPaymentDataMocker data;
    @Autowired
    PaymentResultTestMapper mapper;
    @Autowired
    ClientWebhookChecker webhooks;
    private CreateCheckoutContext fixture;

    /** 本服务队列只保留本例消息；准备已绑定 Intent 的开放订单。 */
    @BeforeEach
    void setUpOrderPaidWebhook() {
        amqpAdmin.purgeQueue(PaymentSucceededListener.QUEUE, false);
        amqpAdmin.purgeQueue(DEAD_LETTER_QUEUE, false);
        fixture = boundOrder();
    }

    /** 首次支付成功提交一次，对象与 GET 订单一致、Hash 可复算；同一消息重复投递不再提交。 */
    @Test
    void test_normalFlow() throws Exception {
        var payload = payload(fixture, ATTEMPT_ID);

        publish(payload);

        var submission = webhooks.awaitSubmissions(eventId(), 1);
        webhooks.awaitTaskCompleted(eventId());
        long version = ((Number)mapper.order(orderId()).get("version")).longValue();
        webhooks.assertFrozen(submission, ClientWebhookEventType.ORDER_PAID, eventId(),
            fixture.getOrder().getClientId(), "2026-09-08T00:00:00Z", expectedObject(version));
        assertMatchesGetOrder(submission.envelope().path("data").path("object"));

        publish(payload);
        var marker = boundOrder();
        publish(payload(marker, ATTEMPT_ID + 1));
        webhooks.awaitSubmissions(ClientWebhookEventType.ORDER_PAID.eventId(marker.getOrder().getOrderNo()), 1);
        assertEquals(1, webhooks.submissions(eventId()).size(), "重复投递不能再次提交");
        webhooks.assertNoTask(eventId());
    }

    /** 已取消订单只补记支付投影，不登记也不提交 order.paid。 */
    @Test
    void test_closedOrderNotSubmitted() throws Exception {
        mapper.cancelOrder(orderId(), fixture.getOrder().getCreateTime());

        publish(payload(fixture, ATTEMPT_ID));

        await().atMost(Duration.ofSeconds(10))
            .until(() -> PaymentIntentStatus.SUCCEEDED.getCode().equals(mapper.payment(orderId()).get("intent_status")));
        assertEquals(OrderStatus.CANCELLED.getCode(), mapper.order(orderId()).get("order_status"));
        webhooks.assertNoTask(eventId());
        assertTrue(webhooks.submissions(eventId()).isEmpty());
    }

    /** 合同约定的 order.paid 对象；金额来自测试支付载荷与订单 Fixture，版本取写入后的订单版本。 */
    JsonNode expectedObject(long version) {
        var amounts = new LinkedHashMap<String, Object>();
        amounts.put("totalAmount", "1.00");
        amounts.put("paidAmount", "1.00");
        Map<String, Object> object = new LinkedHashMap<>();
        object.put("orderNo", fixture.getOrder().getOrderNo());
        object.put("clientOrderId", fixture.getOrder().getClientOrderId());
        object.put("orderStatus", OrderStatus.PAID.getCode());
        object.put("paymentStatus", OrderPaymentStatus.SUCCEEDED.getCode());
        object.put("currency", fixture.getOrder().getCurrency());
        object.put("amounts", amounts);
        object.put("paidAt", SUCCEEDED_TIME);
        object.put("version", version);
        return objectMapper.valueToTree(object);
    }

    /** 同名字段与 GET 订单的当前表示完全相同。 */
    void assertMatchesGetOrder(JsonNode object) {
        var path = UriComponentsBuilder.fromPath("/client-gateway-api/v1/orders/{orderNo}")
            .buildAndExpand(fixture.getOrder().getOrderNo()).toUriString();
        var response = httpTestInvoker.get(path,
            TestRequestHeaders.client(REQUEST_ID, Long.toString(fixture.getOrder().getClientId()), "1"));
        assertEquals(200, response.getStatus().value());
        JsonNode order = objectMapper.readTree(response.getResponseBody());
        assertAll(
            () -> assertEquals(order.path("orderNo"), object.path("orderNo")),
            () -> assertEquals(order.path("clientOrderId"), object.path("clientOrderId")),
            () -> assertEquals(order.path("orderStatus"), object.path("orderStatus")),
            () -> assertEquals(order.path("paymentStatus"), object.path("paymentStatus")),
            () -> assertEquals(order.path("currency"), object.path("currency")),
            () -> assertEquals(order.path("amounts").path("totalAmount"), object.path("amounts").path("totalAmount")),
            () -> assertEquals(order.path("amounts").path("paidAmount"), object.path("amounts").path("paidAmount")),
            () -> assertEquals(order.path("paidAt"), object.path("paidAt")));
    }

    /** 已绑定 Intent（ID 同订单）的开放订单。 */
    CreateCheckoutContext boundOrder() {
        var context = data.mockPrepared(true);
        long orderId = context.getOrder().getOrderId();
        mapper.insertPayment(orderId, orderId, context.getOrder().getCreateTime());
        return context;
    }

    /** 本例订单 ID。 */
    long orderId() {
        return fixture.getOrder().getOrderId();
    }

    /** 本例订单的 order.paid 事件 ID。 */
    String eventId() {
        return ClientWebhookEventType.ORDER_PAID.eventId(fixture.getOrder().getOrderNo());
    }
}
