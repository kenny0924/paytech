package com.paytech.client.contract.test.base.onboarding;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageDeliveryMode;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.client.EntityExchangeResult;

import com.paytech.client.contract.test.mocker.onboarding.OnboardingApplicationMocker.ReviewFixture;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Operations 审核的入口调用：O03/O04 管理 HTTP 与作为 Risk 发布审核结果；不含 @Test。 */
public abstract class AbstractClientReviewTest extends OnboardingMockTestBase {
    /** Operations 操作者。 */
    protected static final long OPERATOR_ID = 9_300_000_009L;
    /** Risk 审核结果 Exchange。 */
    protected static final String RESULT_EXCHANGE = "topic_risk_client_review";
    /** Risk 审核结果路由键。 */
    protected static final String RESULT_ROUTING_KEY = "client_review.decided.v1";

    private static final JsonMapper JSON = JsonMapper.builder().build();

    @Autowired
    protected RabbitTemplate rabbitTemplate;

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

    /** 按 Risk 合同发布审核结果，等待 Broker 确认。 */
    protected void publishResult(ReviewFixture review, long caseId, String outcome, Long decisionId, Long decidedBy) throws Exception {
        publishRaw(envelope(review, caseId, outcome, decisionId, decidedBy, true));
    }

    /** 统一信封；validHash=false 构造被篡改的摘要。 */
    protected static byte[] envelope(ReviewFixture review, long caseId, String outcome, Long decisionId, Long decidedBy,
        boolean validHash) throws Exception {
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
        String hash = HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(JSON.writeValueAsBytes(payload)));
        Map<String, Object> envelope = new LinkedHashMap<>();
        envelope.put("eventKey", "RISK:DD_CASE:" + caseId + ":2:ClientReviewDecided");
        envelope.put("eventType", "ClientReviewDecided");
        envelope.put("sourceSystem", "risk");
        envelope.put("sourceType", "DD_CASE");
        envelope.put("sourceId", Long.toString(caseId));
        envelope.put("sourceVersion", 2);
        envelope.put("occurredAt", "2026-10-13T10:00:00.123456Z");
        envelope.put("schemaVersion", 1);
        envelope.put("payloadHash", "sha256:" + (validHash ? hash : "0".repeat(64)));
        envelope.put("payload", payload);
        return JSON.writeValueAsBytes(envelope);
    }

    /** 发布原始字节并等待确认。 */
    protected void publishRaw(byte[] body) throws Exception {
        var message = MessageBuilder.withBody(body).setContentType("application/json")
            .setContentEncoding(StandardCharsets.UTF_8.name()).setDeliveryMode(MessageDeliveryMode.PERSISTENT)
            .setMessageId(java.util.UUID.randomUUID().toString()).build();
        var correlation = new CorrelationData(message.getMessageProperties().getMessageId());
        rabbitTemplate.send(RESULT_EXCHANGE, RESULT_ROUTING_KEY, message, correlation);
        var confirm = correlation.getFuture().get(10, TimeUnit.SECONDS);
        if (!confirm.ack() || correlation.getReturned() != null) {
            throw new AssertionError("测试审核结果未被 Broker 接收");
        }
    }
}
