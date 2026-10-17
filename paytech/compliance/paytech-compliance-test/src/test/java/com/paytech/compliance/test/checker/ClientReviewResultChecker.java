package com.paytech.risk.test.checker.compliance;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;

import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.core.MessageProperties;

import com.paytech.risk.compliance.domain.ClientReviewAction;
import com.paytech.risk.test.mocker.compliance.ClientReviewMocker.MockedReview;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 审核结果消息 ClientReviewDecided 的 Checker
 * <li>统一信封：eventKey、eventType、sourceSystem、sourceType、sourceId、sourceVersion、occurredAt、schemaVersion、payloadHash、payload。</li>
 * <li>payloadHash 为 "sha256:" + payload 按固定字段顺序紧凑序列化后 UTF-8 字节的小写 SHA-256。</li>
 * <li>消息 Persistent、JSON、messageId 为 eventKey、type 为事件类型。</li>
 *
 * @author Kenny
 */
public final class ClientReviewResultChecker {
    /** payload 字段的固定顺序。 */
    static final List<String> PAYLOAD_FIELDS = List.of("caseId", "caseVersion", "applicationId", "submissionId", "outcome",
        "returnSection", "publicNote", "decidedAt", "decisionId", "decidedBy");

    static final List<String> ENVELOPE_FIELDS = List.of("eventKey", "eventType", "sourceSystem", "sourceType", "sourceId",
        "sourceVersion", "occurredAt", "schemaVersion", "payloadHash", "payload");

    private final Message message;

    private final JsonNode envelope;

    private final ObjectMapper json;

    public ClientReviewResultChecker(Message message, JsonNode envelope, ObjectMapper json) {
        this.message = message;
        this.envelope = envelope;
        this.json = json;
    }

    /**
     * 结果消息与结束后的主任务一致
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
    public ClientReviewResultChecker assertDecided(MockedReview review, ClientReviewAction action, long caseVersion,
        Instant decidedAt, Long decisionId, Long decidedBy, String publicNote, String returnSection) {
        String eventKey = "RISK:DD_CASE:" + review.caseId() + ":" + caseVersion + ":ClientReviewDecided";
        JsonNode payload = envelope.path("payload");
        MessageProperties properties = message.getMessageProperties();
        assertAll("消息属性", () -> assertEquals(MessageDeliveryMode.PERSISTENT, properties.getReceivedDeliveryMode()),
            () -> assertEquals("application/json", properties.getContentType()),
            () -> assertEquals(eventKey, properties.getMessageId()),
            () -> assertEquals("ClientReviewDecided", properties.getType()));
        assertAll("信封", () -> assertEquals(ENVELOPE_FIELDS, List.copyOf(envelope.propertyNames())),
            () -> assertEquals(eventKey, envelope.path("eventKey").asString()),
            () -> assertEquals("ClientReviewDecided", envelope.path("eventType").asString()),
            () -> assertEquals("risk", envelope.path("sourceSystem").asString()),
            () -> assertEquals("DD_CASE", envelope.path("sourceType").asString()),
            () -> assertEquals(Long.toString(review.caseId()), envelope.path("sourceId").asString()),
            () -> assertEquals(caseVersion, envelope.path("sourceVersion").asLong()),
            () -> assertEquals(decidedAt, Instant.parse(envelope.path("occurredAt").asString())),
            () -> assertEquals(1, envelope.path("schemaVersion").asInt()),
            () -> assertTrue(envelope.path("schemaVersion").isIntegralNumber(), "schemaVersion 为整数"),
            () -> assertEquals("sha256:" + sha256(json.writeValueAsBytes(payload)), envelope.path("payloadHash").asString()));
        assertAll("payload", () -> assertEquals(PAYLOAD_FIELDS, List.copyOf(payload.propertyNames())),
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

    static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
