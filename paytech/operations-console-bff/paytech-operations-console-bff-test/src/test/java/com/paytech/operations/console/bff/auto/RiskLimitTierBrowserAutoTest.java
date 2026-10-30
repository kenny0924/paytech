package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.LIMIT_TIER_READ;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RISK_ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
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

import com.paytech.compliance.api.enums.QualificationEvaluationStatus;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.domain.enums.LimitTier;
import com.paytech.operations.console.bff.domain.enums.LimitTierCheckStatus;
import com.paytech.operations.console.bff.domain.enums.LimitTierSystemCheck;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.ClientReviewOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.CommercialOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.RiskDecisionConfigurationSimulator;
import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeListResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierChangeResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierDetailResponseBody;
import com.paytech.risk.decision.api.control.response.ClientTierQuotaBody;
import com.paytech.risk.decision.api.control.response.ClientTierPendingChangeBody;
import com.paytech.risk.decision.api.control.response.ClientTierSummaryBody;
import com.paytech.risk.decision.api.control.response.ClientUnderwritingCurrentResponseBody;
import com.paytech.risk.decision.api.control.response.TierQuotaCurrencyBody;
import com.paytech.risk.decision.api.control.response.TierQuotaListResponseBody;
import com.paytech.risk.decision.api.control.response.TierQuotaTierBody;
import com.paytech.risk.decision.api.control.response.TierStatisticsResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingResponseBody;
import com.paytech.risk.decision.api.control.response.UnderwritingSnapshotBody;
import com.paytech.risk.decision.api.enums.RiskLimitTierErrorCode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 浏览器 HTTP 到授权、risk-decision 限额档位、REQ-03 承保、compliance 资格与 commercial 合同的完整边界（REQ-04）
 * <li>工作台：档位详情、历史显示名、有效承保与复核清单四个系统项（OK / NOT_OK / UNAVAILABLE），可申请目标与申请权限。</li>
 * <li>申请：上线复核时 BFF 重新读取系统项并附页面人工项转发；页面伪造系统项被拒；来源不可用 503 且不调用 Risk 写接口；其他迁移不附清单。</li>
 * <li>转发：列表筛选透传与显示名、统计与额度原样、审批走普通 / 高级接口、Owner 错误原样、回执不符 502；/risk-limit-tiers 只记元数据日志。</li>
 *
 * @see com.paytech.operations.console.bff.ext.rest.RiskLimitTierController
 */
