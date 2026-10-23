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
 * payment.events 上一条 PaymentSucceeded 信封（Schema v2，design/08 §2、design/05 §2.3）的断言：信封字段、AMQP 属性、Payload 为 v1 字段
 * 加 v2 卡域字段（instrumentRef、consentToSave、customerId）、BIN 库卡属性（cardBrand、fundingType、cardLevel）与渠道交易引用
 * （channelTransactionRefId、channelReferenceType）、以真实消费端解码器校验 payloadHash，以及禁止字段。
 */
public class PaymentSucceededEnvelopeChecker {
    /**
     * Payload 固定字段顺序（Schema v2）
     * <li>v1 字段之后依次为 instrumentRef、consentToSave、customerId、cardBrand、fundingType、cardLevel、channelTransactionRefId、
     * channelReferenceType。</li>
     */
    public static final List<String> PAYLOAD_FIELDS = List.of("clientId", "orderId", "sourceType", "paymentIntentId",
        "paymentAttemptId", "sourceSubmissionId", "entityNo", "amount", "currency", "capturedAmount", "paymentSucceededTime",
        "paymentMethodCode", "channelId", "channelAccountId", "midId", "channelTransactionRef", "instrumentRef", "consentToSave",
        "customerId", "cardBrand", "fundingType", "cardLevel", "channelTransactionRefId", "channelReferenceType");

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
            () -> assertEquals(PaymentSucceededPayload.SCHEMA_VERSION, decoded.schemaVersion()),
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

    /** Payload 为 v1 字段加 v2 卡域字段（顺序固定），v1 部分与期望值一致。 */
    public PaymentSucceededEnvelopeChecker payload(Map<String, String> expected) {
        JsonNode payload = envelope.path("payload");
        assertEquals(PAYLOAD_FIELDS, names(payload));
        assertAll(expected.entrySet().stream()
            .map(entry -> () -> assertEquals(entry.getValue(), payload.path(entry.getKey()).asString(), entry.getKey())));
        return this;
    }

    /**
     * v2 卡域字段：工具引用与 Customer 为十进制字符串或 null，保存意愿为 JSON Boolean
     *
     * @param instrumentRef 期望的 Card 工具引用
     * @param consentToSave 期望的保存意愿
     * @param customerId 期望的 Customer；访客为 null
     */
    public PaymentSucceededEnvelopeChecker cardFields(String instrumentRef, boolean consentToSave, String customerId) {
        JsonNode payload = envelope.path("payload");
        assertAll(() -> assertEquals(instrumentRef, payload.path("instrumentRef").asString()),
            () -> assertTrue(payload.path("consentToSave").isBoolean(), "consentToSave 应为 JSON Boolean"),
            () -> assertEquals(consentToSave, payload.path("consentToSave").asBoolean()),
            () -> assertEquals(customerId == null, payload.path("customerId").isNull(), "customerId 是否为 null"),
            () -> assertEquals(customerId, customerId == null ? null : payload.path("customerId").asString()));
        return this;
    }

    /**
     * v2 追加的 BIN 库卡属性：有值为字符串，未识别或号段未提供为 JSON null
     *
     * @param cardBrand 期望的卡组织
     * @param fundingType 期望的借贷记类型
     * @param cardLevel 期望的卡等级
     */
    public PaymentSucceededEnvelopeChecker cardAttributes(String cardBrand, String fundingType, String cardLevel) {
        JsonNode payload = envelope.path("payload");
        assertAll(() -> assertTextOrNull(cardBrand, payload.path("cardBrand"), "cardBrand"),
            () -> assertTextOrNull(fundingType, payload.path("fundingType"), "fundingType"),
            () -> assertTextOrNull(cardLevel, payload.path("cardLevel"), "cardLevel"));
        return this;
    }

    /**
     * v2 追加的渠道交易引用：内部 ID 为十进制字符串，引用类型为代码字符串
     *
     * @param channelTransactionRefId 期望的渠道交易引用内部 ID
     * @param channelReferenceType 期望的引用类型代码
     */
    public PaymentSucceededEnvelopeChecker channelReference(String channelTransactionRefId, String channelReferenceType) {
        JsonNode payload = envelope.path("payload");
        assertAll(() -> assertTrue(payload.path("channelTransactionRefId").isString(), "channelTransactionRefId 应为 JSON 字符串"),
            () -> assertEquals(channelTransactionRefId, payload.path("channelTransactionRefId").asString()),
            () -> assertEquals(channelReferenceType, payload.path("channelReferenceType").asString()));
        return this;
    }

    /** 期望为 null 时字段须为 JSON null，否则为相同字符串。 */
    static void assertTextOrNull(String expected, JsonNode actual, String field) {
        if (expected == null) {
            assertTrue(actual.isNull(), field + " 应为 JSON null");
            return;
        }
        assertEquals(expected, actual.asString(), field);
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
