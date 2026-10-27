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

import com.paytech.client.master.api.enums.CheckoutCandidateErrorCode;
import com.paytech.client.master.api.enums.OnboardingErrorCode;
import com.paytech.client.master.api.enums.PartnerRoleCode;
import com.paytech.compliance.api.enums.QualificationRoleType;
import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.operations.console.bff.domain.enums.ReadAvailability;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.ClientReviewOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * CM-10/11/12 入驻配置、Client 与入驻码：真实 BFF 入站、PLT-I02 授权与 client-master / compliance 出站 HTTP
 * <li>配置与入驻码为单 Owner 转发；Client 详情聚合 client-master 主档与 compliance 资格，资格失败只标记不可用。</li>
 * <li>Owner 返回其他对象或入驻码列表出现明文码视为上游响应无效；不转发 Idempotency-Key。</li>
 */
class OnboardingAdminBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final String MASTER = "/client-master-admin/v1";
    private static final String COMPLIANCE = "/compliance-admin/v1";
    private static final String CLIENT_ID = "8101";
    private static final String PARTNER_ID = "9001";
    private static final String CODES = MASTER + "/local-partners/" + PARTNER_ID + "/onboarding-codes";
    private static final String CODE = "{\"codeId\":\"7101\",\"code\":null,\"codeMasked\":\"****-7Q2K\",\"localPartnerId\":\"9001\",\"status\":\"ACTIVE\","
        + "\"effectiveFrom\":\"2026-10-27T00:00:00.000000Z\",\"effectiveTo\":null,\"maxUses\":50,\"useCount\":0,\"note\":null,\"issuedBy\":\""
        + RISK_ACCOUNT_ID + "\",\"issuedAt\":\"2026-10-27T00:00:00.000000Z\",\"revokedBy\":null,\"revokedAt\":null}";
    private static final String CLIENT = "{\"client\":{\"clientId\":\"8101\",\"clientNo\":\"CL8101\",\"clientType\":\"ORGANIZATION\",\"status\":\"ACTIVE\","
        + "\"jurisdictionCode\":\"SA\",\"responsiblePartnerId\":\"9001\",\"legalName\":\"Synthetic Co\",\"latestApplicationId\":\"8001\","
        + "\"latestApplicationStatus\":\"UNDER_REVIEW\",\"createdAt\":\"2026-10-27T00:00:00.000000Z\"},"
        + "\"values\":{\"organization\":{\"legalName\":\"Synthetic Co\"},\"identifier\":{\"identifierType\":\"SA_CR\",\"identifierMasked\":\"******7890\"}},"
        + "\"people\":[{\"partyId\":\"880000000004\",\"values\":{\"individual\":{\"givenName\":\"Test\",\"familyName\":\"Person\"}}}],"
        + "\"relations\":[{\"relationId\":\"8301\",\"relatedPartyId\":\"880000000004\",\"relationType\":\"UBO\",\"ownershipPercentage\":\"60\","
        + "\"uboBasis\":\"OWNERSHIP\",\"jobTitle\":null,\"status\":\"ACTIVE\"}],"
        + "\"applications\":[{\"applicationId\":\"8001\",\"applicationNo\":\"APP8001\",\"status\":\"UNDER_REVIEW\",\"jurisdictionCode\":\"SA\","
        + "\"organizationTypeCode\":\"LLC\",\"schemaId\":\"6101\",\"responsiblePartnerId\":\"9001\",\"submissionRound\":1,\"dueDiligenceCaseId\":\"7001\","
        + "\"qualificationDecisionId\":null,\"submittedAt\":\"2026-10-26T01:00:00.000000Z\",\"updatedAt\":\"2026-10-26T01:00:00.000000Z\"}]}";
    private static final String CLIENT_QUALIFICATIONS = "roleType=CLIENT&roleId=8101&page=1&size=50";

    @Autowired
    private PlatformOperationsSimulator platform;
    @Autowired
    private ClientReviewOwnerSimulator owners;
    @LocalServerPort
    private int port;

    @BeforeEach
    void prepareOnboardingAdmin() {
        platform.reset();
        platform.useClientReviewIdentity();
        owners.reset();
    }

    @AfterEach
    void cleanOnboardingAdmin() {
        try {
            assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
            assertTrue(owners.unexpectedRequests().isEmpty(), owners.unexpectedRequests().toString());
        } finally {
            platform.reset();
            owners.reset();
        }
    }

    /** CM-10：辖区、Schema 原样转发；目录项由 BFF 只保留全局项与请求辖区项；越界 Query 不调用 Owner；Schema 必须属于请求的辖区。 */
    @Test
    void test_configurationForwardsReadOnlyQueries() throws Exception {
        owners.reply("GET", MASTER + "/jurisdictions", 200, "[{\"jurisdictionCode\":\"SA\",\"countryCode\":\"SA\",\"labelKey\":\"onboardingSchema.jurisdiction.SA\","
            + "\"addressFormatCode\":\"SA_NATIONAL\",\"status\":\"ACTIVE\",\"changeVersion\":1,\"rules\":[{\"scopeType\":\"JURISDICTION\",\"scopeCode\":\"SA\","
            + "\"ruleCode\":\"UBO_THRESHOLD_PCT\",\"ruleValue\":\"25\",\"status\":\"ACTIVE\"}],\"organizationTypes\":[],\"identifierTypes\":[],\"relationTypes\":[]}]");
        owners.reply("GET", MASTER + "/onboarding-schemas", 200, "[{\"schemaId\":\"6101\",\"jurisdictionCode\":\"SA\",\"partyType\":\"ORGANIZATION\","
            + "\"roleType\":\"CLIENT\",\"organizationTypeCode\":null,\"versionNo\":1,\"status\":\"ACTIVE\",\"approvedTime\":null,\"fields\":[{\"section\":\"BASIC\","
            + "\"displayGroup\":\"IDENTITY\",\"fieldKey\":\"organization.legalName\",\"widgetCode\":\"TEXT\",\"catalogCode\":null,\"requirementLevel\":\"REQUIRED\","
            + "\"requirementGroup\":null,\"appliesToSubject\":null,\"validationPattern\":null,\"minLength\":null,\"maxLength\":128,\"visibleWhen\":null,"
            + "\"labelKey\":\"onboardingSchema.field.organization.legalName\",\"helpKey\":null,\"displayOrder\":1}]}]");
        owners.reply("GET", MASTER + "/catalog-items", 200, "[{\"catalogCode\":\"INDUSTRY\",\"itemCode\":\"RETAIL\",\"jurisdictionCode\":\"*\","
            + "\"labelKey\":null,\"clientTypes\":null,\"displayOrder\":1,\"status\":\"ACTIVE\"},{\"catalogCode\":\"INDUSTRY\",\"itemCode\":\"AE_ONLY\","
            + "\"jurisdictionCode\":\"AE\",\"labelKey\":null,\"clientTypes\":null,\"displayOrder\":2,\"status\":\"ACTIVE\"}]");

        var jurisdictions = send("GET", "/onboarding-config-admin/v1/jurisdictions", "operations-onboarding-config-jurisdictions", null);
        var schemas = send("GET", "/onboarding-config-admin/v1/onboarding-schemas?jurisdiction=SA", "operations-onboarding-config-schemas", null);
        var items = send("GET", "/onboarding-config-admin/v1/catalog-items?catalogCode=INDUSTRY&jurisdiction=SA", "operations-onboarding-config-catalog-items", null);

        assertOk(jurisdictions);
        assertEquals("25", JSON.readTree(jurisdictions.body()).get(0).path("rules").get(0).path("ruleValue").asString());
        assertOk(schemas);
        assertEquals("organization.legalName", JSON.readTree(schemas.body()).get(0).path("fields").get(0).path("fieldKey").asString());
        assertOk(items);
        assertEquals(1, JSON.readTree(items.body()).size(), "AE 专属项不出现在 SA 的目录中");
        assertEquals("jurisdiction=SA", owners.requests().get(1).query());
        assertEquals("catalogCode=INDUSTRY", owners.requests().get(2).query(), "client-master 不收辖区参数，由 BFF 过滤");
        for (String bad : List.of("/onboarding-schemas", "/onboarding-schemas?jurisdiction=sa", "/catalog-items?catalogCode=industry", "/jurisdictions?x=1")) {
            assertEquals(400, send("GET", "/onboarding-config-admin/v1" + bad, "operations-onboarding-config-schemas", null).statusCode(), bad);
        }
        assertEquals(3, owners.requests().size());
        assertTrustedCalls();

        owners.reset();
        owners.reply("GET", MASTER + "/onboarding-schemas", 200, "[{\"schemaId\":\"6101\",\"jurisdictionCode\":\"AE\",\"partyType\":\"ORGANIZATION\","
            + "\"versionNo\":1,\"status\":\"ACTIVE\",\"fields\":[]}]");
        assertEquals(502, send("GET", "/onboarding-config-admin/v1/onboarding-schemas?jurisdiction=SA", "operations-onboarding-config-schemas", null).statusCode());
    }

    /** O14 程序：按辖区转发 compliance（不带状态，展示全部版本）；全局程序可见；其他辖区的程序为 502。 */
    @Test
    void test_programsForwardJurisdiction() throws Exception {
        String program = "{\"programId\":\"5001\",\"programCode\":\"SA_ORG_STD\",\"programVersionId\":\"5101\",\"versionNo\":1,"
            + "\"qualificationType\":\"ORGANIZATION_KYB\",\"jurisdictionCode\":\"SA\",\"tier\":\"STANDARD\",\"ownerPartnerId\":null,\"reviewIntervalMonths\":12,"
            + "\"fourEyesRequired\":true,\"materials\":[{\"requirementCode\":\"COMMERCIAL_REGISTRATION\",\"subjectRole\":\"PRIMARY\","
            + "\"documentTypes\":[\"CR_CERTIFICATE\"],\"required\":true,\"maxAgeDays\":90,\"waivable\":false}],\"checks\":[],\"screenings\":[],"
            + "\"partyType\":\"ORGANIZATION\",\"roleType\":\"CLIENT\",\"programStatus\":\"ACTIVE\",\"versionStatus\":\"ACTIVE\",\"approvedBy\":null,\"approvedTime\":null}";
        owners.reply("GET", "/client-onboarding-admin/v1/programs", 200, "{\"items\":[" + program.replace("\"jurisdictionCode\":\"SA\"", "\"jurisdictionCode\":null")
            .replace("5101", "5102") + "," + program + "]}");

        var response = send("GET", "/onboarding-config-admin/v1/programs?jurisdiction=SA", "operations-onboarding-config-programs", null);

        assertOk(response);
        JsonNode body = JSON.readTree(response.body());
        assertTrue(body.path("items").get(0).path("jurisdictionCode").isNull());
        assertEquals(90, body.path("items").get(1).path("materials").get(0).path("maxAgeDays").asInt());
        assertEquals("jurisdictionCode=SA", owners.requests().getFirst().query());
        assertEquals(400, send("GET", "/onboarding-config-admin/v1/programs", "operations-onboarding-config-programs", null).statusCode());
        assertEquals(1, owners.requests().size());
        assertTrustedCalls();

        owners.reset();
        owners.reply("GET", "/client-onboarding-admin/v1/programs", 200, "{\"items\":[" + program.replace("\"jurisdictionCode\":\"SA\"", "\"jurisdictionCode\":\"AE\"") + "]}");
        assertEquals(502, send("GET", "/onboarding-config-admin/v1/programs?jurisdiction=SA", "operations-onboarding-config-programs", null).statusCode());
    }

    /** CM-11 列表：过滤 Query 原样转发；PayFac 选项来自 client-master Partner 列表。 */
    @Test
    void test_clientListAndPartnerOptions() throws Exception {
        owners.reply("GET", MASTER + "/clients", 200, "{\"items\":[{\"clientId\":\"8101\",\"clientNo\":\"CL8101\",\"clientType\":\"ORGANIZATION\","
            + "\"legalName\":\"Synthetic Co\",\"jurisdictionCode\":\"SA\",\"status\":\"ACTIVE\",\"responsiblePartnerId\":\"9001\","
            + "\"latestApplicationId\":null,\"latestApplicationStatus\":null,\"createdAt\":\"2026-10-27T00:00:00.000000Z\"}],\"page\":1,\"size\":20,\"total\":1}");
        owners.reply("GET", MASTER + "/local-partners", 200, "{\"items\":[{\"localPartnerId\":\"9001\",\"partnerNo\":\"LP9001\",\"legalName\":\"Our PayFac\","
            + "\"status\":\"ACTIVE\",\"roles\":[\"OE_PROVIDER\"],\"providedEntityCount\":1,\"changeVersion\":2}],\"page\":1,\"size\":100,\"total\":1}");

        var list = send("GET", "/client-admin/v1/clients?page=1&size=20&status=ACTIVE&jurisdiction=SA&responsiblePartnerId=9001", "operations-client-admin-list", null);
        var partners = send("GET", "/client-admin/v1/responsible-partners", "operations-client-admin-responsible-partners", null);

        assertOk(list);
        assertEquals(1, JSON.readTree(list.body()).path("total").asLong());
        assertEquals("status=ACTIVE&jurisdiction=SA&responsiblePartnerId=9001&page=1&size=20", owners.requests().get(0).query());
        assertOk(partners);
        assertEquals("status=ACTIVE&role=" + PartnerRoleCode.OE_PROVIDER.getCode() + "&page=1&size=100", owners.requests().get(1).query());
        assertEquals(400, send("GET", "/client-admin/v1/clients?size=101", "operations-client-admin-list", null).statusCode());
        assertEquals(2, owners.requests().size());
        assertTrustedCalls();
    }

    /** CM-11 详情：主档 + Client 资格；资格读取失败只标为 UNAVAILABLE。 */
    @Test
    void test_clientDetailAggregatesQualifications() throws Exception {
        owners.reply("GET", MASTER + "/clients/" + CLIENT_ID, 200, CLIENT);
        owners.replyQuery("GET", COMPLIANCE + "/qualifications", CLIENT_QUALIFICATIONS, 200, "{\"items\":[],\"page\":1,\"size\":50,\"total\":0}");

        var response = send("GET", "/client-admin/v1/clients/" + CLIENT_ID, "operations-client-admin-detail", null);

        assertOk(response);
        JsonNode body = JSON.readTree(response.body());
        assertEquals("******7890", body.path("client").path("values").path("identifier").path("identifierMasked").asString());
        assertEquals("7001", body.path("client").path("applications").get(0).path("dueDiligenceCaseId").asString());
        assertEquals("UBO", body.path("client").path("relations").get(0).path("relationType").asString());
        assertEquals(ReadAvailability.AVAILABLE.getCode(), body.path("qualificationsStatus").asString());
        assertEquals("roleType=" + QualificationRoleType.CLIENT.getCode() + "&roleId=8101&page=1&size=50", owners.requests().get(1).query());
        assertTrustedCalls();

        owners.reset();
        owners.reply("GET", MASTER + "/clients/" + CLIENT_ID, 200, CLIENT);
        owners.problemQuery("GET", COMPLIANCE + "/qualifications", CLIENT_QUALIFICATIONS, 503, "SERVICE_UNAVAILABLE");

        JsonNode degraded = JSON.readTree(send("GET", "/client-admin/v1/clients/" + CLIENT_ID, "operations-client-admin-detail", null).body());

        assertEquals(ReadAvailability.UNAVAILABLE.getCode(), degraded.path("qualificationsStatus").asString());
        assertTrue(degraded.path("qualifications").isEmpty());
    }

    /** CM-11 详情：Client 不存在保留 404，不读取资格；返回其他 Client 为 502。 */
    @Test
    void test_clientDetailNotFoundAndMismatch() throws Exception {
        owners.problem("GET", MASTER + "/clients/" + CLIENT_ID, 404, CheckoutCandidateErrorCode.CLIENT_NOT_FOUND.getCode());

        assertProblem(send("GET", "/client-admin/v1/clients/" + CLIENT_ID, "operations-client-admin-detail", null), 404, CheckoutCandidateErrorCode.CLIENT_NOT_FOUND);
        assertEquals(1, owners.requests().size());

        owners.reset();
        owners.reply("GET", MASTER + "/clients/" + CLIENT_ID, 200, CLIENT.replace("\"clientId\":\"8101\"", "\"clientId\":\"8102\""));
        var mismatch = send("GET", "/client-admin/v1/clients/" + CLIENT_ID, "operations-client-admin-detail", null);
        assertEquals(502, mismatch.statusCode(), mismatch.body());
        assertFalse(mismatch.body().contains("Synthetic Co"));
    }

    /** CM-12：列表只输出掩码并按状态过滤（BFF）；签发（201）只在响应中带一次明文；作废无请求体。 */
    @Test
    void test_onboardingCodesListIssueRevoke() throws Exception {
        String revoked = CODE.replace("7101", "7102").replace("****-7Q2K", "****-9X8Y").replace("\"status\":\"ACTIVE\"", "\"status\":\"REVOKED\"");
        owners.reply("GET", CODES, 200, "[" + CODE + "," + revoked + "]");
        owners.reply("POST", CODES, 201, CODE.replace("\"code\":null", "\"code\":\"AB12-7Q2K\""));
        owners.reply("POST", CODES + "/7101/revocation", 200, CODE.replace("\"status\":\"ACTIVE\"", "\"status\":\"REVOKED\""));

        var list = send("GET", "/onboarding-code-admin/v1/local-partners/" + PARTNER_ID + "/onboarding-codes?status=ACTIVE", "operations-onboarding-code-list", null);
        var issued = send("POST", "/onboarding-code-admin/v1/local-partners/" + PARTNER_ID + "/onboarding-codes", "operations-onboarding-code-issue",
            "{\"effectiveTo\":\"2026-12-31T00:00:00Z\",\"maxUses\":50,\"note\":\"Riyadh launch\"}");
        var revoke = send("POST", "/onboarding-code-admin/v1/local-partners/" + PARTNER_ID + "/onboarding-codes/7101/revocation", "operations-onboarding-code-revoke", null);

        assertOk(list);
        JsonNode items = JSON.readTree(list.body()).path("items");
        assertEquals(1, items.size(), "状态过滤在 BFF");
        assertEquals("****-7Q2K", items.get(0).path("codeMasked").asString());
        assertFalse(list.body().contains("\"code\""), "列表不带明文字段");
        assertNull(owners.requests().get(0).query(), "client-master 不收状态参数");
        assertOk(issued);
        assertEquals("AB12-7Q2K", JSON.readTree(issued.body()).path("code").asString());
        assertEquals("****-7Q2K", JSON.readTree(issued.body()).path("issued").path("codeMasked").asString());
        assertEquals(50, JSON.readTree(owners.requests().get(1).body()).path("maxUses").asInt());
        assertOk(revoke);
        assertEquals("REVOKED", JSON.readTree(revoke.body()).path("status").asString());
        assertFalse(revoke.body().contains("AB12"));
        assertTrue(owners.requests().get(2).body().isEmpty(), "作废不带请求体");
        assertTrustedCalls();
    }

    /** CM-12 拒绝：非 OE_PROVIDER 422、入驻码不存在 404；非法 Body 不调用 Owner；列表带明文或掩码格式不符为 502，不外露。 */
    @Test
    void test_onboardingCodeRejections() throws Exception {
        owners.problem("POST", CODES, 422, OnboardingErrorCode.PARTNER_NOT_OE_PROVIDER.getCode());
        owners.problem("POST", CODES + "/7109/revocation", 404, OnboardingErrorCode.ONBOARDING_CODE_NOT_FOUND.getCode());

        assertProblem(send("POST", "/onboarding-code-admin/v1/local-partners/" + PARTNER_ID + "/onboarding-codes", "operations-onboarding-code-issue", "{}"),
            422, OnboardingErrorCode.PARTNER_NOT_OE_PROVIDER);
        assertProblem(send("POST", "/onboarding-code-admin/v1/local-partners/" + PARTNER_ID + "/onboarding-codes/7109/revocation", "operations-onboarding-code-revoke",
            null), 404, OnboardingErrorCode.ONBOARDING_CODE_NOT_FOUND);
        int before = owners.requests().size();
        for (String bad : List.of("{\"maxUses\":0}", "{\"maxUses\":\"5\"}", "{\"note\":\"" + "x".repeat(256) + "\"}", "{\"code\":\"AB12-7Q2K\"}", "[]")) {
            var response = send("POST", "/onboarding-code-admin/v1/local-partners/" + PARTNER_ID + "/onboarding-codes", "operations-onboarding-code-issue", bad);
            assertTrue(response.statusCode() == 400 || response.statusCode() == 422, bad + " " + response.statusCode());
        }
        assertEquals(before, owners.requests().size());

        owners.reply("GET", CODES, 200, "[" + CODE.replace("\"code\":null", "\"code\":\"AB12-7Q2K\"") + "]");
        var leaked = send("GET", "/onboarding-code-admin/v1/local-partners/" + PARTNER_ID + "/onboarding-codes", "operations-onboarding-code-list", null);
        assertEquals(502, leaked.statusCode(), leaked.body());
        assertFalse(leaked.body().contains("AB12-7Q2K"));
        owners.reply("GET", CODES, 200, "[" + CODE.replace("****-7Q2K", "AB12-7Q2K") + "]");
        var unmasked = send("GET", "/onboarding-code-admin/v1/local-partners/" + PARTNER_ID + "/onboarding-codes", "operations-onboarding-code-list", null);
        assertEquals(502, unmasked.statusCode(), unmasked.body());
        assertFalse(unmasked.body().contains("AB12-7Q2K"));
    }

    /** 未授权：PLT-I02 拒绝后不调用 Owner。 */
    @Test
    void test_deniedDoesNotCallOwner() throws Exception {
        platform.allow(false);

        assertEquals(403, send("POST", "/onboarding-code-admin/v1/local-partners/" + PARTNER_ID + "/onboarding-codes", "operations-onboarding-code-issue", "{}").statusCode());
        assertEquals(403, send("GET", "/client-admin/v1/clients/" + CLIENT_ID, "operations-client-admin-detail", null).statusCode());
        assertTrue(owners.requests().isEmpty());
    }

    private void assertTrustedCalls() {
        for (var call : owners.requests()) {
            assertEquals(RISK_ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"), call.path());
            assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"), call.path());
            assertEquals("onboarding-admin-browser", call.header("X-Request-Id"), call.path());
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
    private HttpResponse<String> send(String method, String path, String route, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(8));
        Map.ofEntries(Map.entry("Origin", "https://operations-console.local.test"), Map.entry("X-Request-Id", "onboarding-admin-browser"),
            Map.entry("Idempotency-Key", "browser-key-0000000002"),
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
