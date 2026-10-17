package com.paytech.billing.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;

import com.paytech.billing.api.fact.EconomicFactPayload;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * billing.events 上一条 EconomicFactRecorded 信封（Schema v1，design/08 §2）的断言：信封字段、AMQP 属性、以真实消费端解码器校验
 * payloadHash，以及 Payload 可按 billing-api {@link EconomicFactPayload} 反序列化（ledger / account 的读取方式）。
 */
public class EconomicFactEnvelopeChecker {
    /** Payload 字段顺序（与 EconomicFactPayload 字段一致）。 */
    public static final List<String> PAYLOAD_FIELDS = List.of("factId", "factVersion", "supersedesFactId", "parentFactId",
        "factType", "sourceSystem", "sourceEventKey", "sourceVersion", "sourcePayloadHash", "recordedTime",
        "paymentSucceededTime", "clientId", "entityNo", "localPartnerId", "partnerAgreementOeId", "financeEntityId",
        "currency", "grossAmount", "channelAccountId", "lines");

    private static final JsonMapper JSON = JsonMapper.builder().build();

    private final Message message;

    private final JsonNode envelope;

    private final DomainEventCodec codec;

    public EconomicFactEnvelopeChecker(Message message, JsonNode envelope, DomainEventCodec codec) {
        this.message = message;
        this.envelope = envelope;
        this.codec = codec;
    }

    /** 信封：聚合为经济事实与其版本，eventId 为四段式事件键；消费端解码器重算 payloadHash 一致。 */
    public EconomicFactEnvelopeChecker envelope(long factId, long factVersion, String instanceId) {
        String eventId = AggregateKey.of(EconomicFactPayload.AGGREGATE_TYPE, Long.toString(factId))
            .eventKey(EconomicFactPayload.EVENT_TYPE, factVersion);
        DomainEvent decoded = codec.decode(message.getBody());
        var properties = message.getMessageProperties();
        assertAll(() -> assertEquals(eventId, decoded.eventId()),
            () -> assertEquals(EconomicFactPayload.EVENT_TYPE, decoded.eventType()),
            () -> assertEquals(EconomicFactPayload.SCHEMA_VERSION, decoded.schemaVersion()),
            () -> assertEquals(EconomicFactPayload.AGGREGATE_TYPE, decoded.aggregateType()),
            () -> assertEquals(Long.toString(factId), decoded.aggregateId()),
            () -> assertEquals(factVersion, decoded.aggregateVersion()),
            () -> assertEquals("billing", decoded.sourceService()), () -> assertEquals(instanceId, decoded.instanceId()),
            () -> assertEquals(envelope.path("payload").path("recordedTime").asString(), decoded.occurredAt().toString()),
            () -> assertTrue(decoded.payloadHash().matches("sha256:[0-9a-f]{64}")),
            () -> assertEquals(eventId, properties.getMessageId()),
            () -> assertEquals(EconomicFactPayload.EVENT_TYPE, properties.getType()),
            () -> assertEquals(MessageDeliveryMode.PERSISTENT, properties.getReceivedDeliveryMode()));
        return this;
    }

    /** Payload 字段顺序固定，所有标量为字符串或 null。 */
    public EconomicFactEnvelopeChecker payloadShape() {
        JsonNode payload = envelope.path("payload");
        List<String> names = new ArrayList<>();
        payload.propertyNames().forEach(names::add);
        assertEquals(PAYLOAD_FIELDS, names);
        payload.properties().stream().filter(entry -> !"lines".equals(entry.getKey()))
            .forEach(entry -> assertTrue(entry.getValue().isString() || entry.getValue().isNull(), entry.getKey()));
        return this;
    }

    /** 按 billing-api 载荷类反序列化 Payload。 */
    public EconomicFactPayload payload() {
        return JSON.treeToValue(envelope.path("payload"), EconomicFactPayload.class);
    }

    /** 信封 eventId。 */
    public String eventId() {
        return envelope.path("eventId").asString();
    }
}
