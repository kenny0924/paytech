package com.paytech.payment.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;

import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;
import com.paytech.payment.execution.domain.PaymentSucceededPayload;

import tools.jackson.databind.JsonNode;

/**
 * payment.events 上一条 PaymentSucceeded 信封（Schema v1，design/08 §2）的断言：信封字段、AMQP 属性、Payload 字段与顺序、
 * 以真实消费端解码器校验 payloadHash，以及禁止字段。
 */
public class PaymentSucceededEnvelopeChecker {
    /** Payload 固定字段顺序（Schema v1）。 */
    public static final List<String> PAYLOAD_FIELDS = List.of("clientId", "orderId", "sourceType", "paymentIntentId",
        "paymentAttemptId", "sourceSubmissionId", "entityNo", "amount", "currency", "capturedAmount", "paymentSucceededTime",
        "paymentMethodCode", "channelId", "channelAccountId", "midId", "channelTransactionRef");

    private final Message message;

    private final JsonNode envelope;

    private final DomainEventCodec codec;

    public PaymentSucceededEnvelopeChecker(Message message, JsonNode envelope, DomainEventCodec codec) {
        this.message = message;
        this.envelope = envelope;
        this.codec = codec;
    }

    /** 信封：聚合为支付尝试与其版本，eventId 为 AggregateKey 四段式事件键；消费端解码器重算 payloadHash 一致。 */
    public PaymentSucceededEnvelopeChecker envelope(long attemptId, long attemptVersion, String instanceId) {
        String eventId = AggregateKey.of(PaymentSucceededPayload.AGGREGATE_TYPE, Long.toString(attemptId))
            .eventKey(PaymentSucceededPayload.EVENT_TYPE, attemptVersion);
        DomainEvent decoded = codec.decode(message.getBody());
        var properties = message.getMessageProperties();
        assertAll(() -> assertEquals(eventId, decoded.eventId()),
            () -> assertEquals(PaymentSucceededPayload.EVENT_TYPE, decoded.eventType()),
            () -> assertEquals(1, decoded.schemaVersion()),
            () -> assertEquals(PaymentSucceededPayload.AGGREGATE_TYPE, decoded.aggregateType()),
            () -> assertEquals(Long.toString(attemptId), decoded.aggregateId()),
            () -> assertEquals(attemptVersion, decoded.aggregateVersion()),
            () -> assertEquals("payment", decoded.sourceService()), () -> assertEquals(instanceId, decoded.instanceId()),
            () -> assertEquals(envelope.path("payload").path("paymentSucceededTime").asString(),
                decoded.occurredAt().toString()),
            () -> assertTrue(decoded.payloadHash().matches("sha256:[0-9a-f]{64}")),
            () -> assertEquals(eventId, properties.getMessageId()),
            () -> assertEquals(PaymentSucceededPayload.EVENT_TYPE, properties.getType()),
            () -> assertEquals(Long.toString(attemptId), properties.getHeaders().get(MessagingTopology.AGGREGATE_ID_HEADER)),
            () -> assertEquals(MessageDeliveryMode.PERSISTENT, properties.getReceivedDeliveryMode()));
        return this;
    }

    /** Payload 字段与顺序固定（第 12 步前为 v1），并与期望值一致。 */
    public PaymentSucceededEnvelopeChecker payload(Map<String, String> expected) {
        JsonNode payload = envelope.path("payload");
        assertEquals(PAYLOAD_FIELDS, names(payload));
        assertAll(expected.entrySet().stream()
            .map(entry -> () -> assertEquals(entry.getValue(), payload.path(entry.getKey()).asString(), entry.getKey())));
        return this;
    }

    /** 整条消息不含给定的受限值或字段名。 */
    public PaymentSucceededEnvelopeChecker excludes(String... forbidden) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        for (String value : forbidden) {
            assertFalse(body.contains(value), "信封包含受限内容：" + value);
        }
        return this;
    }

    /** 对象字段名，保持原顺序。 */
    static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
