package com.paytech.client.master.test.base.onboarding;

import com.paytech.client.master.test.base.ClientMasterMockTestBase;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.master.test.mocker.onboarding.ComplianceEventMocker;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ReviewFixture;
import com.paytech.foundation.messaging.api.DomainEvent;
import com.paytech.foundation.messaging.core.codec.DomainEventCodec;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/** Operations 审核的入口调用：O03/O04 管理 HTTP 与作为 compliance 发布审核结果信封；不含 @Test。 */
public abstract class AbstractClientReviewTest extends ClientMasterMockTestBase {
    /** Operations 操作者。 */
    protected static final long OPERATOR_ID = 9_300_000_009L;

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected ComplianceEventMocker complianceEvents;

    @Autowired
    private DomainEventCodec codec;

    /** 本服务分区队列与 DLQ 只保留本例消息。 */
    @BeforeEach
    void setUpComplianceEvents() {
        complianceEvents.beforeEach();
    }

    /** 读取分类。 */
    protected EntityExchangeResult<byte[]> classifications(long caseId) {
        return http.get().uri("/client-onboarding-admin/v1/cases/{caseId}/classifications", caseId).headers(this::operator)
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** O03 整体替换。 */
    protected EntityExchangeResult<byte[]> replace(long caseId, List<Map<String, Object>> items) {
        return http.put().uri("/client-onboarding-admin/v1/cases/{caseId}/classifications", caseId).headers(this::operator)
            .header("Content-Type", "application/json").body(JSON.writeValueAsString(Map.of("items", items)))
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** O04 确认。 */
    protected EntityExchangeResult<byte[]> confirm(long caseId) {
        return http.post().uri("/client-onboarding-admin/v1/cases/{caseId}/classification-confirmation", caseId).headers(this::operator)
            .exchange().expectBody(byte[].class).returnResult();
    }

    /** 一条分类。 */
    protected static Map<String, Object> item(String industry, String mcc, boolean primary) {
        return Map.of("industryCode", industry, "mccCode", mcc, "primary", primary);
    }

    /** 响应 JSON。 */
    protected static JsonNode body(EntityExchangeResult<byte[]> result) {
        return JSON.readTree(result.getResponseBody());
    }

    /** Gateway 可信 Operations 头。 */
    void operator(org.springframework.http.HttpHeaders headers) {
        headers.set("X-Gateway-Actor-Subject", Long.toString(OPERATOR_ID));
        headers.set("X-Gateway-Session-Id", "ops-session-1");
        headers.set("X-Request-Id", "cct-review-test");
    }

    /** 以 compliance 身份发布审核结果信封（Schema v1，版本 2），等待 Broker 确认与路由。 */
    protected void publishResult(ReviewFixture review, long caseId, String outcome, Long decisionId, Long decidedBy) {
        complianceEvents.publish(event(review, caseId, outcome, decisionId, decidedBy));
    }

    /** 审核结果信封；Payload 字段顺序即 ClientReviewDecidedPayload.FIELDS。 */
    protected static DomainEvent event(ReviewFixture review, long caseId, String outcome, Long decisionId, Long decidedBy) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", Long.toString(caseId));
        payload.put("caseVersion", 2);
        payload.put("applicationId", Long.toString(review.app().applicationId()));
        payload.put("submissionId", review.submissionId());
        payload.put("outcome", outcome);
        payload.put("returnSection", "RETURNED".equals(outcome) ? "MATERIALS" : null);
        payload.put("publicNote", "RETURNED".equals(outcome) ? "Please upload a clearer document." : null);
        payload.put("decidedAt", "2026-10-13T10:00:00.123456Z");
        payload.put("decisionId", decisionId == null ? null : Long.toString(decisionId));
        payload.put("decidedBy", decidedBy == null ? null : Long.toString(decidedBy));
        ObjectNode node = JSON.valueToTree(payload);
        return DomainEvent.builder().eventType("ClientReviewDecided").schemaVersion(1).aggregate("DD_CASE", Long.toString(caseId))
            .aggregateVersion(2).occurredAt(Instant.parse("2026-10-13T10:00:00.123456Z")).sourceService("compliance")
            .instanceId("main").payload(node).build();
    }

    /** 发布 payloadHash 被篡改的信封。 */
    protected void publishTampered(ReviewFixture review, long caseId, String outcome, Long decisionId, Long decidedBy)
        throws Exception {
        ObjectNode envelope = (ObjectNode)JSON.readTree(codec.encode(event(review, caseId, outcome, decisionId, decidedBy)));
        envelope.put("payloadHash", "sha256:" + "0".repeat(64));
        complianceEvents.publishRaw(Long.toString(caseId), JSON.writeValueAsBytes(envelope));
    }
}
