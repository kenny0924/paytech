package com.paytech.compliance.test.base;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.compliance.api.request.ClientReviewRegistrationRequestBody;
import com.paytech.compliance.test.builder.ClientReviewRegistrationRequestBuilder;
import com.paytech.compliance.test.checker.JsonResponseChecker;
import com.paytech.compliance.test.mapper.ReviewTestMapper;
import com.paytech.compliance.test.mocker.ProgramPackMocker;
import com.paytech.foundation.testsupport.api.TestRequestHeaders;

import tools.jackson.databind.JsonNode;

/**
 * Client 入驻审核流程测试的入口与公共步骤（不含 @Test）
 * <li>I01 / I02 / I03 以 client-master 身份调用；工作台命令以 Gateway 可信头（操作人、会话）调用。</li>
 * <li>{@link #mockSubjects} 为请求中的每个主体配置 client-master 资料名称；筛查应答由各测试类的 BeforeEach 配置。</li>
 */
public abstract class ClientReviewFlowTestBase extends ComplianceMockTestBase {
    /** 审核员（推荐人）。 */
    protected static final long REVIEWER = 600000000000001L;

    /** 决定人。 */
    protected static final long DECIDER = 600000000000002L;

    /** 责任 PayFac。 */
    protected static final long PAYFAC = 700000000000001L;

    @Autowired
    protected ReviewTestMapper reviews;

    @Autowired
    protected ProgramPackMocker programs;

    /** 为请求中的每个主体配置筛查名称（机构为法定名称，个人为姓名与生日）。 */
    protected void mockSubjects(ClientReviewRegistrationRequestBuilder builder, String primaryName) {
        List<Long> parties = builder.partyIds();
        boolean organization = "ORGANIZATION".equals(builder.body().getClientType());
        long primary = ClientReviewRegistrationRequestBuilder.profileIdOf(parties.getFirst());
        if (organization) {
            partyProfiles.mockOrganization(primary, primaryName, builder.body().getJurisdictionCode());
        } else {
            partyProfiles.mockPerson(primary, primaryName, "Primary", "1985-04-12", builder.body().getJurisdictionCode());
        }
        for (int index = 1; index < parties.size(); index++) {
            partyProfiles.mockPerson(ClientReviewRegistrationRequestBuilder.profileIdOf(parties.get(index)), "Person" + index, "Related",
                "1990-01-01", "SA", "AE");
        }
    }

    /** I01 登记并返回响应 Checker。 */
    protected JsonResponseChecker submit(ClientReviewRegistrationRequestBody body) {
        String requestId = requestId();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", "client-master-service");
        return new JsonResponseChecker(http.post("/client-onboarding-review/v1/submissions", headers, body), objectMapper, requestId);
    }

    /** I01 登记成功并等待筛查任务结束，返回主案件 ID。 */
    protected long register(ClientReviewRegistrationRequestBuilder builder) {
        long caseId = Long.parseLong(submit(builder.build()).ok().path("caseId").asString());
        reviews.awaitScreeningSettled(caseId);
        return caseId;
    }

    /** I02 交付已确认分类（RETAIL / 5999）。 */
    protected JsonResponseChecker classify(long caseId, ClientReviewRegistrationRequestBody body) {
        return classify(caseId, body, "RETAIL");
    }

    /** I02 交付已确认分类；确认时间固定，便于同内容重放。 */
    protected JsonResponseChecker classify(long caseId, ClientReviewRegistrationRequestBody body, String industryCode) {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("applicationId", body.getApplicationId());
        request.put("submissionId", body.getSourceSubmissionId());
        request.put("confirmedBy", Long.toString(REVIEWER));
        request.put("confirmedAt", "2026-10-27T00:00:00.000000Z");
        request.put("classifications", List.of(Map.of("industryCode", industryCode, "mccCode", "5999", "primary", true)));
        String requestId = requestId();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", "client-master-service");
        return new JsonResponseChecker(http.post("/client-onboarding-review/v1/cases/" + caseId + "/classifications", headers, request),
            objectMapper, requestId);
    }

    /** I03 撤回。 */
    protected JsonResponseChecker withdraw(long caseId, String applicationId) {
        String requestId = requestId();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", "client-master-service");
        return new JsonResponseChecker(http.post("/client-onboarding-review/v1/cases/" + caseId + "/withdrawal", headers,
            Map.of("applicationId", applicationId)), objectMapper, requestId);
    }

    /** C05 客户可见结果。 */
    protected JsonResponseChecker result(String applicationId) {
        String requestId = requestId();
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Caller-Service", "client-console-bff");
        return new JsonResponseChecker(http.get("/client-onboarding-review/v1/applications/" + applicationId + "/result", headers),
            objectMapper, requestId);
    }

    /** 工作台 GET。 */
    protected JsonResponseChecker adminGet(long actor, String path) {
        String requestId = requestId();
        return new JsonResponseChecker(http.get("/client-onboarding-admin/v1" + path, adminHeaders(actor, requestId)), objectMapper,
            requestId);
    }

    /** 工作台写命令。 */
    protected JsonResponseChecker admin(long actor, HttpMethod method, String path, Object body) {
        String requestId = requestId();
        return new JsonResponseChecker(http.exchange(method, "/client-onboarding-admin/v1" + path, adminHeaders(actor, requestId), body),
            objectMapper, requestId);
    }

    /** 案件详情（REVIEWER 视角）。 */
    protected JsonNode detail(long caseId) {
        return adminGet(REVIEWER, "/cases/" + caseId).ok();
    }

