package com.paytech.billing.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;

import com.paytech.billing.api.fact.BillingFactPayload;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * billing.events 上一条 BillingFactRecorded 信封（Schema v1）的断言：信封字段、AMQP 属性、以真实消费端解码器校验 payloadHash，以及
 * Payload 可按 billing-api {@link BillingFactPayload} 反序列化（account 的读取方式）。
 */
public class BillingFactEnvelopeChecker {
    /** Payload 字段顺序（与 BillingFactPayload 字段一致）。 */
    public static final List<String> PAYLOAD_FIELDS = List.of("factId", "factVersion", "supersedesFactId", "parentFactId",
        "factType", "originType", "status", "sourceSystem", "sourceEventKey", "sourceVersion", "sourcePayloadHash",
        "businessTime", "recordedTime", "balanceAvailableTime", "clientId", "entityNo", "localPartnerId", "financeEntityId",
        "currency", "grossAmount", "refs", "lines");

    /** 明细行字段顺序。 */
    public static final List<String> LINE_FIELDS = List.of("lineNo", "componentType", "payerRole", "payerId", "payeeRole",
        "payeeId", "direction", "postedAmount", "targetBucket");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Message message;

    private final JsonNode envelope;

    private final DomainEventCodec codec;

    /** 一条已收到的信封。 */
    public BillingFactEnvelopeChecker(Message message, JsonNode envelope, DomainEventCodec codec) {
        this.message = message;
        this.envelope = envelope;
        this.codec = codec;
    }

    /** 信封：聚合为计费事实与其版本，eventId 为四段式事件键，occurredAt 为 recordedTime；消费端解码器重算 payloadHash 一致。 */
    public BillingFactEnvelopeChecker envelope(long factId, long factVersion, String instanceId) {
        String eventId = AggregateKey.of(BillingFactPayload.AGGREGATE_TYPE, Long.toString(factId))
            .eventKey(BillingFactPayload.EVENT_TYPE, factVersion);
        DomainEvent decoded = codec.decode(message.getBody());
        var properties = message.getMessageProperties();
        assertAll(() -> assertEquals(eventId, decoded.eventId()),
            () -> assertEquals(BillingFactPayload.EVENT_TYPE, decoded.eventType()),
            () -> assertEquals(BillingFactPayload.SCHEMA_VERSION, decoded.schemaVersion()),
            () -> assertEquals(BillingFactPayload.AGGREGATE_TYPE, decoded.aggregateType()),
            () -> assertEquals(Long.toString(factId), decoded.aggregateId()),
            () -> assertEquals(factVersion, decoded.aggregateVersion()),
            () -> assertEquals("billing", decoded.sourceService()), () -> assertEquals(instanceId, decoded.instanceId()),
            () -> assertEquals(envelope.path("payload").path("recordedTime").asString(), decoded.occurredAt().toString()),
            () -> assertTrue(decoded.payloadHash().matches("sha256:[0-9a-f]{64}")),
            () -> assertEquals(eventId, properties.getMessageId()),
            () -> assertEquals(BillingFactPayload.EVENT_TYPE, properties.getType()),
            () -> assertEquals(MessageDeliveryMode.PERSISTENT, properties.getReceivedDeliveryMode()));
        return this;
    }

    /** Payload 与明细行字段顺序固定，所有标量为字符串或 null；引用为 {refType, refId, externalReferenceType, externalTransactionId}。 */
    public BillingFactEnvelopeChecker payloadShape() {
        JsonNode payload = envelope.path("payload");
        assertEquals(PAYLOAD_FIELDS, names(payload));
        payload.properties().stream().filter(entry -> !List.of("refs", "lines").contains(entry.getKey()))
            .forEach(entry -> assertTrue(entry.getValue().isString() || entry.getValue().isNull(), entry.getKey()));
        payload.path("refs").forEach(ref -> assertEquals(
            List.of("refType", "refId", "externalReferenceType", "externalTransactionId"), names(ref)));
        payload.path("lines").forEach(line -> {
            assertEquals(LINE_FIELDS, names(line));
            line.properties().forEach(entry -> assertTrue(entry.getValue().isString() || entry.getValue().isNull(),
                entry.getKey()));
        });
        return this;
    }

    /** 按 billing-api 载荷类反序列化 Payload。 */
    public BillingFactPayload payload() {
        return JSON.treeToValue(envelope.path("payload"), BillingFactPayload.class);
    }

    /** Payload 中一个标量字段的原始 JSON 文本；JSON null 返回 null。 */
    public String payloadText(String field) {
        JsonNode value = envelope.path("payload").path(field);
        return value.isNull() || value.isMissingNode() ? null : value.asString();
    }

    /** 信封 eventId。 */
    public String eventId() {
        return envelope.path("eventId").asString();
    }

    /** JSON 对象的字段名，按出现顺序。 */
    static List<String> names(JsonNode node) {
        List<String> names = new ArrayList<>();
        node.propertyNames().forEach(names::add);
        return names;
    }
}
