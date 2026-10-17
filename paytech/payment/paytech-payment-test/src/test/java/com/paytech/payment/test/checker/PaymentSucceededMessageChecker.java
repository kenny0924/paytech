package com.paytech.payment.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;

import com.paytech.payment.integration.mq.PaymentResultRabbitPublisher;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 一条 PaymentSucceeded 消息的断言：消息属性、统一信封、固定顺序 Payload、可重算的 Payload Hash 与禁止字段。 */
public class PaymentSucceededMessageChecker {
    /** Payload 固定字段顺序。 */
    public static final List<String> PAYLOAD_FIELDS = List.of("clientId", "orderId", "sourceType", "paymentIntentId",
        "paymentAttemptId", "sourceSubmissionId", "entityNo", "amount", "currency", "capturedAmount", "paymentSucceededTime",
        "paymentMethodCode", "channelId", "channelAccountId", "midId", "channelTransactionRef");

    /** 信封固定字段顺序。 */
    public static final List<String> ENVELOPE_FIELDS = List.of("eventKey", "eventType", "sourceSystem", "sourceType",
        "sourceId", "sourceVersion", "occurredAt", "schemaVersion", "payloadHash", "payload");

    private final Message message;

    private final JsonNode envelope;

    private final JsonMapper json;

    public PaymentSucceededMessageChecker(Message message, JsonNode envelope, JsonMapper json) {
        this.message = message;
        this.envelope = envelope;
        this.json = json;
    }

    /** 消息属性与信封：事件键含尝试 ID 与版本，messageId 等于事件键，持久化 UTF-8 JSON。 */
    public PaymentSucceededMessageChecker envelope(long attemptId, long attemptVersion) {
        String eventKey = "PAYMENT:PAYMENT_ATTEMPT:" + attemptId + ":" + attemptVersion + ":PaymentSucceeded";
        var properties = message.getMessageProperties();
        assertAll(() -> assertEquals(ENVELOPE_FIELDS, names(envelope)),
            () -> assertEquals(eventKey, envelope.path("eventKey").asString()),
            () -> assertEquals(PaymentResultRabbitPublisher.SUCCEEDED_EVENT_TYPE, envelope.path("eventType").asString()),
            () -> assertEquals("payment", envelope.path("sourceSystem").asString()),
            () -> assertEquals("PAYMENT_ATTEMPT", envelope.path("sourceType").asString()),
            () -> assertEquals(Long.toString(attemptId), envelope.path("sourceId").asString()),
            () -> assertEquals(attemptVersion, envelope.path("sourceVersion").asLong()),
            () -> assertEquals(1, envelope.path("schemaVersion").asInt()),
            () -> assertEquals(envelope.path("payload").path("paymentSucceededTime").asString(),
                envelope.path("occurredAt").asString()),
            () -> assertEquals(eventKey, properties.getMessageId()),
            () -> assertEquals(PaymentResultRabbitPublisher.SUCCEEDED_EVENT_TYPE, properties.getType()),
            () -> assertEquals("application/json", properties.getContentType()),
            () -> assertEquals(StandardCharsets.UTF_8.name(), properties.getContentEncoding()),
            () -> assertEquals(MessageDeliveryMode.PERSISTENT, properties.getReceivedDeliveryMode()));
        return this;
    }

    /** Payload 字段顺序固定、全部为字符串，并与期望值一致；成功时间为 UTC RFC 3339。 */
    public PaymentSucceededMessageChecker payload(Map<String, String> expected) {
        JsonNode payload = envelope.path("payload");
        assertEquals(PAYLOAD_FIELDS, names(payload));
        assertAll(PAYLOAD_FIELDS.stream()
            .map(field -> () -> assertTrue(payload.path(field).isString(), "Payload 字段必须为字符串：" + field)));
        assertAll(expected.entrySet().stream()
            .map(entry -> () -> assertEquals(entry.getValue(), payload.path(entry.getKey()).asString(), entry.getKey())));
        Instant.parse(payload.path("paymentSucceededTime").asString());
        return this;
    }

    /** payloadHash 等于 payload 按原字段顺序序列化后的 UTF-8 字节 SHA-256。 */
    public PaymentSucceededMessageChecker payloadHashRecomputable() {
        byte[] payload = json.writeValueAsBytes(envelope.path("payload"));
        assertEquals("sha256:" + sha256(payload), envelope.path("payloadHash").asString());
        return this;
    }

    /** 整条消息不含给定的受限值或字段名。 */
    public PaymentSucceededMessageChecker excludes(String... forbidden) {
        String body = new String(message.getBody(), StandardCharsets.UTF_8);
        for (String value : forbidden) {
            assertFalse(body.contains(value), "消息包含受限内容：" + value);
        }
        return this;
    }

    /** 对象字段名，保持原顺序。 */
    static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }

    /** 小写十六进制 SHA-256。 */
    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