    /** 接受家族内全部待核验证据。 */
    protected void acceptAllEvidence(long caseId) {
        for (JsonNode subject : detail(caseId).path("subjects")) {
            for (JsonNode requirement : subject.path("requirements")) {
                for (JsonNode evidence : requirement.path("evidence")) {
                    if ("RECEIVED".equals(evidence.path("status").asString())) {
                        admin(REVIEWER, HttpMethod.POST, "/cases/" + caseId + "/evidence/" + evidence.path("evidenceId").asString() + "/review",
                            Map.of("outcome", "ACCEPTED")).ok();
                    }
                }
            }
        }
    }

    /** 录入家族内全部待录入人工检查为 PASS。 */
    protected void passAllChecks(long caseId) {
        for (JsonNode subject : detail(caseId).path("subjects")) {
            for (JsonNode check : subject.path("checks")) {
                if ("REQUESTED".equals(check.path("status").asString())) {
                    admin(REVIEWER, HttpMethod.POST, "/cases/" + caseId + "/checks/" + check.path("checkId").asString() + "/result",
                        Map.of("outcome", "PASS", "reasonCode", "NO_ISSUE", "evidenceRef", "LIST-2026-10")).ok();
                }
            }
        }
    }

    /** 手工评级。 */
    protected JsonResponseChecker rate(long caseId, String rating) {
        return admin(REVIEWER, HttpMethod.PUT, "/cases/" + caseId + "/risk-rating", Map.of("riskRating", rating, "note", "manual CRR",
            "factors", List.of(Map.of("factorCode", "COUNTRY_RISK", "factorValue", "LOW"), Map.of("factorCode", "INDUSTRY_RISK",
                "factorValue", "MEDIUM"))));
    }

    /** 推荐。 */
    protected JsonResponseChecker recommend(long actor, long caseId, String recommendation) {
        return admin(actor, HttpMethod.POST, "/cases/" + caseId + "/recommendation", Map.of("recommendation", recommendation, "note",
            "recommendation note"));
    }

    /** 批准。 */
    protected JsonResponseChecker approve(long actor, long caseId, Long onBehalfOf) {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("reasonCode", "ALL_CHECKS_PASSED", "reason", "approved",
            "publicNote", "Welcome"));
        if (onBehalfOf != null) {
            body.put("onBehalfOfPartnerId", Long.toString(onBehalfOf));
        }
        return admin(actor, HttpMethod.POST, "/cases/" + caseId + "/approval", body);
    }

    /** 退回：一项材料错误，期限 dueDays 天。 */
    protected JsonResponseChecker returnCase(long caseId, long subjectPartyId, int dueDays) {
        Map<String, Object> error = new LinkedHashMap<>();
        error.put("requirementKey", "document.BANK");
        error.put("subjectPartyId", Long.toString(subjectPartyId));
        error.put("code", "DOCUMENT_TOO_OLD");
        error.put("reason", "Bank statement older than 6 months");
        error.put("remediation", "UPLOAD_DOCUMENT");
        return admin(REVIEWER, HttpMethod.POST, "/cases/" + caseId + "/return", Map.of("reasonCode", "MATERIAL_ISSUE",
            "errors", List.of(error), "dueDays", dueDays, "publicNote", "Please upload a recent bank statement"));
    }

    /** 让案件满足批准门禁（不含推荐）：材料、检查、分类、评级。 */
    protected void prepareForDecision(long caseId, ClientReviewRegistrationRequestBody body) {
        acceptAllEvidence(caseId);
        passAllChecks(caseId);
        classify(caseId, body).ok();
        rate(caseId, "LOW").ok();
    }

    /** 主案件状态。 */
    protected String status(long caseId) {
        return (String)reviews.caseRow(caseId).get("status");
    }

    /** 主案件版本。 */
    protected long version(long caseId) {
        return ((Number)reviews.caseRow(caseId).get("version")).longValue();
    }

    /** 门禁未满足项。 */
    protected List<String> blockers(long actor, long caseId) {
        List<String> result = new ArrayList<>();
        adminGet(actor, "/cases/" + caseId).ok().path("approvalBlockers").forEach(item -> result.add(item.asString()));
        return result;
    }

    /** 断言状态。 */
    protected void assertStatus(long caseId, String expected) {
        assertEquals(expected, status(caseId), "案件状态 " + caseId);
    }

    /** Gateway 可信头。 */
    protected static HttpHeaders adminHeaders(long actor, String requestId) {
        HttpHeaders headers = TestRequestHeaders.internal(requestId);
        headers.set("X-Gateway-Actor-Subject", Long.toString(actor));
        headers.set("X-Gateway-Session-Id", "session-" + actor);
        return headers;
    }

    /** 新请求 ID。 */
    protected static String requestId() {
        return "compliance-test-" + UUID.randomUUID();
    }

    /** 十进制 ID 文本。 */
    protected static String text(Object value) {
        return value == null ? null : value.toString();
    }

    /** 数值列转 long。 */
    protected static long number(Object value) {
        return ((Number)value).longValue();
    }

    /** 等待事件并返回 payload。 */
    protected JsonNode awaitEvent(String eventType, long aggregateId, String field, String expected) {
        return events.await(eventType, Long.toString(aggregateId), envelope -> expected == null
            || expected.equals(envelope.path("payload").path(field).asString())).path("payload");
    }

    /** 复制列表（保持只读）。 */
    protected static <T> List<T> listOf(Iterable<T> items) {
        List<T> result = new ArrayList<>();
        items.forEach(result::add);
        return result;
    }
}
