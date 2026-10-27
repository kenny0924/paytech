package com.paytech.client.master.auto.ext.mq.onboarding;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.client.master.service.handler.ClientTenantProjectionHandler;
import com.paytech.client.master.test.base.onboarding.AbstractClientReviewTest;
import com.paytech.client.master.test.mocker.onboarding.OnboardingApplicationMocker.ReviewFixture;
import com.paytech.foundation.messaging.api.DomainEvent;

import tools.jackson.databind.json.JsonMapper;

/**
 * ClientReviewDecided v2 与 QualificationChanged：批准冻结资料（内容摘要、释放草稿编辑权）、退回保存补件期限、退回超期 EXPIRED 落为撤回、
 * CLIENT 资格撤销挂起 Client 并投影 Platform。
 *
 * @see com.paytech.client.master.ext.mq.ClientReviewResultListener
 * @see com.paytech.client.master.ext.mq.QualificationChangedListener
 */
class ClientReviewLifecycleV2Test extends AbstractClientReviewTest {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String DUE_TIME = "2026-11-10T00:00:00Z";

    /** 批准：主体资料写入 64 位内容摘要、清空草稿编辑权并保持当前槽位，供 Checkout 法律资料核对。 */
    @Test
    void test_approvedFreezesProfile() {
        var review = applications.underReview(ACCOUNT_ID);
        owners.mockTenantProjection(review.app().clientId(), ACCOUNT_ID, review.firstPayloadHash());

        complianceEvents.publish(v2(review, "APPROVED", applications.nextId(), null));

        waitUntil(() -> owners.projectionRequests().size() == 1, Duration.ofSeconds(10));
        var profile = mapper.applicationProfileState(review.app().applicationId());
        assertTrue(String.valueOf(profile.get("content_hash")).matches("[0-9a-f]{64}"), String.valueOf(profile.get("content_hash")));
        assertNull(profile.get("draft_application_id"));
        assertEquals(Boolean.TRUE, profile.get("current_slot"));
        assertNotNull(profile.get("approved_time"));
        assertEquals("ACTIVE", mapper.clientStatus(review.app().clientId()));
    }

    /**
     * 退回后需求清单（B3）：审核员的需求级错误覆盖同键本地项，awaitingActionFrom=CLIENT 并带补件期限；本地才能发现的问题照常列出；
     * 期限已过为 PAST_DUE；不影响 ready 的本地计算
     */
    @Test
    void test_returnedErrorsInRequirements() {
        var review = applications.underReview(ACCOUNT_ID);
        owners.mockStandardPrograms();
        complianceEvents.publish(v2(review, "RETURNED", null, DUE_TIME));
        waitUntil(() -> "RETURNED".equals(mapper.review(review.app().applicationId()).get("status")), Duration.ofSeconds(10));
        String party = Long.toString(review.app().partyId());
        owners.mockReturnedResult(review.app().applicationId(), review.caseId(), DUE_TIME,
            new String[] {"document.IDENTITY", party, "DOCUMENT_UNREADABLE", "UPLOAD_DOCUMENT"},
            new String[] {"individual.birthPlace", party, "FIELD_FORMAT_INVALID", "PROVIDE_FIELD"},
            new String[] {"declaration.PLATFORM_TERMS", null, "DECLARATION_MISSING", "SIGN_DECLARATION"});

        var items = requirements(review.app().applicationId()).path("items");

        var identity = item(items, "document.IDENTITY");
        assertEquals("CURRENTLY_DUE", identity.path("status").asString());
        assertEquals("CLIENT", identity.path("awaitingActionFrom").asString());
        assertEquals("DOCUMENT_UNREADABLE", identity.path("errorCode").asString());
        assertEquals("UPLOAD_DOCUMENT", identity.path("remediationCode").asString());
        assertEquals("2026-11-10T00:00:00.000000Z", identity.path("dueTime").asString());
        assertEquals("DOCUMENT", identity.path("requirementKind").asString());
        assertEquals(1, items.valueStream().filter(node -> "document.IDENTITY".equals(node.path("requirementKey").asString())).count());
        assertEquals("FIELD_FORMAT_INVALID", item(items, "individual.birthPlace").path("errorCode").asString());
        assertEquals("SATISFIED", item(items, "individual.givenName").path("status").asString());
        var terms = item(items, "declaration.PLATFORM_TERMS");
        assertEquals(party, terms.path("subjectPartyId").asString(), "申请级错误归主主体");
        assertEquals("DECLARATION_MISSING", terms.path("errorCode").asString());

        owners.mockReturnedResult(review.app().applicationId(), review.caseId(), "2026-01-01T00:00:00Z",
            new String[] {"document.IDENTITY", party, "DOCUMENT_UNREADABLE", "UPLOAD_DOCUMENT"});

        assertEquals("PAST_DUE", item(requirements(review.app().applicationId()).path("items"), "document.IDENTITY").path("status")
            .asString());
    }

