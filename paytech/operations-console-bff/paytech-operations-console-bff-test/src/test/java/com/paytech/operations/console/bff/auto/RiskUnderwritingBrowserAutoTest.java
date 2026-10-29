package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UNDERWRITING_MANAGE;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UNDERWRITING_READ;
import static com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator.query;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.ReadAvailability;
import com.paytech.operations.console.bff.domain.enums.UnderwritingInputStatus;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.ClientReviewOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;
import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;
import com.paytech.risk.decision.api.control.response.ClientUnderwritingCurrentResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingCheckBody;
import com.paytech.risk.decision.api.control.response.UnderwritingPreviewResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingSnapshotBody;
import com.paytech.risk.decision.api.control.response.UnderwritingSummaryBody;
import com.paytech.risk.decision.api.enums.RiskUnderwritingErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 浏览器 HTTP 到授权、client-master / compliance / Platform 输入读取与 Risk 承保接口的完整边界
 * <li>工作台：选最近已批准申请的案件，组装输入快照（含 REQ-12 四项、来源坐标与卡组织高风险标记），来源降级与只读用户不预览。</li>
 * <li>/risk-underwriting 只记元数据日志：Client 法定名称与网站不进入日志。</li>
 * <li>命令：页面只发结论，BFF 重新读取输入后转发；页面伪造 input 被拒；来源不可用 503 且不调用 Risk；Owner 错误原样返回。</li>
 *
 * @see com.paytech.operations.console.bff.ext.rest.RiskUnderwritingController
 */