@ExtendWith(OutputCaptureExtension.class)
class RiskLimitTierBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ROOT = "/risk-limit-tiers/v1";
    private static final String CLIENT = "8201";
    private static final String CHANGE_ID = "770001";
    private static final String AGREEMENT_ID = "7319000000000000901";
    private static final String UW = "/risk-underwriting/v1";
    private static final String QUALIFICATIONS = "/compliance-admin/v1/qualifications";
    private static final String QUALIFICATION_QUERY = "roleType=CLIENT&roleId=" + CLIENT + "&page=1&size=50";
    private static final String AGREEMENTS = "/agreements/v1";
    private static final String OTHER_ACCOUNT = "10002";

    @Autowired private PlatformOperationsSimulator platform;
    @Autowired private RiskDecisionConfigurationSimulator risk;
    @Autowired private ClientReviewOwnerSimulator owners;
    @Autowired private CommercialOwnerSimulator commercial;
    @LocalServerPort private int port;

    /** 每例从空 Owner 配置与默认授权开始，补两个操作人的显示名。 */
    @BeforeEach
    void prepareLimitTier() {
        platform.reset();
        platform.useRiskIdentity();
        risk.reset();
        owners.reset();
        commercial.reset();
        platform.accountLabels(Map.of(RISK_ACCOUNT_ID, "Risk Analyst", OTHER_ACCOUNT, "Risk Lead"));
    }

    /** 各 Owner 与 Platform 不得收到未配置的请求；无论断言结果都清空。 */
    @AfterEach
    void cleanLimitTier() {
        try {
            assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
            assertTrue(risk.unexpectedRequests().isEmpty(), risk.unexpectedRequests().toString());
            assertTrue(owners.unexpectedRequests().isEmpty(), owners.unexpectedRequests().toString());
            assertTrue(commercial.unexpectedRequests().isEmpty(), commercial.unexpectedRequests().toString());
        } finally {
            platform.reset();
            risk.reset();
            owners.reset();
            commercial.reset();
        }
    }

    /** 工作台：四个系统项 OK、事实值、有效承保摘要、历史显示名、可申请目标；Client 名称不进日志。 */
    @Test
    void test_normalFlow_workspace(CapturedOutput output) throws Exception {
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier", 200, detail(LimitTier.VERIFICATION, null));
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier-changes", 200,
            history(change("UNDERWRITING_INIT", "APPROVED", null, null), change("REQUEST", "REJECTED", OTHER_ACCOUNT, RISK_ACCOUNT_ID)));
        mockSources("ACTIVE", "ENABLED");

        var response = send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        JsonNode facts = body.path("checklist").path("facts");
        JsonNode rejected = body.path("history").path("items").get(1);
        assertAll("工作台",
            () -> assertEquals(List.of(LimitTierSystemCheck.QUALIFICATION.getCode(), LimitTierSystemCheck.UNDERWRITING.getCode(),
                LimitTierSystemCheck.AGREEMENT.getCode(), LimitTierSystemCheck.CAPABILITIES.getCode()), codes(body, "code")),
            () -> assertEquals(List.of(LimitTierCheckStatus.OK.getCode(), LimitTierCheckStatus.OK.getCode(),
                LimitTierCheckStatus.OK.getCode(), LimitTierCheckStatus.OK.getCode()), codes(body, "status")),
            () -> assertEquals(LimitTierSystemCheck.QUALIFICATION.source(), body.path("checklist").path("items").get(0).path("source").asString()),
            () -> assertEquals("APPROVED", facts.path("qualificationStatus").asString()),
            () -> assertEquals("40.00", body.path("tier").path("quotas").get(0).path("usedToday").asString()),
            () -> assertEquals("140.00", body.path("tier").path("quotas").get(0).path("usedLast30Days").asString()),
            () -> assertEquals("250.00", body.path("tier").path("quotas").get(0).path("day").asString()),
            () -> assertTrue(body.path("tier").path("quotas").get(0).path("last30Days").isNull()),
            () -> assertTrue(facts.path("underwritingId").isString()),
            () -> assertTrue(body.path("underwriting").path("decisionSource").isString()),
            () -> assertEquals(AGREEMENT_ID, facts.path("agreementId").asString()),
            () -> assertEquals("CARD_VISA", facts.path("enabledPaymentCapabilities").get(0).asString()),
            () -> assertEquals("APPLE_PAY", facts.path("pendingPaymentCapabilities").get(0).asString()),
            () -> assertEquals("LOW", facts.path("crr").asString()),
            () -> assertEquals("990001", body.path("underwriting").path("underwritingId").asString()),
            () -> assertEquals(30, body.path("underwriting").path("observationDays").asInt()),
            () -> assertEquals(LimitTier.VERIFICATION.getCode(), body.path("tier").path("tier").asString()),
            () -> assertEquals("Synthetic Tier Co", body.path("client").path("displayName").asString()),
            () -> assertEquals("Risk Lead", rejected.path("requestedByLabel").asString()),
            () -> assertEquals("Risk Analyst", rejected.path("decidedByLabel").asString()),
            () -> assertTrue(body.path("history").path("items").get(0).path("requestedByLabel").isNull()),
            () -> assertTrue(body.path("canRequest").asBoolean()),
            () -> assertEquals(List.of(LimitTier.OBSERVATION.getCode(), LimitTier.RESTRICTED.getCode()),
                JSON.convertValue(body.path("requestableTargets"), List.class)));
        assertEquals(QUALIFICATION_QUERY, ownerCall(QUALIFICATIONS).query());
        assertTrue(response.body().contains("Synthetic Tier Co"));
        assertFalse(output.getAll().contains("Synthetic Tier Co"), "Client 法定名称不得进入 BFF 日志");
    }

    /** 来源降级：承保、资格与合同不可用只标 UNAVAILABLE；合同未生效、开通项未 ENABLED 为 NOT_OK；待审批时不可再申请。 */
    @Test
    void test_edgeCase_workspaceSourcesDegrade() throws Exception {
        ClientTierDetailResponseBody tier = detail(LimitTier.OBSERVATION, pending(OTHER_ACCOUNT));
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier", 200, tier);
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier-changes", 200,
            history(change("REQUEST", "PENDING", OTHER_ACCOUNT, null)));
        risk.underwritingProblem("GET", UW + "/clients/" + CLIENT + "/underwriting", 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        owners.problemQuery("GET", QUALIFICATIONS, QUALIFICATION_QUERY, 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        commercial.problem("GET", AGREEMENTS, 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());

        JsonNode unavailable = JSON.readTree(send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null).body());
        assertEquals(List.of(LimitTierCheckStatus.UNAVAILABLE.getCode(), LimitTierCheckStatus.UNAVAILABLE.getCode(),
            LimitTierCheckStatus.UNAVAILABLE.getCode(), LimitTierCheckStatus.UNAVAILABLE.getCode()), codes(unavailable, "status"));
        assertTrue(unavailable.path("underwriting").isNull());
        assertEquals("Risk Lead", unavailable.path("tier").path("pendingChange").path("requestedByLabel").asString());
        assertTrue(unavailable.path("requestableTargets").isEmpty(), "已有待审批申请时不可再申请");

        risk.reset();
        owners.reset();
        commercial.reset();
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier", 200, detail(LimitTier.VERIFICATION, null));
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier-changes", 200, history());
        mockSources("SIGNED", "PENDING_PREREQUISITES");
        JsonNode notOk = JSON.readTree(send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null).body());
        assertEquals(List.of(LimitTierCheckStatus.OK.getCode(), LimitTierCheckStatus.OK.getCode(),
            LimitTierCheckStatus.NOT_OK.getCode(), LimitTierCheckStatus.NOT_OK.getCode()), codes(notOk, "status"));
    }

    /** 无档位：tier 为 null、不可申请；Client 摘要取承保快照。 */
    @Test
    void test_noDataFound_workspaceWithoutTier() throws Exception {
        ClientTierDetailResponseBody none = new ClientTierDetailResponseBody();
        none.setClientId(CLIENT);
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier", 200, none);
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier-changes", 200, history());
        mockSources("ACTIVE", "ENABLED");

        JsonNode body = JSON.readTree(send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null).body());
        assertTrue(body.path("tier").isNull());
        assertTrue(body.path("requestableTargets").isEmpty());
        assertEquals("CL8201", body.path("client").path("clientNo").asString());
    }

    /** 上线复核：BFF 重新读取四个系统项并附页面人工项转发 Risk；201 与显示名原样。 */
    @Test
    void test_normalFlow_goLiveRequestRefetchesSystemItems() throws Exception {
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier", 200, detail(LimitTier.VERIFICATION, null));
        risk.limitTierReply("POST", ROOT + "/clients/" + CLIENT + "/tier-changes", 201, change("REQUEST", "PENDING", RISK_ACCOUNT_ID, null));
        mockSources("ACTIVE", "ENABLED");

        var response = send("POST", ROOT + "/clients/" + CLIENT + "/tier-changes", "request",
            "{\"expectedTierVersion\":1,\"toTier\":\"OBSERVATION\",\"reason\":\"Go live\",\"goLiveNote\":\"Go live\","
                + "\"checklist\":{\"channelRegistrationConfirmed\":true,\"integrationConfirmed\":true,\"productionVerified\":true,"
                + "\"openIssuesCleared\":true}}");

        assertEquals(201, response.statusCode(), response.body());
        assertEquals("Risk Analyst", JSON.readTree(response.body()).path("requestedByLabel").asString());
        JsonNode forwarded = JSON.readTree(riskCall("POST", ROOT + "/clients/" + CLIENT + "/tier-changes").body());
        JsonNode checklist = forwarded.path("checklist");
        assertAll("转发的申请",
            () -> assertEquals(1, forwarded.path("expectedTierVersion").asInt()),
            () -> assertEquals(LimitTier.OBSERVATION.getCode(), forwarded.path("toTier").asString()),
            () -> assertEquals("APPROVED", checklist.path("qualificationStatus").asString()),
            () -> assertEquals("990001", checklist.path("underwritingId").asString()),
            () -> assertEquals(1, checklist.path("underwritingVersionNo").asInt()),
            () -> assertEquals(AGREEMENT_ID, checklist.path("agreementId").asString()),
            () -> assertEquals("ACTIVE", checklist.path("agreementStatus").asString()),
            () -> assertEquals("CARD_VISA", checklist.path("enabledPaymentCapabilities").asString()),
            () -> assertEquals("APPLE_PAY", checklist.path("pendingPaymentCapabilities").asString()),
            () -> assertTrue(checklist.path("channelRegistrationConfirmed").asBoolean()),
            () -> assertTrue(checklist.path("openIssuesCleared").asBoolean()));
        for (var call : risk.requests()) {
            assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
            assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        }
    }

    /** 非上线复核（OBSERVATION → STANDARD）：不读系统项、不附清单。 */
    @Test
    void test_normalFlow_otherTransitionWithoutChecklist() throws Exception {
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier", 200, detail(LimitTier.OBSERVATION, null));
        risk.limitTierReply("POST", ROOT + "/clients/" + CLIENT + "/tier-changes", 201, change("REQUEST", "PENDING", RISK_ACCOUNT_ID, null));

        var response = send("POST", ROOT + "/clients/" + CLIENT + "/tier-changes", "request",
            "{\"expectedTierVersion\":2,\"toTier\":\"STANDARD\",\"reason\":\"Observation complete\","
                + "\"checklist\":{\"productionVerified\":true}}");

        assertEquals(201, response.statusCode(), response.body());
        JsonNode forwarded = JSON.readTree(riskCall("POST", ROOT + "/clients/" + CLIENT + "/tier-changes").body());
        assertTrue(forwarded.path("checklist").isNull());
        assertTrue(owners.requests().isEmpty());
        assertTrue(commercial.requests().isEmpty());
    }

    /** 页面伪造系统项、未知属性返回 400，字段格式返回 422；均在调用任何 Owner 前拒绝。 */
    @Test
    void test_invalidParameter_forgedSystemItems() throws Exception {
        var forged = send("POST", ROOT + "/clients/" + CLIENT + "/tier-changes", "request",
            "{\"expectedTierVersion\":1,\"toTier\":\"OBSERVATION\",\"reason\":\"x\",\"checklist\":{\"qualificationStatus\":\"APPROVED\"}}");
        assertEquals(400, forged.statusCode(), forged.body());
        var unknown = send("POST", ROOT + "/clients/" + CLIENT + "/tier-changes", "request",
            "{\"expectedTierVersion\":1,\"toTier\":\"OBSERVATION\",\"reason\":\"x\",\"requestedBy\":\"1\"}");
        assertEquals(400, unknown.statusCode(), unknown.body());
        var badTier = send("POST", ROOT + "/clients/" + CLIENT + "/tier-changes", "request",
            "{\"expectedTierVersion\":1,\"toTier\":\"GOLD\",\"reason\":\"x\"}");
        assertEquals(422, badTier.statusCode(), badTier.body());
        assertEquals("toTier", JSON.readTree(badTier.body()).path("errors").get(0).path("field").asString());
        assertEquals(400, send("GET", ROOT + "/client-tiers?unknown=1", "list", null).statusCode());
        assertEquals(400, send("GET", ROOT + "/clients/abc/workspace", "workspace", null).statusCode());
        assertTrue(risk.requests().isEmpty());
        assertTrue(owners.requests().isEmpty());
        assertTrue(commercial.requests().isEmpty());
    }

    /** 上线复核时系统项来源不可用：503，不调用 Risk 写接口。 */
    @Test
    void test_businessRuleViolation_goLiveSourceUnavailable() throws Exception {
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier", 200, detail(LimitTier.VERIFICATION, null));
        mockSources("ACTIVE", "ENABLED");
        owners.reset();
        owners.problemQuery("GET", QUALIFICATIONS, QUALIFICATION_QUERY, 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());

        var response = send("POST", ROOT + "/clients/" + CLIENT + "/tier-changes", "request",
            "{\"expectedTierVersion\":1,\"toTier\":\"OBSERVATION\",\"reason\":\"Go live\",\"goLiveNote\":\"Go live\"}");

        assertEquals(503, response.statusCode(), response.body());
        assertTrue(risk.requests().stream().noneMatch(call -> call.method().equals("POST")));
    }

    /** 列表筛选透传、待审批申请人显示名；统计与额度原样转发。 */
    @Test
    void test_normalFlow_listStatisticsAndQuotas() throws Exception {
        ClientTierSummaryBody summary = summary(LimitTier.OBSERVATION, pending(OTHER_ACCOUNT));
        risk.limitTierReply("GET", ROOT + "/client-tiers", 200, new PagingResponseBody<>(List.of(summary), 1, 20, 1));

        var page = send("GET", ROOT + "/client-tiers?tier=OBSERVATION&pendingApproval=true&observationDue=true&keyword=Synth&size=20",
            "list", null);

        assertEquals(200, page.statusCode(), page.body());
        assertEquals("Risk Lead", JSON.readTree(page.body()).path("items").get(0).path("pendingChange").path("requestedByLabel").asString());
        assertEquals(Map.of("tier", LimitTier.OBSERVATION.getCode(), "pendingApproval", "true", "observationDue", "true",
            "keyword", "Synth", "page", "1", "size", "20"), query(riskCall("GET", ROOT + "/client-tiers").query()));

        TierStatisticsResponseBody statistics = new TierStatisticsResponseBody();
        statistics.setVerification(2L); statistics.setObservation(1L); statistics.setStandard(4L);
        statistics.setRestricted(0L); statistics.setPendingApproval(1L); statistics.setObservationDue(1L);
        risk.limitTierReply("GET", ROOT + "/tier-statistics", 200, statistics);
        var stats = send("GET", ROOT + "/tier-statistics", "statistics", null);
        assertEquals(200, stats.statusCode(), stats.body());
        assertEquals(4, JSON.readTree(stats.body()).path("standard").asInt());

        risk.limitTierReply("GET", ROOT + "/tier-quotas", 200, quotas());
        var quotas = send("GET", ROOT + "/tier-quotas", "quotas", null);
        assertEquals(200, quotas.statusCode(), quotas.body());
        assertEquals("50.00", JSON.readTree(quotas.body()).path("tiers").get(0).path("currencies").get(0).path("single").asString());
    }

    /** 审批走普通 / 高级接口；Owner 409 / 404 原样；回执申请 ID 不符 502。 */
    @Test
    void test_businessRuleViolation_ownerErrors() throws Exception {
        String decide = "{\"expectedVersion\":1,\"comment\":\"ok\"}";
        risk.limitTierProblem("POST", ROOT + "/tier-changes/" + CHANGE_ID + "/approval", 409,
            RiskLimitTierErrorCode.RISK_LIMIT_TIER_ELEVATION_REQUIRED.getCode());
        var elevationRequired = send("POST", ROOT + "/tier-changes/" + CHANGE_ID + "/approval", "approve", decide);
        assertEquals(409, elevationRequired.statusCode(), elevationRequired.body());
        assertEquals(RiskLimitTierErrorCode.RISK_LIMIT_TIER_ELEVATION_REQUIRED.getCode(),
            JSON.readTree(elevationRequired.body()).path("code").asString());

        risk.limitTierReply("POST", ROOT + "/tier-changes/" + CHANGE_ID + "/elevated-approval", 200,
            change("REQUEST", "APPROVED", OTHER_ACCOUNT, RISK_ACCOUNT_ID));
        var approved = send("POST", ROOT + "/tier-changes/" + CHANGE_ID + "/elevated-approval", "approve-elevated", decide);
        assertEquals(200, approved.statusCode(), approved.body());
        assertEquals("Risk Analyst", JSON.readTree(approved.body()).path("decidedByLabel").asString());
        assertEquals("ok", JSON.readTree(riskCall("POST", ROOT + "/tier-changes/" + CHANGE_ID + "/elevated-approval").body())
            .path("comment").asString());

        risk.limitTierProblem("POST", ROOT + "/tier-changes/" + CHANGE_ID + "/withdrawal", 404,
            RiskLimitTierErrorCode.RISK_LIMIT_TIER_CHANGE_NOT_FOUND.getCode());
        var missing = send("POST", ROOT + "/tier-changes/" + CHANGE_ID + "/withdrawal", "withdraw", "{\"expectedVersion\":1}");
        assertEquals(404, missing.statusCode(), missing.body());

        ClientTierChangeResponseBody other = change("REQUEST", "REJECTED", OTHER_ACCOUNT, RISK_ACCOUNT_ID);
        other.setChangeId("770002");
        risk.limitTierReply("POST", ROOT + "/tier-changes/" + CHANGE_ID + "/elevated-rejection", 200, other);
        var mismatch = send("POST", ROOT + "/tier-changes/" + CHANGE_ID + "/elevated-rejection", "reject-elevated", decide);
        assertEquals(502, mismatch.statusCode(), mismatch.body());
    }

    /** 只读用户：工作台不可申请；申请路由 403 且不调用任何 Owner。 */
    @Test
    void test_permission_readOnly() throws Exception {
        platform.grantPermissions(LIMIT_TIER_READ);
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier", 200, detail(LimitTier.VERIFICATION, null));
        risk.limitTierReply("GET", ROOT + "/clients/" + CLIENT + "/tier-changes", 200, history());
        mockSources("ACTIVE", "ENABLED");

        JsonNode body = JSON.readTree(send("GET", ROOT + "/clients/" + CLIENT + "/workspace", "workspace", null).body());
        assertFalse(body.path("canRequest").asBoolean());
        assertTrue(body.path("requestableTargets").isEmpty());
        int before = risk.requests().size();
        assertEquals(403, send("POST", ROOT + "/clients/" + CLIENT + "/tier-changes", "request",
            "{\"expectedTierVersion\":1,\"toTier\":\"RESTRICTED\",\"reason\":\"x\"}").statusCode());
        assertEquals(before, risk.requests().size());
    }

    /** 三个清单来源：当前承保（有效 LOW 承保）、Client 资格（ELIGIBLE）、CLIENT 合同与两个开通项。 */
    private void mockSources(String agreementStatus, String cardExecutionStatus) {
        risk.underwritingReply("GET", UW + "/clients/" + CLIENT + "/underwriting", 200, current());
        owners.replyQuery("GET", QUALIFICATIONS, QUALIFICATION_QUERY, 200, "{\"items\":[{\"qualificationId\":\"6201\",\"roleType\":\"CLIENT\","
            + "\"roleId\":\"" + CLIENT + "\",\"qualificationType\":\"ORGANIZATION_KYB\",\"status\":\"APPROVED\",\"effectiveStatus\":\""
            + QualificationEvaluationStatus.ELIGIBLE.getCode() + "\",\"validFrom\":\"2026-10-24T00:00:00.000000Z\",\"validTo\":null,"
            + "\"currentDecisionId\":\"6301\",\"decisionSource\":\"REVIEWER\",\"reviewStatus\":\"NOT_REQUIRED\",\"version\":\"1\","
            + "\"updatedAt\":\"2026-10-24T00:00:00.000000Z\"}],\"page\":1,\"size\":50,\"total\":1}");
        commercial.reply("GET", AGREEMENTS, 200, "{\"items\":[{\"agreementId\":\"" + AGREEMENT_ID + "\",\"agreementNo\":\"AGR-1\","
            + "\"agreementName\":\"Client agreement\",\"agreementType\":\"CLIENT\",\"status\":\"" + agreementStatus + "\",\"businessMode\":\"MOR\","
            + "\"fundMode\":\"SETTLE_THROUGH_PLATFORM\",\"clientId\":\"" + CLIENT + "\",\"localPartnerId\":null,\"entityNo\":\"AE001\","
            + "\"effectiveFrom\":\"2026-10-01T00:00:00Z\",\"effectiveTo\":null,\"signedTime\":\"2026-10-02T00:00:00Z\","
            + "\"activatedTime\":\"2026-10-03T00:00:00Z\",\"changeVersion\":3,\"updateTime\":\"2026-10-03T00:00:00Z\"}],"
            + "\"page\":1,\"size\":20,\"total\":1}");
        commercial.reply("GET", AGREEMENTS + "/" + AGREEMENT_ID, 200, "{\"agreementId\":\"" + AGREEMENT_ID + "\",\"agreementNo\":\"AGR-1\","
            + "\"agreementName\":\"Client agreement\",\"agreementType\":\"CLIENT\",\"businessMode\":\"MOR\",\"fundMode\":\"SETTLE_THROUGH_PLATFORM\","
            + "\"pricingAuthority\":\"PLATFORM\",\"referralPartnerId\":null,\"clientId\":\"" + CLIENT + "\",\"localPartnerId\":null,"
            + "\"entityNo\":\"AE001\",\"operatingEntityNos\":[],\"status\":\"" + agreementStatus + "\",\"effectiveFrom\":\"2026-10-01T00:00:00Z\","
            + "\"effectiveTo\":null,\"signedTime\":\"2026-10-02T00:00:00Z\",\"activatedTime\":\"2026-10-03T00:00:00Z\",\"changeVersion\":3,"
            + "\"parties\":[],\"documents\":[],\"productCodes\":[\"ACQUIRING\"],\"capabilities\":["
            + capability("7319000000000000911", "CARD_VISA", "ACTIVE", cardExecutionStatus) + ","
            + capability("7319000000000000912", "APPLE_PAY", "ACTIVE", "PENDING_PREREQUISITES") + ","
            + capability("7319000000000000913", "MADA", "DISABLED", null) + "]}");
    }

    /** commercial 开通项 JSON。 */
    private static String capability(String id, String code, String status, String executionStatus) {
        return "{\"agreementCapabilityId\":\"" + id + "\",\"entityNo\":\"AE001\",\"capabilityCode\":\"" + code + "\",\"status\":\""
            + status + "\",\"executionStatus\":" + (executionStatus == null ? "null" : "\"" + executionStatus + "\"")
            + ",\"effectiveFrom\":\"2026-10-01T00:00:00Z\",\"effectiveTo\":null,\"rollingReserveRate\":null,\"rollingReserveDays\":null,"
            + "\"conditions\":[],\"require3ds\":false}";
    }

    /** Risk 档位详情；VERIFICATION 与 OBSERVATION 两态。 */
    private static ClientTierDetailResponseBody detail(LimitTier tier, ClientTierPendingChangeBody pendingChange) {
        ClientTierDetailResponseBody body = new ClientTierDetailResponseBody();
        fill(body, tier, pendingChange);
        body.setVersion(tier == LimitTier.VERIFICATION ? 1 : 2);
        ClientTierQuotaBody usd = new ClientTierQuotaBody();
        usd.setCurrency("USD"); usd.setSingle("50.00"); usd.setDay("250.00"); usd.setLast30Days(null);
        usd.setUsedToday("40.00"); usd.setUsedLast30Days("140.00");
        body.setQuotas(List.of(usd)); body.setUnconfiguredCurrencies(List.of("AED")); body.setUnconfiguredCurrencyPolicy("DENY");
        return body;
    }

    /** Risk 档位列表行。 */
    private static ClientTierSummaryBody summary(LimitTier tier, ClientTierPendingChangeBody pendingChange) {
        ClientTierSummaryBody body = new ClientTierSummaryBody();
        fill(body, tier, pendingChange);
        return body;
    }

    /** 列表行与详情共有的字段。 */
    private static void fill(ClientTierSummaryBody body, LimitTier tier, ClientTierPendingChangeBody pendingChange) {
        body.setClientId(CLIENT); body.setClientDisplayName("Synthetic Tier Co"); body.setClientNo("CL8201");
        body.setJurisdictionCode("SA"); body.setClientType("ORGANIZATION"); body.setCrr("LOW");
        body.setTier(tier.getCode()); body.setEffectiveTime("2026-10-29T01:00:00Z"); body.setSourceType("UNDERWRITING_INIT");
        body.setObservationDue(Boolean.FALSE); body.setPendingChange(pendingChange); body.setUpdatedAt("2026-10-29T01:00:00Z");
    }

    /** Risk 待审批摘要。 */
    private static ClientTierPendingChangeBody pending(String requestedBy) {
        ClientTierPendingChangeBody body = new ClientTierPendingChangeBody();
        body.setChangeId(CHANGE_ID); body.setFromTier(LimitTier.OBSERVATION.getCode()); body.setToTier(LimitTier.STANDARD.getCode());
        body.setApprovalLevel("STANDARD"); body.setRequestedBy(requestedBy); body.setRequestedTime("2026-10-29T02:00:00Z");
        return body;
    }

    /** Risk 变更记录。 */
    private static ClientTierChangeResponseBody change(String changeType, String status, String requestedBy, String decidedBy) {
        ClientTierChangeResponseBody body = new ClientTierChangeResponseBody();
        body.setChangeId(CHANGE_ID); body.setClientId(CLIENT); body.setChangeType(changeType);
        body.setToTier(LimitTier.OBSERVATION.getCode()); body.setStatus(status); body.setReason("Synthetic reason");
        body.setRequestedBy(requestedBy); body.setRequestedTime("2026-10-29T02:00:00Z"); body.setDecidedBy(decidedBy);
        body.setDecisionComment("REJECTED".equals(status) ? "Not yet" : null); body.setVersion(1);
        return body;
    }

    /** Risk 变更历史。 */
    private static ClientTierChangeListResponseBody history(ClientTierChangeResponseBody... items) {
        ClientTierChangeListResponseBody body = new ClientTierChangeListResponseBody();
        body.setItems(List.of(items));
        return body;
    }

    /** Risk 额度：验证档 USD 一组。 */
    private static TierQuotaListResponseBody quotas() {
        TierQuotaCurrencyBody usd = new TierQuotaCurrencyBody();
        usd.setCurrency("USD"); usd.setSingle("50.00"); usd.setDay("250.00"); usd.setLast30Days("1000.00");
        TierQuotaTierBody verification = new TierQuotaTierBody();
        verification.setTier(LimitTier.VERIFICATION.getCode()); verification.setCurrencies(List.of(usd));
        verification.setUnconfiguredCurrencyPolicy("DENY");
        TierQuotaListResponseBody body = new TierQuotaListResponseBody();
        body.setTiers(List.of(verification)); body.setConfigurationIssues(List.of());
        return body;
    }

    /** REQ-03 当前承保：有效 LOW 承保版本。 */
    private static ClientUnderwritingCurrentResponseBody current() {
        UnderwritingSnapshotBody input = new UnderwritingSnapshotBody();
        input.setClientDisplayName("Synthetic Tier Co"); input.setClientNo("CL8201"); input.setClientType("ORGANIZATION");
        input.setJurisdictionCode("SA"); input.setCrr("LOW"); input.setNetworkHighRiskMcc(Boolean.FALSE); input.setCrrMissing(Boolean.FALSE);
        UnderwritingDecisionBody decision = new UnderwritingDecisionBody();
        decision.setObservationDays(30); decision.setObservationTransactions(100); decision.setRequireDeposit(Boolean.FALSE);
        decision.setDepositBlocksCollection(Boolean.FALSE); decision.setSuggestedSettlementDays(5); decision.setHighRiskMcc(Boolean.FALSE);
        UnderwritingResponseBody effective = new UnderwritingResponseBody();
        effective.setUnderwritingId("990001"); effective.setClientId(CLIENT); effective.setVersionNo(1); effective.setStatus("APPROVED");
        effective.setDecisionSource("MANUAL");
        effective.setReviewLevel("LOW"); effective.setInput(input); effective.setDecision(decision); effective.setCreatedBy(RISK_ACCOUNT_ID);
        effective.setCreatedAt("2026-10-29T01:00:00Z"); effective.setUpdatedAt("2026-10-29T01:00:00Z"); effective.setVersion(3);
        ClientUnderwritingCurrentResponseBody body = new ClientUnderwritingCurrentResponseBody();
        body.setEffective(effective);
        body.setLatest(effective);
        return body;
    }

    /** 清单系统项某字段的列表。 */
    private static List<String> codes(JsonNode workspace, String field) {
        return workspace.path("checklist").path("items").valueStream().map(item -> item.path(field).asString()).toList();
    }

    /** Risk 收到的某次调用（最后一次）。 */
    private RiskDecisionConfigurationSimulator.Request riskCall(String method, String path) {
        return risk.requests().stream().filter(call -> call.method().equals(method) && call.path().equals(path))
            .reduce((first, second) -> second).orElseThrow(() -> new AssertionError("Risk 未收到 " + method + " " + path));
    }

    /** compliance 收到的某路径调用（最后一次）。 */
    private ClientReviewOwnerSimulator.Request ownerCall(String path) {
        return owners.requests().stream().filter(call -> call.path().equals(path)).reduce((first, second) -> second)
            .orElseThrow(() -> new AssertionError("compliance 未收到 " + path));
    }

    /** 带 Gateway 可信头的浏览器请求。 */
    private HttpResponse<String> send(String method, String path, String route, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", "risk-limit-tier-browser"),
            Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"),
            Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID),
            Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", "operations-risk-limit-tier-" + route),
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