    /** 通过真实 HTTP 读取需求清单。 */
    tools.jackson.databind.JsonNode requirements(long applicationId) {
        var result = http.get().uri("/client-onboarding/v1/applications/{id}/requirements", applicationId).headers(headers -> {
            headers.set("X-Caller-Service", "client-console-bff");
            headers.set("X-Gateway-Actor-Subject", Long.toString(ACCOUNT_ID));
            headers.set("X-Gateway-Session-Id", Long.toString(SESSION_ID));
            headers.set("X-Request-Id", "cct-requirements-test");
        }).exchange().expectBody(byte[].class).returnResult();
        assertEquals(200, result.getStatus().value(), new String(result.getResponseBody()));
        return body(result);
    }

    /** 按需求键取清单项。 */
    static tools.jackson.databind.JsonNode item(tools.jackson.databind.JsonNode items, String key) {
        return items.valueStream().filter(node -> key.equals(node.path("requirementKey").asString())).findFirst()
            .orElseThrow(() -> new AssertionError("缺少需求项 " + key + ": " + items));
    }

    /** v2 退回：保存补件期限；随后 EXPIRED 把申请落为 WITHDRAWN 并释放名额，不再调用 compliance。 */
    @Test
    void test_returnedThenExpired() {
        var review = applications.underReview(ACCOUNT_ID);

        complianceEvents.publish(v2(review, "RETURNED", null, DUE_TIME));

        waitUntil(() -> "RETURNED".equals(mapper.review(review.app().applicationId()).get("status")), Duration.ofSeconds(10));
        var terms = mapper.applicationTerms(review.app().applicationId());
        assertEquals(Instant.parse(DUE_TIME), ((java.time.LocalDateTime)terms.get("review_due_time")).toInstant(java.time.ZoneOffset.UTC));

        complianceEvents.publish(v2(review, "EXPIRED", null, null, 3));

        waitUntil(() -> "WITHDRAWN".equals(mapper.review(review.app().applicationId()).get("status")), Duration.ofSeconds(10));
        assertNull(mapper.review(review.app().applicationId()).get("active_slot"));
        assertEquals(0, owners.count("withdraw:"));
    }

    /** CLIENT 资格 REVOKED：ACTIVE Client 挂起，追加第三版生命周期历史并投影 SUSPENDED；其他角色的撤销忽略。 */
    @Test
    void test_qualificationRevokedSuspendsClient() {
        var review = applications.underReview(ACCOUNT_ID);
        owners.mockTenantProjection(review.app().clientId(), ACCOUNT_ID, review.firstPayloadHash());
        complianceEvents.publish(v2(review, "APPROVED", applications.nextId(), null));
        waitUntil(() -> owners.projectionRequests().size() == 1 && mapper.events(ClientTenantProjectionHandler.EVENT_TYPE,
            Long.toString(mapper.clientChangeId(review.app().clientId(), 2))).isEmpty(), Duration.ofSeconds(10));

        complianceEvents.publish(qualification("LOCAL_PARTNER", Long.toString(applications.nextId()), "REVOKED"));
        long decisionId = applications.nextId();
        complianceEvents.publish(qualification("CLIENT", Long.toString(review.app().clientId()), "REVOKED", decisionId));

        waitUntil(() -> owners.projectionRequests().size() == 2, Duration.ofSeconds(10));
        assertEquals("SUSPENDED", mapper.clientStatus(review.app().clientId()));
        var history = mapper.clientHistory(review.app().clientId());
        assertEquals(3, history.size());
        assertEquals("UPDATE", history.get(2).get("operation_type"));
        var projection = owners.projectionRequests().get(1);
        assertEquals("SUSPENDED", projection.getOwnerStatus());
        assertEquals(3, projection.getSourceVersion());
    }

