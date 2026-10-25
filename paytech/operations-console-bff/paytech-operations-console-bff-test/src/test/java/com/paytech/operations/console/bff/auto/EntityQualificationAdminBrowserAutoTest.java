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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.client.master.api.enums.AcquiringQualification;
import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.api.enums.EntityAdminErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.compliance.api.enums.QualificationErrorCode;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.domain.enums.QualificationSubjectTerminalStatus;
import com.paytech.operations.console.bff.domain.enums.ReadAvailability;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.ClientReviewOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * B1—B11 主档维护与资格登记：真实 BFF 入站、PLT-I02 授权与 client-master / compliance 出站 HTTP
 * <li>详情聚合主档与资格，资格部分失败只标记不可用；列表补主体名称，失败只留 ID。</li>
 * <li>登记前核对主体存在且未终结；Owner 的 404 / 409 / 422 原样到达浏览器；不转发 Idempotency-Key。</li>
 */
class EntityQualificationAdminBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String ENTITY = "/entity-admin/v1";
    private static final String QUALIFICATION = "/qualification-admin/v1";
    private static final String MASTER = "/client-master-admin/v1";
    private static final String COMPLIANCE = "/compliance-admin/v1";
    private static final String ROUTE = "operations-master-data-test";
    private static final String ENTITY_NO = "AE001";
    private static final String PARTNER_ID = "9001";
    private static final String CLIENT_ID = "8101";
    private static final String OE_QUERY = "roleType=OPERATING_ENTITY&roleId=AE001&page=1&size=50";
    private static final String PARTNER_QUERY = "roleType=LOCAL_PARTNER&roleId=9001&page=1&size=50";
    private static final String PROVIDER = "{\"localPartnerId\":\"9001\",\"partnerNo\":\"LP9001\",\"partyId\":\"880000000002\","
        + "\"legalName\":\"Our PayFac\",\"status\":\"ACTIVE\",\"roles\":[\"OE_PROVIDER\"]}";
    private static final String PARTNER_ENTITY = "{\"entityNo\":\"AE001\",\"partyId\":\"880000000001\",\"legalName\":\"PayFac AE LLC\","
        + "\"operatingCountry\":\"AE\",\"status\":\"ACTIVE\",\"acquiringQualification\":\"PAYFAC\",\"changeVersion\":3,"
        + "\"sourceRelation\":{\"sourceRelationId\":\"5001\",\"sourceType\":\"PARTNER\",\"sourceClientId\":null,\"sourcePartnerId\":\"9001\","
        + "\"effectiveFrom\":\"2026-10-24T00:00:00.000000Z\",\"effectiveTo\":null},\"provider\":" + PROVIDER + ","
        + "\"scopes\":[{\"scopeId\":\"5101\",\"consumerCountryCode\":\"AE\",\"currency\":\"USD\",\"industryCode\":\"RETAIL\","
        + "\"mccCode\":\"5411\",\"status\":\"ACTIVE\",\"effectiveFrom\":\"2026-10-24T00:00:00.000000Z\",\"effectiveTo\":null}],"
        + "\"sellerDisclosures\":[{\"disclosureId\":\"5201\",\"countryCode\":\"AE\",\"locale\":\"en\",\"status\":\"ACTIVE\","
        + "\"effectiveFrom\":\"2026-10-24T00:00:00.000000Z\",\"effectiveTo\":null}]}";
    private static final String CLIENT_ENTITY = PARTNER_ENTITY.replace("\"sourceType\":\"PARTNER\",\"sourceClientId\":null,\"sourcePartnerId\":\"9001\"",
        "\"sourceType\":\"CLIENT\",\"sourceClientId\":\"8101\",\"sourcePartnerId\":null").replace("\"provider\":" + PROVIDER, "\"provider\":null");
    private static final String PARTNER = "{\"localPartnerId\":\"9001\",\"partnerNo\":\"LP9001\",\"partyId\":\"880000000002\","
        + "\"legalName\":\"Our PayFac\",\"status\":\"ACTIVE\",\"roles\":[\"OE_PROVIDER\"],\"changeVersion\":2,"
        + "\"providedEntities\":[{\"entityNo\":\"AE001\",\"legalName\":\"PayFac AE LLC\",\"entityStatus\":\"ACTIVE\","
        + "\"sourceRelationId\":\"5001\",\"sourceStatus\":\"ACTIVE\",\"effectiveFrom\":\"2026-10-24T00:00:00.000000Z\",\"effectiveTo\":null}]}";
    private static final String CLIENT = "{\"clientId\":\"8101\",\"clientNo\":\"CL8101\",\"partyId\":\"880000000003\","
        + "\"clientType\":\"ORGANIZATION\",\"status\":\"ACTIVE\",\"legalName\":null}";

    @Autowired
    private PlatformOperationsSimulator platform;
    @Autowired
    private ClientReviewOwnerSimulator owners;
    @LocalServerPort
    private int port;

    @BeforeEach
    void prepareMasterData() {
        platform.reset();
        platform.useClientReviewIdentity();
        owners.reset();
    }

    @AfterEach
    void cleanMasterData() {
        try {
            assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
            assertTrue(owners.unexpectedRequests().isEmpty(), owners.unexpectedRequests().toString());
        } finally {
            platform.reset();
            owners.reset();
        }
    }

    /** B1 / B4 列表：Query 原样到达 client-master；未知、重复或越界 Query 不调用 Owner。 */
    @Test
    void test_listsForwardExactQuery() throws Exception {
        owners.reply("GET", MASTER + "/operating-entities", 200, "{\"items\":[{\"entityNo\":\"AE001\",\"legalName\":\"PayFac AE LLC\","
            + "\"operatingCountry\":\"AE\",\"status\":\"ACTIVE\",\"sourceType\":\"PARTNER\",\"acquiringQualification\":\"PAYFAC\","
            + "\"provider\":{\"localPartnerId\":\"9001\",\"legalName\":\"Our PayFac\"},\"changeVersion\":3}],\"page\":1,\"size\":20,\"total\":1}");
        owners.reply("GET", MASTER + "/local-partners", 200, "{\"items\":[{\"localPartnerId\":\"9001\",\"partnerNo\":\"LP9001\","
            + "\"legalName\":\"Our PayFac\",\"status\":\"ACTIVE\",\"roles\":[\"OE_PROVIDER\"],\"providedEntityCount\":1,"
            + "\"changeVersion\":2}],\"page\":1,\"size\":10,\"total\":1}");

        var entities = send("GET", ENTITY + "/operating-entities?status=ACTIVE&sourceType=PARTNER&country=AE&page=1&size=20", null);
        var partners = send("GET", ENTITY + "/local-partners?status=ACTIVE&role=" + PartnerRoleCode.OE_PROVIDER.getCode(), null);

        assertOk(entities);
        assertOk(partners);
        assertEquals("9001", JSON.readTree(entities.body()).path("items").get(0).path("provider").path("localPartnerId").asString());
        assertEquals(1, JSON.readTree(partners.body()).path("items").get(0).path("providedEntityCount").asInt());
        assertEquals("status=ACTIVE&sourceType=PARTNER&country=AE&page=1&size=20", owners.requests().get(0).query());
        assertEquals("status=ACTIVE&role=OE_PROVIDER", owners.requests().get(1).query());
        for (String bad : List.of("?sourceType=PLATFORM", "?region=AE", "?page=1&page=2", "?status=", "?size=101", "?country=ae")) {
            assertEquals(400, send("GET", ENTITY + "/operating-entities" + bad, null).statusCode(), bad);
        }
        assertEquals(400, send("GET", ENTITY + "/local-partners?role=PROVIDER", null).statusCode());
        assertEquals(2, owners.requests().size());
        assertTrustedCalls();
    }

    /** B2 PARTNER 来源：主档 + OE 资格 + 提供方资格，三次 Owner 调用都携带可信身份。 */
    @Test
    void test_operatingEntityDetailAggregatesQualifications() throws Exception {
        owners.reply("GET", MASTER + "/operating-entities/" + ENTITY_NO, 200, PARTNER_ENTITY);
        owners.replyQuery("GET", COMPLIANCE + "/qualifications", OE_QUERY, 200, page(qualification("6001", "OPERATING_ENTITY", ENTITY_NO)));
        owners.replyQuery("GET", COMPLIANCE + "/qualifications", PARTNER_QUERY, 200, page(qualification("6002", "LOCAL_PARTNER", PARTNER_ID)));

        var response = send("GET", ENTITY + "/operating-entities/" + ENTITY_NO, null);

        assertOk(response);
        JsonNode body = JSON.readTree(response.body());
        assertEquals(ENTITY_NO, body.path("entity").path("entityNo").asString());
        assertEquals(AcquiringQualification.PAYFAC.getCode(), body.path("entity").path("acquiringQualification").asString());
        assertEquals("5411", body.path("entity").path("scopes").get(0).path("mccCode").asString());
        assertEquals(ReadAvailability.AVAILABLE.getCode(), body.path("qualificationsStatus").asString());
        assertEquals("6001", body.path("qualifications").get(0).path("qualificationId").asString());
        assertEquals(ReadAvailability.AVAILABLE.getCode(), body.path("providerQualificationsStatus").asString());
        assertEquals("6002", body.path("providerQualifications").get(0).path("qualificationId").asString());
        assertEquals(3, owners.requests().size());
        assertTrustedCalls();
    }

    /** B2 资格部分失败：只把该部分标为 UNAVAILABLE；CLIENT 来源不读提供方资格，标为 NOT_APPLICABLE。 */
    @Test
    void test_operatingEntityDetailDegradesQualificationParts() throws Exception {
        owners.reply("GET", MASTER + "/operating-entities/" + ENTITY_NO, 200, PARTNER_ENTITY);
        owners.problemQuery("GET", COMPLIANCE + "/qualifications", OE_QUERY, 503, "SERVICE_UNAVAILABLE");
        owners.replyQuery("GET", COMPLIANCE + "/qualifications", PARTNER_QUERY, 200, page(qualification("6002", "LOCAL_PARTNER", PARTNER_ID)));

        JsonNode degraded = JSON.readTree(send("GET", ENTITY + "/operating-entities/" + ENTITY_NO, null).body());

        assertEquals(ReadAvailability.UNAVAILABLE.getCode(), degraded.path("qualificationsStatus").asString());
        assertTrue(degraded.path("qualifications").isEmpty());
        assertEquals(ReadAvailability.AVAILABLE.getCode(), degraded.path("providerQualificationsStatus").asString());

        owners.reset();
        owners.reply("GET", MASTER + "/operating-entities/" + ENTITY_NO, 200, CLIENT_ENTITY);
        owners.replyQuery("GET", COMPLIANCE + "/qualifications", OE_QUERY, 200, page(qualification("6001", "OPERATING_ENTITY", ENTITY_NO)));

        var clientSource = send("GET", ENTITY + "/operating-entities/" + ENTITY_NO, null);

        assertOk(clientSource);
        JsonNode body = JSON.readTree(clientSource.body());
        assertEquals(ReadAvailability.AVAILABLE.getCode(), body.path("qualificationsStatus").asString());
        assertEquals(ReadAvailability.NOT_APPLICABLE.getCode(), body.path("providerQualificationsStatus").asString());
        assertTrue(body.path("providerQualifications").isEmpty());
        assertEquals(2, owners.requests().size());
    }

    /** B2 主档不存在：整页 404 保留 client-master 错误码，不读取资格。 */
    @Test
    void test_operatingEntityNotFound() throws Exception {
        owners.problem("GET", MASTER + "/operating-entities/" + ENTITY_NO, 404, EntityAdminErrorCode.OPERATING_ENTITY_NOT_FOUND.getCode());

        assertProblem(send("GET", ENTITY + "/operating-entities/" + ENTITY_NO, null), 404, EntityAdminErrorCode.OPERATING_ENTITY_NOT_FOUND);
        assertEquals(400, send("GET", ENTITY + "/operating-entities/ae001", null).statusCode());
        assertEquals(1, owners.requests().size());
    }

    /** B5 Partner 详情：资格可用时原样返回，资格不可用时只标记 UNAVAILABLE。 */
    @Test
    void test_localPartnerDetailAggregatesQualifications() throws Exception {
        owners.reply("GET", MASTER + "/local-partners/" + PARTNER_ID, 200, PARTNER);
        owners.replyQuery("GET", COMPLIANCE + "/qualifications", PARTNER_QUERY, 200, page(qualification("6002", "LOCAL_PARTNER", PARTNER_ID)));

        JsonNode body = JSON.readTree(send("GET", ENTITY + "/local-partners/" + PARTNER_ID, null).body());

        assertEquals(PARTNER_ID, body.path("partner").path("localPartnerId").asString());
        assertEquals(ENTITY_NO, body.path("partner").path("providedEntities").get(0).path("entityNo").asString());
        assertEquals(ReadAvailability.AVAILABLE.getCode(), body.path("qualificationsStatus").asString());
        assertEquals("PARTNER_DUE_DILIGENCE", body.path("qualifications").get(0).path("qualificationType").asString());

        owners.problemQuery("GET", COMPLIANCE + "/qualifications", PARTNER_QUERY, 503, "SERVICE_UNAVAILABLE");

        JsonNode degraded = JSON.readTree(send("GET", ENTITY + "/local-partners/" + PARTNER_ID, null).body());

        assertEquals(ReadAvailability.UNAVAILABLE.getCode(), degraded.path("qualificationsStatus").asString());
        assertTrue(degraded.path("qualifications").isEmpty());
        assertTrustedCalls();
    }

    /** B3 / B6 写入：Body 按 client-master 合同转发；Owner 的 409 / 422 保留错误码；非法 Body 不调用 Owner。 */
    @Test
    void test_entityWritesForwardOwnerContracts() throws Exception {
        owners.reply("PUT", MASTER + "/operating-entities/" + ENTITY_NO + "/acquiring-qualification", 200, PARTNER_ENTITY);
        owners.reply("PUT", MASTER + "/local-partners/" + PARTNER_ID + "/roles", 200, PARTNER);

        assertOk(send("PUT", ENTITY + "/operating-entities/" + ENTITY_NO + "/acquiring-qualification",
            "{\"acquiringQualification\":\"PAYFAC\",\"expectedChangeVersion\":3,\"reason\":\"Acquirer approved PayFac programme\"}"));
        assertOk(send("PUT", ENTITY + "/local-partners/" + PARTNER_ID + "/roles",
            "{\"roles\":[\"OE_PROVIDER\",\"REFERRER\"],\"expectedChangeVersion\":2,\"reason\":\"Add referral\"}"));

        var calls = owners.requests();
        assertEquals(AcquiringQualification.PAYFAC.getCode(), JSON.readTree(calls.get(0).body()).path("acquiringQualification").asString());
        assertEquals(3, JSON.readTree(calls.get(0).body()).path("expectedChangeVersion").asLong());
        assertEquals(PartnerRoleCode.REFERRER.getCode(), JSON.readTree(calls.get(1).body()).path("roles").get(1).asString());
        assertTrustedCalls();

        owners.problem("PUT", MASTER + "/operating-entities/" + ENTITY_NO + "/acquiring-qualification", 409,
            EntityAdminErrorCode.ENTITY_CHANGE_VERSION_CONFLICT.getCode());
        owners.problem("PUT", MASTER + "/local-partners/" + PARTNER_ID + "/roles", 422, EntityAdminErrorCode.ENTITY_ATTRIBUTE_INVALID.getCode());

        assertProblem(send("PUT", ENTITY + "/operating-entities/" + ENTITY_NO + "/acquiring-qualification",
            "{\"acquiringQualification\":\"MERCHANT\",\"expectedChangeVersion\":2,\"reason\":\"Downgrade\"}"),
            409, EntityAdminErrorCode.ENTITY_CHANGE_VERSION_CONFLICT);
        assertProblem(send("PUT", ENTITY + "/local-partners/" + PARTNER_ID + "/roles",
            "{\"roles\":[\"REFERRER\"],\"expectedChangeVersion\":2,\"reason\":\"Drop provider\"}"), 422, EntityAdminErrorCode.ENTITY_ATTRIBUTE_INVALID);

        int before = owners.requests().size();
        for (String bad : List.of("{}", "{\"acquiringQualification\":\"PAYFAC\",\"expectedChangeVersion\":3,\"reason\":\"x\",\"affiliation\":1}",
            "{\"acquiringQualification\":\"PAYFAC\",\"expectedChangeVersion\":\"3\",\"reason\":\"x\"}", "[]")) {
            var response = send("PUT", ENTITY + "/operating-entities/" + ENTITY_NO + "/acquiring-qualification", bad);
            assertTrue(response.statusCode() == 400 || response.statusCode() == 422, bad + " " + response.statusCode());
        }
        assertEquals(before, owners.requests().size());
    }

    /** B7 主体核对：三种角色分别读取 client-master 对应接口；名称缺失回退编号；不存在保留 404 错误码。 */
    @Test
    void test_subjectLookupPerRole() throws Exception {
        owners.reply("GET", MASTER + "/operating-entities/" + ENTITY_NO, 200, PARTNER_ENTITY);
        owners.reply("GET", MASTER + "/local-partners/" + PARTNER_ID, 200, PARTNER);
        owners.reply("GET", MASTER + "/clients/" + CLIENT_ID + "/summary", 200, CLIENT);

        JsonNode entity = subject(QualificationRoleType.OPERATING_ENTITY, ENTITY_NO);
        JsonNode partner = subject(QualificationRoleType.LOCAL_PARTNER, PARTNER_ID);
        JsonNode client = subject(QualificationRoleType.CLIENT, CLIENT_ID);

        assertEquals(Map.of("roleType", "OPERATING_ENTITY", "roleId", ENTITY_NO, "displayName", "PayFac AE LLC", "status", "ACTIVE"),
            JSON.convertValue(entity, Map.class));
        assertEquals(PARTNER_ID, partner.path("roleId").asString());
        assertEquals("Our PayFac", partner.path("displayName").asString());
        assertEquals(QualificationRoleType.CLIENT.getCode(), client.path("roleType").asString());
        assertEquals("CL8101", client.path("displayName").asString(), "法定名称缺失时以 Client 编号展示");
        assertEquals(List.of(MASTER + "/operating-entities/" + ENTITY_NO, MASTER + "/local-partners/" + PARTNER_ID,
            MASTER + "/clients/" + CLIENT_ID + "/summary"), owners.requests().stream().map(ClientReviewOwnerSimulator.Request::path).toList());

        owners.problem("GET", MASTER + "/clients/8102/summary", 404, CheckoutCandidateErrorCode.CLIENT_NOT_FOUND.getCode());

        assertProblem(send("GET", QUALIFICATION + "/subjects?roleType=CLIENT&subjectRef=8102", null), 404,
            CheckoutCandidateErrorCode.CLIENT_NOT_FOUND);
        assertEquals(400, send("GET", QUALIFICATION + "/subjects?roleType=MERCHANT&subjectRef=8102", null).statusCode());
        assertEquals(400, send("GET", QUALIFICATION + "/subjects?roleType=CLIENT", null).statusCode());
        assertEquals(4, owners.requests().size());
        assertTrustedCalls();
    }

    /** B8 列表：页内主体去重后补名称；某个主体读取失败只让该行名称为空。 */
    @Test
    void test_qualificationListEnrichesSubjectNames() throws Exception {
        owners.reply("GET", COMPLIANCE + "/qualifications", 200, "{\"items\":[" + qualification("6001", "OPERATING_ENTITY", ENTITY_NO) + ","
            + qualification("6002", "LOCAL_PARTNER", PARTNER_ID) + "," + qualification("6003", "CLIENT", CLIENT_ID).replace("ENTITY_UNDERWRITING", "ORGANIZATION_KYB")
            + "," + qualification("6004", "CLIENT", CLIENT_ID).replace("ENTITY_UNDERWRITING", "INDIVIDUAL_KYC") + "],\"page\":1,\"size\":20,\"total\":4}");
        owners.reply("GET", MASTER + "/operating-entities/" + ENTITY_NO, 200, PARTNER_ENTITY);
        owners.problem("GET", MASTER + "/local-partners/" + PARTNER_ID, 503, "SERVICE_UNAVAILABLE");
        owners.reply("GET", MASTER + "/clients/" + CLIENT_ID + "/summary", 200, CLIENT.replace("\"legalName\":null", "\"legalName\":\"Synthetic Co\""));

        var response = send("GET", QUALIFICATION + "/qualifications?qualificationType=ORGANIZATION_KYB&status=APPROVED&page=1&size=20", null);

        assertOk(response);
        JsonNode body = JSON.readTree(response.body());
        assertEquals(4, body.path("total").asLong());
        assertEquals("6001", body.path("items").get(0).path("qualification").path("qualificationId").asString());
        assertEquals("PayFac AE LLC", body.path("items").get(0).path("subjectName").asString());
        assertTrue(body.path("items").get(1).path("subjectName").isNull());
        assertEquals(PARTNER_ID, body.path("items").get(1).path("qualification").path("roleId").asString());
        assertEquals("Synthetic Co", body.path("items").get(2).path("subjectName").asString());
        assertEquals("Synthetic Co", body.path("items").get(3).path("subjectName").asString());
        assertEquals("qualificationType=ORGANIZATION_KYB&status=APPROVED&page=1&size=20", owners.requests().getFirst().query());
        assertEquals(1, owners.requests().stream().filter(call -> call.path().endsWith("/clients/" + CLIENT_ID + "/summary")).count(),
            "同一主体只读取一次");
        assertEquals(4, owners.requests().size());
        assertTrustedCalls();
    }

    /** B10 登记：先核对主体，再单次写入 compliance，Body 原样转发。 */
    @Test
    void test_manualDecisionChecksSubjectBeforeWriting() throws Exception {
        owners.reply("GET", MASTER + "/operating-entities/" + ENTITY_NO, 200, PARTNER_ENTITY);
        owners.reply("POST", COMPLIANCE + "/qualifications/manual-decisions", 200, qualification("6001", "OPERATING_ENTITY", ENTITY_NO));

        var response = send("POST", QUALIFICATION + "/qualifications/manual-decisions", manualDecision("OPERATING_ENTITY", ENTITY_NO));

        assertOk(response);
        assertEquals("6001", JSON.readTree(response.body()).path("qualificationId").asString());
        var calls = owners.requests();
        assertEquals(2, calls.size());
        assertEquals(MASTER + "/operating-entities/" + ENTITY_NO, calls.get(0).path());
        assertEquals(COMPLIANCE + "/qualifications/manual-decisions", calls.get(1).path());
        JsonNode forwarded = JSON.readTree(calls.get(1).body());
        assertEquals("UNDERWRITING_DONE", forwarded.path("reasonCode").asString());
        assertEquals("Acquirer letter 2026-10-24", forwarded.path("basisNote").asString());
        assertTrue(forwarded.path("validTo").isNull());
        assertTrustedCalls();
    }

    /** B10 主体不存在：保留 client-master 404，不调用 compliance。 */
    @Test
    void test_manualDecisionRejectsMissingSubject() throws Exception {
        owners.problem("GET", MASTER + "/local-partners/" + PARTNER_ID, 404, EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND.getCode());

        var response = send("POST", QUALIFICATION + "/qualifications/manual-decisions", manualDecision("LOCAL_PARTNER", PARTNER_ID)
            .replace("ENTITY_UNDERWRITING", "PARTNER_DUE_DILIGENCE"));

        assertProblem(response, 404, EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND);
        assertEquals(1, owners.requests().size());
    }

    /** B10 主体已终结：OE CLOSED、Client TERMINATED 返回 422 QUALIFICATION_SUBJECT_TERMINATED，不调用 compliance。 */
    @Test
    void test_manualDecisionRejectsTerminatedSubject() throws Exception {
        owners.reply("GET", MASTER + "/operating-entities/" + ENTITY_NO, 200,
            PARTNER_ENTITY.replace("\"status\":\"ACTIVE\",\"acquiringQualification\"",
                "\"status\":\"" + QualificationSubjectTerminalStatus.CLOSED.getCode() + "\",\"acquiringQualification\""));
        owners.reply("GET", MASTER + "/clients/" + CLIENT_ID + "/summary", 200,
            CLIENT.replace("\"status\":\"ACTIVE\"", "\"status\":\"" + QualificationSubjectTerminalStatus.TERMINATED.getCode() + "\""));

        assertProblem(send("POST", QUALIFICATION + "/qualifications/manual-decisions", manualDecision("OPERATING_ENTITY", ENTITY_NO)),
            422, OperationsBffErrorCode.QUALIFICATION_SUBJECT_TERMINATED);
        assertProblem(send("POST", QUALIFICATION + "/qualifications/manual-decisions", manualDecision("CLIENT", CLIENT_ID)
            .replace("ENTITY_UNDERWRITING", "ORGANIZATION_KYB")), 422, OperationsBffErrorCode.QUALIFICATION_SUBJECT_TERMINATED);
        assertEquals(2, owners.requests().size());
        assertTrue(owners.requests().stream().noneMatch(call -> call.path().startsWith(COMPLIANCE)));
    }

    /** B9 / B11 转发与 compliance 错误原样到达：404、409、422。 */
    @Test
    void test_historyRevocationAndComplianceRejections() throws Exception {
        owners.reply("GET", COMPLIANCE + "/qualifications/6001/history", 200, "{\"qualificationId\":\"6001\",\"items\":[{\"changeId\":\"6201\","
            + "\"aggregateVersion\":\"1\",\"fromStatus\":null,\"toStatus\":\"APPROVED\",\"triggerType\":\"OPERATIONS_MANUAL\",\"reasonCode\":\"UNDERWRITING_DONE\","
            + "\"actorId\":\"" + RISK_ACCOUNT_ID + "\",\"occurredAt\":\"2026-10-24T00:00:00.000000Z\",\"decisionId\":\"6101\",\"decisionSource\":\"MANUAL\","
            + "\"outcome\":\"APPROVED\",\"decidedBy\":\"" + RISK_ACCOUNT_ID + "\",\"basisNote\":\"Acquirer letter\",\"validFrom\":\"2026-10-24T00:00:00.000000Z\","
            + "\"validTo\":null}]}");
        owners.reply("POST", COMPLIANCE + "/qualifications/6001/revocation", 200,
            qualification("6001", "OPERATING_ENTITY", ENTITY_NO).replace("\"status\":\"APPROVED\"", "\"status\":\"REVOKED\""));

        var history = send("GET", QUALIFICATION + "/qualifications/6001/history", null);
        var revoked = send("POST", QUALIFICATION + "/qualifications/6001/revocation", revocation());

        assertOk(history);
        assertEquals("OPERATIONS_MANUAL", JSON.readTree(history.body()).path("items").get(0).path("triggerType").asString());
        assertOk(revoked);
        assertEquals("REVOKED", JSON.readTree(revoked.body()).path("status").asString());
        assertEquals("2", JSON.readTree(owners.requests().get(1).body()).path("expectedVersion").asString());
        assertTrustedCalls();

        owners.problem("GET", COMPLIANCE + "/qualifications/6009/history", 404, QualificationErrorCode.QUALIFICATION_NOT_FOUND.getCode());
        owners.problem("POST", COMPLIANCE + "/qualifications/6001/revocation", 409, QualificationErrorCode.QUALIFICATION_NOT_ACTIVE.getCode());
        owners.reply("GET", MASTER + "/operating-entities/" + ENTITY_NO, 200, PARTNER_ENTITY);
        owners.problem("POST", COMPLIANCE + "/qualifications/manual-decisions", 409, QualificationErrorCode.QUALIFICATION_CHANGED.getCode());

        assertProblem(send("GET", QUALIFICATION + "/qualifications/6009/history", null), 404, QualificationErrorCode.QUALIFICATION_NOT_FOUND);
        assertProblem(send("POST", QUALIFICATION + "/qualifications/6001/revocation", revocation()), 409,
            QualificationErrorCode.QUALIFICATION_NOT_ACTIVE);
        assertProblem(send("POST", QUALIFICATION + "/qualifications/manual-decisions", manualDecision("OPERATING_ENTITY", ENTITY_NO)), 409,
            QualificationErrorCode.QUALIFICATION_CHANGED);

        owners.problem("POST", COMPLIANCE + "/qualifications/manual-decisions", 422, QualificationErrorCode.QUALIFICATION_INVALID.getCode());

        assertProblem(send("POST", QUALIFICATION + "/qualifications/manual-decisions", manualDecision("OPERATING_ENTITY", ENTITY_NO)), 422,
            QualificationErrorCode.QUALIFICATION_INVALID);
    }

    /** Owner 返回其他主体或资格的结果：上游响应无效 502，不暴露原文。 */
    @Test
    void test_mismatchedOwnerResponseIsRejected() throws Exception {
        owners.reply("GET", MASTER + "/operating-entities/" + ENTITY_NO, 200, PARTNER_ENTITY.replace("\"entityNo\":\"AE001\"", "\"entityNo\":\"AE002\""));
        owners.reply("POST", COMPLIANCE + "/qualifications/6001/revocation", 200, qualification("6002", "OPERATING_ENTITY", ENTITY_NO));

        var entity = send("GET", ENTITY + "/operating-entities/" + ENTITY_NO, null);
        var revoked = send("POST", QUALIFICATION + "/qualifications/6001/revocation", revocation());

        assertEquals(502, entity.statusCode(), entity.body());
        assertFalse(entity.body().contains("AE002"));
        assertEquals(502, revoked.statusCode(), revoked.body());
        assertEquals(2, owners.requests().size());
    }

    /** 未授权：PLT-I02 拒绝后不调用 Owner。 */
    @Test
    void test_deniedDoesNotCallOwner() throws Exception {
        platform.allow(false);

        assertEquals(403, send("POST", QUALIFICATION + "/qualifications/manual-decisions", manualDecision("OPERATING_ENTITY", ENTITY_NO)).statusCode());
        assertEquals(403, send("GET", ENTITY + "/operating-entities/" + ENTITY_NO, null).statusCode());
        assertTrue(owners.requests().isEmpty());
    }

    /** B7 主体核对结果。 */
    private JsonNode subject(QualificationRoleType roleType, String subjectRef) throws Exception {
        var response = send("GET", QUALIFICATION + "/subjects?roleType=" + roleType.getCode() + "&subjectRef=" + subjectRef, null);
        assertOk(response);
        return JSON.readTree(response.body());
    }

    /** 一条 compliance 资格，默认 ENTITY_UNDERWRITING / APPROVED。 */
    private static String qualification(String qualificationId, String roleType, String roleId) {
        String type = switch (roleType) {
            case "LOCAL_PARTNER" -> "PARTNER_DUE_DILIGENCE";
            default -> "ENTITY_UNDERWRITING";
        };
        return "{\"qualificationId\":\"" + qualificationId + "\",\"roleType\":\"" + roleType + "\",\"roleId\":\"" + roleId + "\","
            + "\"qualificationType\":\"" + type + "\",\"status\":\"APPROVED\",\"effectiveStatus\":\"ELIGIBLE\","
            + "\"validFrom\":\"2026-10-24T00:00:00.000000Z\",\"validTo\":null,\"currentDecisionId\":\"6101\",\"decisionSource\":\"MANUAL\","
            + "\"reviewStatus\":\"NOT_REQUIRED\",\"version\":\"2\",\"updatedAt\":\"2026-10-24T00:00:00.000000Z\"}";
    }

    /** 单条资格的分页。 */
    private static String page(String item) {
        return "{\"items\":[" + item + "],\"page\":1,\"size\":50,\"total\":1}";
    }

    /** 登记请求，默认 ENTITY_UNDERWRITING、无结束时间、首次登记。 */
    private static String manualDecision(String roleType, String roleId) {
        return "{\"roleType\":\"" + roleType + "\",\"roleId\":\"" + roleId + "\",\"qualificationType\":\"ENTITY_UNDERWRITING\","
            + "\"validFrom\":\"2026-10-24T00:00:00.000000Z\",\"validTo\":null,\"reasonCode\":\"UNDERWRITING_DONE\","
            + "\"basisNote\":\"Acquirer letter 2026-10-24\",\"expectedVersion\":null}";
    }

    /** 撤销请求。 */
    private static String revocation() {
        return "{\"expectedVersion\":\"2\",\"reasonCode\":\"ACQUIRER_WITHDRAWN\",\"basisNote\":\"Programme ended\"}";
    }

    private void assertTrustedCalls() {
        for (var call : owners.requests()) {
            assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"), call.path());
            assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"), call.path());
            assertEquals("master-data-browser", call.header("X-Request-Id"), call.path());
            assertNull(call.header("Idempotency-Key"), call.path());
        }
    }

    private static void assertOk(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
    }

    private static void assertProblem(HttpResponse<String> response, int status, CodeMessageEnum code) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(response.body().contains("\"code\":\"" + code.getCode() + "\""), response.body());
    }

    /** 带 Gateway 可信头访问 BFF。 */
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", "master-data-browser"),
            Map.entry("Idempotency-Key", "browser-key-0000000001"),
            Map.entry("X-Gateway-Realm", "OPERATIONS"), Map.entry("X-Gateway-Channel", "OPERATIONS"),
            Map.entry("X-Gateway-Actor-Type", "PLATFORM_USER"), Map.entry("X-Gateway-Actor-Subject", RISK_ACCOUNT_ID),
            Map.entry("X-Gateway-Session-Id", SESSION_ID), Map.entry("X-Gateway-Route-Code", ROUTE),
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
