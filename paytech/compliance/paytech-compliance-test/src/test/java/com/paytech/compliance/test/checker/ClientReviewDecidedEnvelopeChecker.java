package com.paytech.compliance.test.checker;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;

import com.paytech.compliance.api.event.ClientReviewDecidedPayload;
import com.paytech.compliance.domain.ClientReviewAction;
import com.paytech.compliance.test.mocker.ClientReviewMocker.MockedReview;
import com.paytech.foundation.messaging.api.AggregateKey;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;
import com.paytech.foundation.messaging.core.topology.MessagingTopology;

import tools.jackson.databind.JsonNode;

/**
 * compliance.events 上一条 ClientReviewDecided 信封（Schema v1，design/08 §2）的断言：信封字段、AMQP 属性、以真实消费端解码器校验
 * payloadHash，以及 Payload 字段顺序与结束后的主任务、决定一致（旧 topic_risk_client_review 第 08 步停发，原报文 Checker 的 Payload 断言移入本类）。
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

    /**
     * Payload 与结束后的主任务一致
     *
     * @param review 主任务
     * @param action 审核结论
     * @param caseVersion 结束后的主任务版本
     * @param decidedAt 主任务结束时间（落库值）
     * @param decisionId 主任务最终决定 ID；退回为 null
     * @param decidedBy 决定人；退回为 null
     * @param publicNote 客户可见说明
     * @param returnSection 退回分区
     * @return 当前 Checker
     */
    public ClientReviewDecidedEnvelopeChecker payload(MockedReview review, ClientReviewAction action, long caseVersion,
        Instant decidedAt, Long decisionId, Long decidedBy, String publicNote, String returnSection) {
        JsonNode payload = envelope.path("payload");
        assertAll("payload", () -> assertEquals(ClientReviewDecidedPayload.FIELDS, List.copyOf(payload.propertyNames())),
            () -> assertEquals(Long.toString(review.caseId()), payload.path("caseId").asString()),
            () -> assertEquals(caseVersion, payload.path("caseVersion").asLong()),
            () -> assertEquals(Long.toString(review.applicationId()), payload.path("applicationId").asString()),
            () -> assertEquals(review.submissionId(), payload.path("submissionId").asString()),
            () -> assertEquals(action.getCaseStatus(), payload.path("outcome").asString()),
            () -> assertText(returnSection, payload.path("returnSection")),
            () -> assertText(publicNote, payload.path("publicNote")),
            () -> assertEquals(decidedAt, Instant.parse(payload.path("decidedAt").asString())),
            () -> assertText(decisionId == null ? null : Long.toString(decisionId), payload.path("decisionId")),
            () -> assertText(decidedBy == null ? null : Long.toString(decidedBy), payload.path("decidedBy")));
        return this;
    }

    /** 可空字符串；空值为 JSON null。 */
    static void assertText(String expected, JsonNode actual) {
        if (expected == null) {
            assertTrue(actual.isNull(), "应为 null");
            return;
        }
        assertTrue(actual.isString(), "应为字符串");
        assertEquals(expected, actual.asString());
    }
}
