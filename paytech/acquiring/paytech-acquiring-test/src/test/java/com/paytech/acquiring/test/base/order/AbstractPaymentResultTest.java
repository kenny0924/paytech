package com.paytech.acquiring.test.base.order;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;

import com.paytech.acquiring.domain.checkout.CreateCheckoutContext;
import com.paytech.acquiring.test.base.MockTestBase;

import tools.jackson.databind.json.JsonMapper;

/** 测试扮演 Payment 发布 PaymentSucceeded：按发布方合同构造信封并等待 Broker 确认；不含 @Test。 */
public abstract class AbstractPaymentResultTest extends MockTestBase {
    /** Payment 支付结果 Exchange。 */
    protected static final String RESULT_EXCHANGE = "topic_payment_result";
    /** 支付成功路由键。 */
    protected static final String SUCCEEDED_ROUTING_KEY = "payment.succeeded.v1";
    /** 本例支付成功时间。 */
    protected static final String SUCCEEDED_TIME = "2026-09-08T00:00:01.654321Z";
    /** 本例事件发生时间。 */
    protected static final String OCCURRED_AT = "2026-09-08T00:00:02.123456Z";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected RabbitTemplate rabbitTemplate;

    /** 订单 Fixture 的成功载荷，字段顺序与发布方一致；Intent ID 与订单 ID 相同。 */
    protected static Map<String, Object> payload(CreateCheckoutContext fixture, long attemptId) {
        var order = fixture.getOrder();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("clientId", Long.toString(order.getClientId()));
        payload.put("orderId", Long.toString(order.getOrderId()));
        payload.put("sourceType", "COMMERCE_ORDER");
        payload.put("paymentIntentId", Long.toString(order.getOrderId()));
        payload.put("paymentAttemptId", Long.toString(attemptId));
        payload.put("sourceSubmissionId", "7000001");
        payload.put("entityNo", order.getEntityNo());
        payload.put("amount", "1.00");
        payload.put("currency", order.getCurrency());
        payload.put("capturedAmount", "1.00");
        payload.put("paymentSucceededTime", SUCCEEDED_TIME);
        payload.put("paymentMethodCode", "CARD_TEST");
        payload.put("channelId", "301");
        payload.put("channelAccountId", "302");
        payload.put("midId", "303");
        payload.put("channelTransactionRef", "ch-ref-" + attemptId);
        return payload;
    }

    /** 载荷 JSON 字节的 SHA-256 小写十六进制。 */
    protected static String payloadHash(Map<String, Object> payload) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(payload)));
    }

    /** 统一信封；validHash=false 构造被篡改的摘要。 */
    protected static byte[] envelope(Map<String, Object> payload, boolean validHash) throws Exception {
        String attemptId = (String)payload.get("paymentAttemptId");
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventKey", eventKey(payload));
        envelope.put("eventType", "PaymentSucceeded");
        envelope.put("sourceSystem", "payment");
        envelope.put("sourceType", "PAYMENT_ATTEMPT");
        envelope.put("sourceId", attemptId);
        envelope.put("sourceVersion", 3);
        envelope.put("occurredAt", OCCURRED_AT);
        envelope.put("schemaVersion", 1);
        envelope.put("payloadHash", "sha256:" + (validHash ? payloadHash(payload) : "0".repeat(64)));
        envelope.put("payload", payload);
        return JSON.writeValueAsBytes(envelope);
    }

    /** 发布方事件键：来源版本固定为 3。 */
    protected static String eventKey(Map<String, Object> payload) {
        return "PAYMENT:PAYMENT_ATTEMPT:" + payload.get("paymentAttemptId") + ":3:PaymentSucceeded";
    }

    /** 发布合法事件并等待确认。 */
    protected void publish(Map<String, Object> payload) throws Exception {
        publishRaw(envelope(payload, true));
    }

    /** 发布原始字节并等待确认。 */
    protected void publishRaw(byte[] body) throws Exception {
        var message = MessageBuilder.withBody(body).setContentType("application/json")
            .setContentEncoding(StandardCharsets.UTF_8.name()).setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setMessageId(UUID.randomUUID().toString()).build();
        var correlation = new CorrelationData(message.getMessageProperties().getMessageId());
        rabbitTemplate.send(RESULT_EXCHANGE, SUCCEEDED_ROUTING_KEY, message, correlation);
        var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new AssertionError("测试支付结果未被 Broker 接收");
        }
    }
}