    /** ClientReviewDecided v2，caseVersion 2。 */
    static DomainEvent v2(ReviewFixture review, String outcome, Long decisionId, String dueTime) {
        return v2(review, outcome, decisionId, dueTime, 2);
    }

    /** ClientReviewDecided v2；Payload 字段顺序即 ClientReviewDecidedPayload.FIELDS。 */
    static DomainEvent v2(ReviewFixture review, String outcome, Long decisionId, String dueTime, int caseVersion) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("caseId", Long.toString(review.caseId()));
        payload.put("caseVersion", caseVersion);
        payload.put("applicationId", Long.toString(review.app().applicationId()));
        payload.put("submissionId", review.submissionId());
        payload.put("outcome", outcome);
        payload.put("returnSection", "RETURNED".equals(outcome) ? "MATERIALS" : null);
        payload.put("publicNote", null);
        payload.put("decidedAt", "2026-10-27T10:00:00.123456Z");
        payload.put("decisionId", decisionId == null ? null : Long.toString(decisionId));
        payload.put("decidedBy", "APPROVED".equals(outcome) ? Long.toString(OPERATOR_ID) : null);
        payload.put("caseType", "ONBOARDING");
        payload.put("roundNo", 1);
        payload.put("tier", "STANDARD");
        payload.put("riskRating", "APPROVED".equals(outcome) ? "LOW" : null);
        payload.put("dueTime", dueTime);
        payload.put("errors", "RETURNED".equals(outcome)
            ? List.of(Map.of("requirementKey", "document.IDENTITY", "code", "DOCUMENT_UNREADABLE")) : List.of());
        return DomainEvent.builder().eventType("ClientReviewDecided").schemaVersion(2).aggregate("DD_CASE", Long.toString(review.caseId()))
            .aggregateVersion(caseVersion).occurredAt(Instant.parse("2026-10-27T10:00:00.123456Z")).sourceService("compliance")
            .instanceId("main").payload(JSON.valueToTree(payload)).build();
    }

    /** 无决定 ID 的资格变化。 */
    static DomainEvent qualification(String roleType, String roleId, String status) {
        return qualification(roleType, roleId, status, 9_300_000_000_000_777L);
    }

    /** QualificationChanged v1；Payload 字段顺序即 QualificationChangedPayload.FIELDS。 */
    static DomainEvent qualification(String roleType, String roleId, String status, long decisionId) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("qualificationId", Long.toString(decisionId + 1));
        payload.put("qualificationVersion", 2);
        payload.put("roleType", roleType);
        payload.put("roleId", roleId);
        payload.put("qualificationType", "INDIVIDUAL_KYC");
        payload.put("previousStatus", "APPROVED");
        payload.put("status", status);
        payload.put("decisionId", Long.toString(decisionId));
        payload.put("decisionSource", "OPERATIONS");
        payload.put("caseId", null);
        payload.put("validFrom", "2026-10-01T00:00:00Z");
        payload.put("validTo", null);
        payload.put("tier", "STANDARD");
        payload.put("riskRating", "HIGH");
        payload.put("jurisdictionCode", "AE");
        payload.put("responsiblePartnerId", null);
        payload.put("reasonCode", "SANCTIONS_HIT");
        payload.put("changedAt", "2026-10-27T11:00:00Z");
        return DomainEvent.builder().eventType("QualificationChanged").schemaVersion(1).aggregate("ROLE_QUALIFICATION",
            Long.toString(decisionId + 1)).aggregateVersion(2).occurredAt(Instant.parse("2026-10-27T11:00:00Z"))
            .sourceService("compliance").instanceId("main").payload(JSON.valueToTree(payload)).build();
    }
}