@ExtendWith(OutputCaptureExtension.class)
class RiskUnderwritingBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/risk-underwriting/v1";
    private static final String CLIENT = "8101";
    private static final String CASE = "7001";
    private static final String MASTER = "/client-master-admin/v1/clients/" + CLIENT;
    private static final String REVIEW = "/client-onboarding-admin/v1/cases/" + CASE;
    private static final String MCCS_PATH = "/platform/v1/reference-data/mccs";
    private static final String UW = "/risk-underwriting/v1";
    private static final String UNDERWRITING_ID = "990001";

    @Autowired private PlatformOperationsSimulator platform;
    @Autowired private RiskDecisionConfigurationSimulator risk;
    @Autowired private ClientReviewOwnerSimulator owners;
    @LocalServerPort private int port;

    @BeforeEach
    void prepareUnderwriting() {
        platform.reset();
        platform.useRiskIdentity();
        risk.reset();
        owners.reset();
        platform.accountLabels(Map.of(RISK_ACCOUNT_ID, "Risk Analyst"));
    }

    @AfterEach
    void cleanUnderwriting() {
        try {
            assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
            assertTrue(risk.unexpectedRequests().isEmpty(), risk.unexpectedRequests().toString());
            assertTrue(owners.unexpectedRequests().isEmpty(), owners.unexpectedRequests().toString());
        } finally {
            platform.reset();
            risk.reset();
            owners.reset();
        }
    }

    /** 工作台：最近一次已批准申请的案件、输入快照、卡组织高风险、预览与当前承保显示名。 */
    @Test
    void test_normalFlow_workspace(CapturedOutput output) throws Exception {
        mockOwners(true, "5967", true);
        risk.underwritingReply("GET", UW + "/clients/" + CLIENT + "/underwriting", 200, current(draft()));
        risk.underwritingReply("POST", UW + "/previews", 200, preview(false));

        var response = send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        JsonNode input = body.path("input");
        assertAll("工作台",
            () -> assertEquals(UnderwritingInputStatus.READY.getCode(), body.path("inputStatus").asString()),
            () -> assertTrue(body.path("canManage").asBoolean()),
            () -> assertTrue(body.path("canSubmit").asBoolean()),
            () -> assertEquals("Synthetic Co", body.path("client").path("displayName").asString()),
            () -> assertEquals("LOW", input.path("crr").asString()),
            () -> assertEquals("STANDARD", input.path("ddTier").asString()),
            () -> assertEquals("5967", input.path("mccCode").asString()),
            () -> assertTrue(input.path("networkHighRiskMcc").asBoolean()),
            () -> assertEquals("30000", input.path("expectedMonthlyVolume").asString()),
            () -> assertEquals("USD", input.path("volumeCurrency").asString()),
            () -> assertEquals("PRE_ORDER", input.path("fulfillmentType").asString()),
            () -> assertEquals(10, input.path("fulfillmentDays").asInt()),
            () -> assertEquals("APPLE_PAY,CARD", input.path("expectedPaymentMethods").asString()),
            () -> assertEquals(CASE, input.path("sourceCaseId").asString()),
            () -> assertEquals("3", input.path("sourceCaseVersion").asString()),
            () -> assertEquals("8001", input.path("sourceApplicationId").asString()),
            () -> assertEquals("4", input.path("sourceDraftRevision").asString()),
            () -> assertEquals("2026-10-20T08:00:00Z", input.path("sourceClassificationConfirmedAt").asString()),
            () -> assertEquals(ReadAvailability.AVAILABLE.getCode(), body.path("sources").path("mcc").asString()),
            () -> assertEquals("Risk Analyst", body.path("current").path("open").path("createdByLabel").asString()),
            () -> assertFalse(body.path("preview").path("autoEligible").asBoolean()),
            () -> assertEquals("50000.00", body.path("preview").path("thresholdAmount").asString()));
        JsonNode forwarded = JSON.readTree(riskCall("POST", UW + "/previews").body());
        assertEquals(5, forwarded.path("suggestedSettlementDays").asInt());
        assertEquals(CASE, forwarded.path("input").path("sourceCaseId").asString());
        assertEquals("codes=5967&page=1&size=50", platformCall(MCCS_PATH).query());
        assertTrue(response.body().contains("Synthetic Co"));
        assertFalse(output.getAll().contains("Synthetic Co"), "Client 法定名称不得进入 BFF 日志");
        assertFalse(output.getAll().contains("synthetic.example.test"), "网站不得进入 BFF 日志");
    }

    /** 来源降级：案件读取失败 SOURCE_UNAVAILABLE；没有已批准申请 DD_NOT_APPROVED；分类未确认 CLASSIFICATION_NOT_CONFIRMED。 */
    @Test
    void test_edgeCase_workspaceDegrades() throws Exception {
        mockOwners(true, "5732", false);
        owners.problem("GET", REVIEW, 503, "SERVICE_UNAVAILABLE");
        risk.underwritingReply("GET", UW + "/clients/" + CLIENT + "/underwriting", 200, current(null));
        risk.underwritingReply("POST", UW + "/previews", 200, preview(false));
        JsonNode unavailable = JSON.readTree(send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null).body());
        assertEquals(UnderwritingInputStatus.SOURCE_UNAVAILABLE.getCode(), unavailable.path("inputStatus").asString());
        assertEquals(ReadAvailability.UNAVAILABLE.getCode(), unavailable.path("sources").path("dueDiligenceCase").asString());
        assertFalse(unavailable.path("canSubmit").asBoolean());
        assertTrue(unavailable.path("input").path("crr").isNull());

        owners.reset();
        owners.reply("GET", MASTER, 200, clientDetail(false));
        JsonNode notApproved = JSON.readTree(send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null).body());
        assertEquals(UnderwritingInputStatus.DD_NOT_APPROVED.getCode(), notApproved.path("inputStatus").asString());
        assertEquals(ReadAvailability.NOT_APPLICABLE.getCode(), notApproved.path("sources").path("application").asString());

        owners.reset();
        mockOwners(false, "5732", false);
        JsonNode unconfirmed = JSON.readTree(send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null).body());
        assertEquals(UnderwritingInputStatus.CLASSIFICATION_NOT_CONFIRMED.getCode(), unconfirmed.path("inputStatus").asString());
        assertTrue(unconfirmed.path("input").path("sourceClassificationConfirmedAt").isNull());
    }

    /** 只读用户：工作台不调用预览、不可维护；维护路由 403 且不调用 Risk。 */
    @Test
    void test_permission_readOnly() throws Exception {
        platform.grantPermissions(UNDERWRITING_READ);
        mockOwners(true, "5732", false);
        risk.underwritingReply("GET", UW + "/clients/" + CLIENT + "/underwriting", 200, current(null));

        JsonNode body = JSON.readTree(send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null).body());
        assertFalse(body.path("canManage").asBoolean());
        assertTrue(body.path("preview").isNull());
        int before = risk.requests().size();
        assertEquals(403, send("POST", ROOT + "/clients/" + CLIENT + "/underwritings", "create", "{}").statusCode());
        assertEquals(before, risk.requests().size());
        assertTrue(risk.requests().stream().noneMatch(call -> call.path().equals(UW + "/previews")));
    }

    /** 建草稿与提交：页面只发结论，BFF 重新读取输入后转发；回执补显示名，201 / 200 原样。 */
    @Test
    void test_normalFlow_commandsRefetchInput() throws Exception {
        platform.grantPermissions(UNDERWRITING_READ, UNDERWRITING_MANAGE);
        mockOwners(true, "5732", false);
        risk.underwritingReply("POST", UW + "/clients/" + CLIENT + "/underwritings", 201, draft());
        risk.underwritingReply("GET", UW + "/underwritings/" + UNDERWRITING_ID, 200, draft());
        risk.underwritingReply("POST", UW + "/underwritings/" + UNDERWRITING_ID + "/submission", 200, draft());

        var created = send("POST", ROOT + "/clients/" + CLIENT + "/underwritings", "create",
            "{\"decision\":" + JSON.writeValueAsString(decision()) + "}");
        assertEquals(201, created.statusCode(), created.body());
        assertEquals("Risk Analyst", JSON.readTree(created.body()).path("createdByLabel").asString());
        JsonNode createBody = JSON.readTree(riskCall("POST", UW + "/clients/" + CLIENT + "/underwritings").body());
        assertEquals(CASE, createBody.path("input").path("sourceCaseId").asString());
        assertEquals(30, createBody.path("decision").path("observationDays").asInt());

        var submitted = send("POST", ROOT + "/underwritings/" + UNDERWRITING_ID + "/submission", "submit",
            "{\"expectedVersion\":2,\"decision\":" + JSON.writeValueAsString(decision()) + ",\"outcome\":\"APPROVE\"}");
        assertEquals(200, submitted.statusCode(), submitted.body());
        JsonNode submitBody = JSON.readTree(riskCall("POST", UW + "/underwritings/" + UNDERWRITING_ID + "/submission").body());
        assertAll("提交转发",
            () -> assertEquals(2, submitBody.path("expectedVersion").asInt()),
            () -> assertEquals("APPROVE", submitBody.path("outcome").asString()),
            () -> assertEquals("5732", submitBody.path("input").path("mccCode").asString()),
            () -> assertEquals("Synthetic Co", submitBody.path("input").path("clientDisplayName").asString()));
        for (var call : risk.requests()) {
            assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
            assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        }
    }

    /** 页面伪造 input、未知 Query 或非法 ID 返回 400，Body 字段格式返回 422；均在调用任何 Owner 前拒绝。 */
    @Test
    void test_invalidParameter_forgedInput() throws Exception {
        var forged = send("POST", ROOT + "/clients/" + CLIENT + "/underwritings", "create",
            "{\"input\":{\"crr\":\"LOW\"},\"decision\":{}}");
        assertEquals(400, forged.statusCode(), forged.body());
        assertEquals(400, send("GET", ROOT + "/underwritings?unknown=1", "list", null).statusCode());
        assertEquals(400, send("GET", ROOT + "/underwritings/abc", "detail", null).statusCode());
        var badClient = send("POST", ROOT + "/previews", "preview", "{\"clientId\":\"x\"}");
        assertEquals(422, badClient.statusCode(), badClient.body());
        assertEquals("clientId", JSON.readTree(badClient.body()).path("errors").get(0).path("field").asString());
        assertTrue(risk.requests().isEmpty());
        assertTrue(owners.requests().isEmpty());
    }

    /** 输入来源不可用：命令返回 503，不调用 Risk 写接口。 */
    @Test
    void test_businessRuleViolation_sourceUnavailable() throws Exception {
        mockOwners(true, "5732", false);
        owners.problem("GET", REVIEW + "/application", 503, "SERVICE_UNAVAILABLE");
        var response = send("POST", ROOT + "/clients/" + CLIENT + "/auto-underwritings", "auto", null);
        assertEquals(503, response.statusCode(), response.body());
        assertTrue(risk.requests().isEmpty());
    }

    /** Owner 错误原样返回：409 已有进行中、404 不存在、422 字段定位到转发 Body 路径。 */
    @Test
    void test_businessRuleViolation_ownerErrors() throws Exception {
        mockOwners(true, "5732", false);
        risk.underwritingProblem("POST", UW + "/clients/" + CLIENT + "/underwritings", 409,
            RiskUnderwritingErrorCode.RISK_UNDERWRITING_OPEN_EXISTS.getCode());
        var open = send("POST", ROOT + "/clients/" + CLIENT + "/underwritings", "create", "{}");
        assertEquals(409, open.statusCode(), open.body());
        assertEquals(RiskUnderwritingErrorCode.RISK_UNDERWRITING_OPEN_EXISTS.getCode(), JSON.readTree(open.body()).path("code").asString());

        risk.underwritingProblem("GET", UW + "/underwritings/" + UNDERWRITING_ID, 404,
            RiskUnderwritingErrorCode.RISK_UNDERWRITING_NOT_FOUND.getCode());
        var missing = send("GET", ROOT + "/underwritings/" + UNDERWRITING_ID, "detail", null);
        assertEquals(404, missing.statusCode(), missing.body());

        risk.problem(422, CommonErrorCode.VALIDATION_ERROR.getCode());
        risk.problemFields(List.of(Map.of("field", "input.mccCode", "message", "提交时 MCC 必填"),
            Map.of("field", "unknown.path", "message", "x")));
        var invalid = send("POST", ROOT + "/clients/" + CLIENT + "/auto-underwritings", "auto", null);
        assertEquals(422, invalid.statusCode(), invalid.body());
        JsonNode errors = JSON.readTree(invalid.body()).path("errors");
        assertEquals(1, errors.size(), invalid.body());
        assertEquals("input.mccCode", errors.get(0).path("field").asString());
    }

    /** 列表：待我复核以当前账号转发；本页提交人与决定人补显示名；统计简单转发。 */
    @Test
    void test_normalFlow_listAndStatistics() throws Exception {
        UnderwritingSummaryBody summary = new UnderwritingSummaryBody();
        summary.setUnderwritingId(UNDERWRITING_ID); summary.setClientId(CLIENT); summary.setClientDisplayName("Synthetic Co");
        summary.setClientNo("CL8101"); summary.setJurisdictionCode("SA"); summary.setClientType("ORGANIZATION");
        summary.setNetworkHighRiskMcc(Boolean.FALSE); summary.setVersionNo(1); summary.setStatus("SUBMITTED");
        summary.setReviewLevel("HIGH"); summary.setRequireDeposit(Boolean.FALSE); summary.setSubmittedBy("10002");
        summary.setUpdatedAt("2026-10-29T01:00:00Z");
        risk.underwritingReply("GET", UW + "/underwritings", 200, new PagingResponseBody<>(List.of(summary), 1, 10, 1));
        platform.accountLabels(Map.of("10002", "Underwriter Two"));

        var page = send("GET", ROOT + "/underwritings?pendingReviewForMe=true&status=SUBMITTED", "list", null);
        assertEquals(200, page.statusCode(), page.body());
        JsonNode item = JSON.readTree(page.body()).path("items").get(0);
        assertEquals("Underwriter Two", item.path("submittedByLabel").asString());
        assertEquals("SUBMITTED", item.path("status").asString());
        assertEquals(Map.of("status", "SUBMITTED", "pendingReviewFor", RISK_ACCOUNT_ID, "page", "1", "size", "10"),
            query(riskCall("GET", UW + "/underwritings").query()));

        var statistics = new com.paytech.risk.decision.api.control.response.UnderwritingStatisticsResponseBody();
        statistics.setEffective(1L); statistics.setDraft(2L); statistics.setSubmitted(1L);
        statistics.setApprovedThisMonth(3L); statistics.setDeclinedThisMonth(0L);
        risk.underwritingReply("GET", UW + "/underwriting-statistics", 200, statistics);
        var stats = send("GET", ROOT + "/underwriting-statistics", "statistics", null);
        assertEquals(200, stats.statusCode(), stats.body());
        assertEquals(3, JSON.readTree(stats.body()).path("approvedThisMonth").asInt());
    }

    /** Risk 回执不对应请求的承保版本：上游无效响应 502。 */
    @Test
    void test_edgeCase_ownerResponseMismatch() throws Exception {
        UnderwritingResponseBody other = draft();
        other.setUnderwritingId("990002");
        risk.underwritingReply("GET", UW + "/underwritings/" + UNDERWRITING_ID, 200, other);
        var response = send("GET", ROOT + "/underwritings/" + UNDERWRITING_ID, "detail", null);
        assertEquals(502, response.statusCode(), response.body());
    }

    /** client-master 主档、compliance 案件、申请资料、分类与 Platform MCC 的 Owner DTO。 */
    private void mockOwners(boolean confirmed, String mccCode, boolean highRisk) {
        owners.reply("GET", MASTER, 200, clientDetail(true));
        owners.reply("GET", REVIEW, 200, "{\"caseId\":\"" + CASE + "\",\"clientId\":\"" + CLIENT + "\",\"riskRating\":\"LOW\","
            + "\"tier\":\"STANDARD\",\"version\":\"3\",\"status\":\"APPROVED\"}");
        owners.reply("GET", REVIEW + "/application", 200, "{\"applicationId\":\"8001\",\"applicationNo\":\"APP8001\",\"clientId\":\""
            + CLIENT + "\",\"clientType\":\"ORGANIZATION\",\"status\":\"APPROVED\",\"editable\":false,\"draftRevision\":\"4\","
            + "\"values\":{\"business\":{\"expectedVolume\":\"30000.00\",\"expectedTicket\":\"120\",\"currency\":\"USD\","
            + "\"websiteUrl\":\"https://synthetic.example.test\",\"refundPolicyUrl\":\"https://synthetic.example.test/refunds\","
            + "\"expectedPaymentMethods\":\"APPLE_PAY,CARD\",\"fulfillmentType\":\"PRE_ORDER\",\"fulfillmentDays\":\"10\"}}}");
        owners.reply("GET", REVIEW + "/classifications", 200, "{\"caseId\":\"" + CASE + "\",\"applicationId\":\"8001\",\"confirmed\":"
            + confirmed + ",\"confirmedBy\":null,\"confirmedAt\":" + (confirmed ? "\"2026-10-20T08:00:00.000000Z\"" : "null")
            + ",\"items\":[{\"industryCode\":\"RETAIL\",\"mccCode\":\"" + mccCode + "\",\"primary\":true}]}");
        platform.groupedResponses(Map.of(MCCS_PATH, "{\"items\":[{\"mccCode\":\"" + mccCode + "\",\"name\":\"Synthetic\","
            + "\"networkHighRisk\":" + highRisk + ",\"status\":\"ACTIVE\",\"version\":1}],\"page\":1,\"size\":50,\"total\":1}"));
    }

    /** Client 主档：已批准的旧申请（案件 6990）、已批准的最近申请（案件 7001）与审核中的新申请（案件 7100）。 */
    private static String clientDetail(boolean approved) {
        String status = approved ? "APPROVED" : "UNDER_REVIEW";
        return "{\"client\":{\"clientId\":\"" + CLIENT + "\",\"clientNo\":\"CL8101\",\"clientType\":\"ORGANIZATION\",\"status\":\"ACTIVE\","
            + "\"jurisdictionCode\":\"SA\",\"legalName\":\"Synthetic Co\"},\"values\":{},\"people\":[],\"relations\":[],\"applications\":["
            + application("7990", "6990", status, "2026-09-01T00:00:00.000000Z") + ","
            + application("8001", CASE, status, "2026-10-01T00:00:00.000000Z") + ","
            + application("8100", "7100", "UNDER_REVIEW", "2026-10-26T00:00:00.000000Z") + "]}";
    }

    /** 一条申请摘要。 */
    private static String application(String applicationId, String caseId, String status, String submittedAt) {
        return "{\"applicationId\":\"" + applicationId + "\",\"status\":\"" + status + "\",\"jurisdictionCode\":\"SA\","
            + "\"submissionRound\":1,\"dueDiligenceCaseId\":\"" + caseId + "\",\"submittedAt\":\"" + submittedAt + "\","
            + "\"updatedAt\":\"" + submittedAt + "\"}";
    }

    /** 页面结论字段。 */
    private static UnderwritingDecisionBody decision() {
        UnderwritingDecisionBody decision = new UnderwritingDecisionBody();
        decision.setObservationDays(30);
        decision.setObservationTransactions(100);
        decision.setRequireDeposit(Boolean.FALSE);
        decision.setDepositBlocksCollection(Boolean.FALSE);
        decision.setSuggestedSettlementDays(5);
        decision.setHighRiskMcc(Boolean.FALSE);
        return decision;
    }

    /** Risk 的草稿版本 DTO。 */
    private static UnderwritingResponseBody draft() {
        UnderwritingSnapshotBody input = new UnderwritingSnapshotBody();
        input.setClientDisplayName("Synthetic Co"); input.setClientNo("CL8101"); input.setClientType("ORGANIZATION");
        input.setJurisdictionCode("SA"); input.setNetworkHighRiskMcc(Boolean.FALSE); input.setCrrMissing(Boolean.FALSE);
        UnderwritingDecisionBody decision = decision();
        UnderwritingResponseBody body = new UnderwritingResponseBody();
        body.setUnderwritingId(UNDERWRITING_ID); body.setClientId(CLIENT); body.setVersionNo(1); body.setStatus("DRAFT");
        body.setReviewLevel("LOW"); body.setInput(input); body.setDecision(decision); body.setCreatedBy(RISK_ACCOUNT_ID);
        body.setCreatedAt("2026-10-29T01:00:00Z"); body.setUpdatedAt("2026-10-29T01:00:00Z"); body.setVersion(2);
        return body;
    }

    /** Client 当前承保：只有进行中版本（或都为空）。 */
    private static ClientUnderwritingCurrentResponseBody current(UnderwritingResponseBody open) {
        ClientUnderwritingCurrentResponseBody body = new ClientUnderwritingCurrentResponseBody();
        body.setOpen(open);
        body.setLatest(open);
        return body;
    }

    /** Risk 预览 DTO。 */
    private static UnderwritingPreviewResponseBody preview(boolean eligible) {
        UnderwritingCheckBody check = new UnderwritingCheckBody();
        check.setCode("MCC_NOT_NETWORK_HIGH_RISK");
        check.setPassed(eligible);
        UnderwritingPreviewResponseBody body = new UnderwritingPreviewResponseBody();
        body.setRuleVersionNo(1); body.setAutoEligible(eligible); body.setChecks(List.of(check)); body.setReviewLevel("LOW");
        body.setExposureUnavailableReason("FULFILLMENT_TYPE_MISSING");
        body.setThresholdAmount("50000.00");
        body.setThresholdCurrency("USD");
        return body;
    }

    /** Risk 收到的某次调用（最后一次）。 */
    private RiskDecisionConfigurationSimulator.Request riskCall(String method, String path) {
        return risk.requests().stream().filter(call -> call.method().equals(method) && call.path().equals(path))
            .reduce((first, second) -> second).orElseThrow(() -> new AssertionError("Risk 未收到 " + method + " " + path));
    }

    /** Platform 收到的某路径调用（最后一次）。 */
    private PlatformOperationsSimulator.RecordedRequest platformCall(String path) {
        return platform.requests().stream().filter(call -> call.path().equals(path)).reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("Platform 未收到 " + path));
    }

    private HttpResponse<String> send(String method, String path, String route, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", "risk-underwriting-browser"),
            Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"),
            Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID),
            Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", "operations-risk-underwriting-" + route),
            Map.entry("X-Gateway-Context-Type", "TENANT"), Map.entry("X-Gateway-Tenant-Type", "INTERNAL"),
            Map.entry("X-Gateway-Tenant-Id", "tenant_10001"), Map.entry("X-Gateway-Tenant-Member-Id", "member_10001")).forEach(builder::header);
        if (!"GET".equals(method)) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        try (var client = HttpClient.newHttpClient()) {
            return client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
        }
    }
}
