package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.ClientReviewOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * O01—O05、O07—O09 Client 入驻审核：真实 BFF 入站、PLT-I02 授权与 Risk/CCT 出站 HTTP
 * <li>详情聚合 Risk 任务与 CCT 申请、分类、行业目录；CCT 某部分失败只标记不可用。</li>
 * <li>命令按 Owner 合同转发可信身份与 Request ID，不转发 Idempotency-Key。</li>
 */
class ClientOnboardingReviewBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/client-onboarding-admin/v1";
    private static final String CASE = "7001";
    private static final String RISK_CASE = "{\"caseId\":\"7001\",\"caseNo\":\"DD7001\",\"applicationId\":\"8001\",\"clientId\":\"8101\","
        + "\"sourceSubmissionId\":\"sub-1\",\"qualificationType\":\"ORGANIZATION_KYB\",\"status\":\"SUBMITTED\",\"version\":\"1\","
        + "\"submittedAt\":\"2026-10-13T00:00:00.000000Z\",\"decidedAt\":null,\"publicNote\":null,\"returnSection\":null,"
        + "\"subjects\":[],\"classifications\":[],\"approvalBlockers\":[\"EVIDENCE_PENDING\"]}";
    private static final String APPLICATION = "{\"applicationId\":\"8001\",\"applicationNo\":\"APP8001\",\"clientId\":\"8101\","
        + "\"clientType\":\"ORGANIZATION\",\"subjectPartyId\":\"8201\",\"status\":\"UNDER_REVIEW\",\"savedAt\":\"2026-10-13T00:00:00.000000Z\","
        + "\"editable\":false,\"values\":{\"organization\":{\"legalName\":\"Synthetic Co\"},\"contact\":{}},\"people\":[],\"relations\":[],\"documents\":[]}";
    private static final String CLASSIFICATION = "{\"caseId\":\"7001\",\"applicationId\":\"8001\",\"confirmed\":false,\"confirmedBy\":null,"
        + "\"confirmedAt\":null,\"items\":[{\"industryCode\":\"RETAIL\",\"mccCode\":\"5411\",\"primary\":true}]}";
    private static final String OPTIONS = "{\"industries\":[{\"code\":\"RETAIL\",\"label\":\"零售\"}]}";
    private static final String COMMAND = "{\"caseId\":\"7001\",\"status\":\"RETURNED\",\"accepted\":true,\"processing\":false}";

    @Autowired
    private PlatformOperationsSimulator platform;
    @Autowired
    private ClientReviewOwnerSimulator owners;
    @LocalServerPort
    private int port;

    @BeforeEach
    void prepareReview() {
        platform.reset();
        platform.useClientReviewIdentity();
        owners.reset();
    }

    @AfterEach
    void cleanReview() {
        try {
            assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
            assertTrue(owners.unexpectedRequests().isEmpty(), owners.unexpectedRequests().toString());
        } finally {
            platform.reset();
            owners.reset();
        }
    }

    /** 详情：Risk 任务 + CCT 申请、分类、行业目录，四次 Owner 调用都携带可信身份。 */
    @Test
    void test_detailAggregatesRiskAndCct() throws Exception {
        detailOwners();

        var response = send("GET", ROOT + "/cases/" + CASE, "client-onboarding-o02", null);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertEquals(CASE, body.path("review").path("caseId").asString());
        assertEquals("AVAILABLE", body.path("applicationStatus").asString());
        assertEquals("Synthetic Co", body.path("application").path("values").path("organization").path("legalName").asString());
        assertEquals("AVAILABLE", body.path("classificationStatus").asString());
        assertEquals("5411", body.path("classification").path("items").get(0).path("mccCode").asString());
        assertEquals("RETAIL", body.path("industries").get(0).path("code").asString());
        assertEquals(4, owners.requests().size());
        assertTrustedCalls();
    }

    /** CCT 申请资料或目录不可用：对应部分标为 UNAVAILABLE，详情仍返回。 */
    @Test
    void test_detailMarksUnavailableCctParts() throws Exception {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE);
        owners.problem("GET", ROOT + "/cases/" + CASE + "/application", 503, "SERVICE_UNAVAILABLE");
        owners.problem("GET", ROOT + "/cases/" + CASE + "/classifications", 404, "REVIEW_CASE_NOT_FOUND");
        owners.reply("GET", ROOT + "/classification-options", 200, OPTIONS);

        var response = send("GET", ROOT + "/cases/" + CASE, "client-onboarding-o02", null);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertEquals("UNAVAILABLE", body.path("applicationStatus").asString());
        assertTrue(body.path("application").isNull());
        assertEquals("UNAVAILABLE", body.path("classificationStatus").asString());
        assertEquals("AVAILABLE", body.path("industriesStatus").asString());
    }

    /** Risk 任务不存在：整页 404，不读取 CCT。 */
    @Test
    void test_detailNotFound() throws Exception {
        owners.problem("GET", ROOT + "/cases/" + CASE, 404, "CLIENT_REVIEW_NOT_FOUND");

        var response = send("GET", ROOT + "/cases/" + CASE, "client-onboarding-o02", null);

        assertEquals(404, response.statusCode(), response.body());
        assertTrue(response.body().contains("CLIENT_REVIEW_NOT_FOUND"));
        assertEquals(1, owners.requests().size());
    }

    /** 列表：Query 原样到达 Risk；未知、重复或空 Query 不调用 Owner。 */
    @Test
    void test_listForwardsExactQuery() throws Exception {
        owners.reply("GET", ROOT + "/cases", 200, "{\"items\":[" + RISK_CASE + "],\"page\":1,\"size\":20,\"total\":1}");

        var response = send("GET", ROOT + "/cases?page=1&size=20&status=SUBMITTED&qualificationType=ORGANIZATION_KYB&applicationId=8001",
            "client-onboarding-o01", null);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(1, JSON.readTree(response.body()).path("total").asLong());
        var query = owners.requests().getFirst().query();
        for (String part : List.of("page=1", "size=20", "status=SUBMITTED", "qualificationType=ORGANIZATION_KYB", "applicationId=8001")) {
            assertTrue(query.contains(part), query);
        }
        for (String bad : List.of("?clientType=ORGANIZATION", "?page=1&page=2", "?status=", "?size=101", "?applicationId=012")) {
            assertEquals(400, send("GET", ROOT + "/cases" + bad, "client-onboarding-o01", null).statusCode(), bad);
        }
        assertEquals(1, owners.requests().size());
    }

    /** 六个命令：请求体按 Owner 合同转发，不带 Idempotency-Key。 */
    @Test
    void test_commandsForwardOwnerContracts() throws Exception {
        owners.reply("POST", ROOT + "/cases/" + CASE + "/evidence/9001/review", 200, COMMAND);
        owners.reply("POST", ROOT + "/cases/" + CASE + "/return", 200, COMMAND);
        owners.reply("POST", ROOT + "/cases/" + CASE + "/approval", 200, COMMAND);
        owners.reply("POST", ROOT + "/cases/" + CASE + "/rejection", 200, COMMAND);
        owners.reply("PUT", ROOT + "/cases/" + CASE + "/classifications", 200, CLASSIFICATION);
        owners.reply("POST", ROOT + "/cases/" + CASE + "/classification-confirmation", 200, CLASSIFICATION);

        assertOk(send("POST", ROOT + "/cases/" + CASE + "/evidence/9001/review", "client-onboarding-o05", "{\"outcome\":\"ACCEPTED\"}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/return", "client-onboarding-o07",
            "{\"reasonCode\":\"ADDRESS_UNCLEAR\",\"section\":\"BUSINESS\",\"publicNote\":\"请补充经营地址\"}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/approval", "client-onboarding-o08",
            "{\"reasonCode\":\"KYB_PASSED\",\"reason\":\"材料齐全\",\"publicNote\":null}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/rejection", "client-onboarding-o09",
            "{\"reasonCode\":\"KYB_FAILED\",\"reason\":\"主体不符\",\"publicNote\":\"未通过审核\"}"));
        assertOk(send("PUT", ROOT + "/cases/" + CASE + "/classifications", "client-onboarding-o03",
            "{\"items\":[{\"industryCode\":\"RETAIL\",\"mccCode\":\"5411\",\"primary\":true}]}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/classification-confirmation", "client-onboarding-o04", null));

        var calls = owners.requests();
        assertEquals(6, calls.size());
        assertEquals(Map.of("outcome", "ACCEPTED"), JSON.readValue(calls.get(0).body(), Map.class));
        assertEquals("BUSINESS", JSON.readTree(calls.get(1).body()).path("section").asString());
        assertEquals("请补充经营地址", JSON.readTree(calls.get(1).body()).path("publicNote").asString());
        assertEquals("材料齐全", JSON.readTree(calls.get(2).body()).path("reason").asString());
        assertEquals("KYB_FAILED", JSON.readTree(calls.get(3).body()).path("reasonCode").asString());
        assertTrue(JSON.readTree(calls.get(4).body()).path("items").get(0).path("primary").asBoolean());
        assertEquals(6, platform.completedAuthorizationCount());
        assertTrustedCalls();
    }

    /** Owner 业务拒绝保留错误码与对应状态。 */
    @Test
    void test_ownerRejectionsKeepCodes() throws Exception {
        owners.problem("POST", ROOT + "/cases/" + CASE + "/approval", 422, "CLIENT_REVIEW_NOT_READY");
        owners.problem("PUT", ROOT + "/cases/" + CASE + "/classifications", 409, "CLASSIFICATION_CONFIRMED");
        owners.problem("POST", ROOT + "/cases/" + CASE + "/return", 409, "CLIENT_REVIEW_CLOSED");

        var approval = send("POST", ROOT + "/cases/" + CASE + "/approval", "client-onboarding-o08",
            "{\"reasonCode\":\"KYB_PASSED\",\"reason\":\"材料齐全\",\"publicNote\":null}");
        var classification = send("PUT", ROOT + "/cases/" + CASE + "/classifications", "client-onboarding-o03",
            "{\"items\":[{\"industryCode\":\"RETAIL\",\"mccCode\":\"5411\",\"primary\":true}]}");
        var returned = send("POST", ROOT + "/cases/" + CASE + "/return", "client-onboarding-o07",
            "{\"reasonCode\":\"ADDRESS_UNCLEAR\",\"section\":\"BUSINESS\",\"publicNote\":\"请补充经营地址\"}");

        assertProblem(approval, 422, "CLIENT_REVIEW_NOT_READY");
        assertProblem(classification, 409, "CLASSIFICATION_CONFIRMED");
        assertProblem(returned, 409, "CLIENT_REVIEW_CLOSED");
    }

    /** 未授权：PLT-I02 拒绝后不调用 Owner。 */
    @Test
    void test_deniedDoesNotCallOwner() throws Exception {
        platform.allow(false);

        assertEquals(403, send("POST", ROOT + "/cases/" + CASE + "/approval", "client-onboarding-o08",
            "{\"reasonCode\":\"KYB_PASSED\",\"reason\":\"材料齐全\",\"publicNote\":null}").statusCode());
        assertTrue(owners.requests().isEmpty());
    }

    /** 不合法的命令 Body 在 BFF 拒绝，不调用 Owner。 */
    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"reasonCode\":\"R\",\"section\":\"OTHER\",\"publicNote\":\"x\"}",
        "{\"reasonCode\":\"R\",\"section\":\"BASIC\",\"publicNote\":\"x\",\"internal\":\"y\"}",
        "{\"reasonCode\":\"R\",\"section\":\"BASIC\",\"section\":\"PEOPLE\",\"publicNote\":\"x\"}", "[]"})
    void test_invalidReturnBodyNeverReachesOwner(String body) throws Exception {
        var response = send("POST", ROOT + "/cases/" + CASE + "/return", "client-onboarding-o07", body);

        assertTrue(response.statusCode() == 400 || response.statusCode() == 422, response.statusCode() + response.body());
        assertTrue(owners.requests().isEmpty());
    }

    /** Owner 返回其他任务的结果：上游响应无效 502，不暴露原文。 */
    @Test
    void test_mismatchedOwnerResponseIsRejected() throws Exception {
        owners.reply("POST", ROOT + "/cases/" + CASE + "/rejection", 200, COMMAND.replace("7001", "7002"));

        var response = send("POST", ROOT + "/cases/" + CASE + "/rejection", "client-onboarding-o09",
            "{\"reasonCode\":\"KYB_FAILED\",\"reason\":\"主体不符\",\"publicNote\":null}");

        assertEquals(502, response.statusCode(), response.body());
        assertFalse(response.body().contains("7002"));
        assertEquals(1, owners.requests().size());
    }

    private void detailOwners() {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE);
        owners.reply("GET", ROOT + "/cases/" + CASE + "/application", 200, APPLICATION);
        owners.reply("GET", ROOT + "/cases/" + CASE + "/classifications", 200, CLASSIFICATION);
        owners.reply("GET", ROOT + "/classification-options", 200, OPTIONS);
    }

    private void assertTrustedCalls() {
        for (var call : owners.requests()) {
            assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"), call.path());
            assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"), call.path());
            assertEquals("client-review-browser", call.header("X-Request-Id"), call.path());
            assertNull(call.header("Idempotency-Key"), call.path());
        }
    }

    private static void assertOk(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
    }

    private static void assertProblem(HttpResponse<String> response, int status, String code) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"code\":\"" + code + "\""), response.body());
    }

    /** 带 Gateway 可信头访问 BFF。 */
    private HttpResponse<String> send(String method, String path, String route, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", "client-review-browser"),
            Map.entry("Idempotency-Key", "browser-key-0000000001"),
            Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"),
            Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID),
            Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", route),
            Map.entry("X-Gateway-Context-Type", "TENANT"), Map.entry("X-Gateway-Tenant-Type", "INTERNAL"),
            Map.entry("X-Gateway-Tenant-Id", "tenant_10001"), Map.entry("X-Gateway-Tenant-Member-Id", "member_10001"))
            .forEach(builder::header);
        if (!"GET".equals(method)) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
