package com.paytech.compliance.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;

import com.paytech.compliance.api.event.ClientReviewDecidedPayload;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;

import tools.jackson.databind.JsonNode;

/**
 * compliance.events 上一条 ClientReviewDecided 信封（Schema v1，design/08 §2）的断言：信封字段、AMQP 属性、以真实消费端解码器校验
 * payloadHash，以及 Payload 与旧 topic_risk_client_review 报文同字段、同顺序、同值。
 */
public class ClientReviewDecidedEnvelopeChecker {
    private final Message message;

    private final JsonNode envelope;

    private final DomainEventCodec codec;

    public ClientReviewDecidedEnvelopeChecker(Message message, JsonNode envelope, DomainEventCodec codec) {
        this.message = message;
        this.envelope = envelope;
        this.codec = codec;
    }

    /** 信封：聚合为审核主任务与其版本，eventId 为 AggregateKey 四段式事件键；消费端解码器重算 payloadHash 一致。 */
    public ClientReviewDecidedEnvelopeChecker envelope(long caseId, long caseVersion, Instant decidedAt, String instanceId) {
        String eventId = AggregateKey.of(ClientReviewDecidedPayload.AGGREGATE_TYPE, Long.toString(caseId))
            .eventKey(ClientReviewDecidedPayload.EVENT_TYPE, caseVersion);
        DomainEvent decoded = codec.decode(message.getBody());
        var properties = message.getMessageProperties();
        assertAll("compliance.events 信封", () -> assertEquals(eventId, decoded.eventId()),
            () -> assertEquals(ClientReviewDecidedPayload.EVENT_TYPE, decoded.eventType()),
            () -> assertEquals(ClientReviewDecidedPayload.SCHEMA_VERSION, decoded.schemaVersion()),
            () -> assertEquals(ClientReviewDecidedPayload.AGGREGATE_TYPE, decoded.aggregateType()),
            () -> assertEquals(Long.toString(caseId), decoded.aggregateId()),
            () -> assertEquals(caseVersion, decoded.aggregateVersion()),
            () -> assertEquals("compliance", decoded.sourceService()),
            () -> assertEquals(instanceId, decoded.instanceId()),
            () -> assertEquals(decidedAt, decoded.occurredAt()),
            () -> assertTrue(decoded.payloadHash().matches("sha256:[0-9a-f]{64}")),
            () -> assertEquals(eventId, properties.getMessageId()),
            () -> assertEquals(ClientReviewDecidedPayload.EVENT_TYPE, properties.getType()),
            () -> assertEquals(Long.toString(caseId), properties.getHeaders().get(MessagingTopology.AGGREGATE_ID_HEADER)),
            () -> assertEquals(MessageDeliveryMode.PERSISTENT, properties.getReceivedDeliveryMode()));
        return this;
    }

    /** Payload 与旧报文 payload 同字段、同顺序、同值。 */
    public ClientReviewDecidedEnvelopeChecker payload(JsonNode legacyPayload) {
        JsonNode payload = envelope.path("payload");
        assertEquals(ClientReviewDecidedPayload.FIELDS, List.copyOf(payload.propertyNames()), "Payload 字段与顺序");
        assertEquals(legacyPayload, payload, "Payload 与旧报文一致");
        return this;
    }
}
