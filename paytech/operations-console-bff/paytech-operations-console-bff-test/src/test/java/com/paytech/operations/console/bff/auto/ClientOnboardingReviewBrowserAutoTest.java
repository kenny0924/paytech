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

import com.paytech.client.master.api.enums.EntityAdminErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
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
    private static final String INDUSTRIES_PATH = "/platform/v1/reference-data/industries";
    private static final String MCCS_PATH = "/platform/v1/reference-data/mccs";
    /** Platform 平铺行业：细类先于大类、排序号乱序，用于证明 BFF 组装与排序；OTHER_RETAIL 的上级不在列表中。 */
    private static final String INDUSTRIES = """
        {"items":[
          {"industryCode":"RETAIL_HOME","parentIndustryCode":"RETAIL","level":2,"name":"Home & furniture","defaultMccCode":"5712","sortNo":30,"status":"ACTIVE","version":1},
          {"industryCode":"RETAIL","parentIndustryCode":null,"level":1,"name":"Retail","logoPublicUrl":"https://static.local.test/retail.svg","sortNo":10,"status":"ACTIVE","version":1},
          {"industryCode":"RETAIL_GENERAL","parentIndustryCode":"RETAIL","level":2,"name":"General merchandise","defaultMccCode":"5399","sortNo":10,"status":"ACTIVE","version":1},
          {"industryCode":"DIGITAL_GOODS","parentIndustryCode":null,"level":1,"name":"Digital goods","sortNo":5,"status":"ACTIVE","version":1},
          {"industryCode":"OTHER_RETAIL","parentIndustryCode":"OTHER","level":2,"name":"Other retail","defaultMccCode":"5999","sortNo":10,"status":"ACTIVE","version":1}]}
        """;
    private static final String MCCS = """
        {"items":[
          {"mccCode":"5399","name":"Misc. General Merchandise","networkHighRisk":false,"status":"ACTIVE","version":1},
          {"mccCode":"5411","name":"Grocery Stores, Supermarkets","networkHighRisk":false,"status":"INACTIVE","version":2},
          {"mccCode":"5712","name":"Furniture","networkHighRisk":false,"status":"ACTIVE","version":1}],"page":1,"size":50,"total":3}
        """;
    private static final String FILE = "9001";
    private static final String FILE_PATH = "/internal/files/v1/files/" + FILE;
    private static final String DOWNLOAD_PATH = FILE_PATH + "/download";
    private static final String RISK_CASE_WITH_FILE = RISK_CASE.replace("\"subjects\":[]", "\"subjects\":[{\"caseId\":\"7001\","
        + "\"caseStatus\":\"SUBMITTED\",\"partyId\":\"8201\",\"profileId\":\"8301\",\"roleType\":\"CLIENT\","
        + "\"qualificationType\":\"ORGANIZATION_KYB\",\"dependencyTypes\":[],\"requirements\":[{\"requirementId\":\"8401\","
        + "\"requirementCode\":\"REGISTRATION\",\"documentTypes\":[\"REGISTRATION_CERTIFICATE\"],\"required\":true,"
        + "\"status\":\"RECEIVED\",\"evidence\":[{\"evidenceId\":\"8501\",\"fileAddress\":\"/files/v1/" + FILE + "\",\"status\":\"RECEIVED\"}]}]}]");
    private static final String RETURN = "{\"reasonCode\":\"ADDRESS_UNCLEAR\",\"errors\":[{\"requirementKey\":\"document.PROOF_OF_ADDRESS\","
        + "\"subjectPartyId\":\"8201\",\"code\":\"DOCUMENT_MISSING\",\"reason\":null,\"remediation\":\"UPLOAD_DOCUMENT\"}],\"dueDays\":14,"
        + "\"publicNote\":\"请补充经营地址证明\"}";
    private static final String PARTNER = "9001";
    private static final String PARTNER_PATH = "/client-master-admin/v1/local-partners/" + PARTNER;
    private static final String PAYFAC = "{\"localPartnerId\":\"9001\",\"partnerNo\":\"LP9001\",\"partyId\":\"880000000002\","
        + "\"legalName\":\"Our PayFac\",\"status\":\"ACTIVE\",\"roles\":[\"OE_PROVIDER\"],\"changeVersion\":2,\"providedEntities\":[]}";
    private static final String STORED_FILE = "{\"fileId\":\"" + FILE + "\",\"fileAddress\":\"/files/v1/" + FILE + "\","
        + "\"sourceSystem\":\"client-console-bff\",\"reasonCode\":\"CLIENT_ONBOARDING_MATERIAL\",\"displayName\":\"registration.pdf\","
        + "\"mimeType\":\"application/pdf\",\"sizeBytes\":\"1024\",\"contentHash\":\"" + "a".repeat(64) + "\","
        + "\"scanStatus\":\"AVAILABLE\",\"objectStatus\":\"ACTIVE\",\"uploaderAccountId\":\"8601\","
        + "\"createTime\":\"2026-10-13T00:00:00Z\",\"accessLevel\":\"PRIVATE\",\"versionNo\":1,\"publicUrl\":null,"
        + "\"versionedUrl\":null,\"pendingVersionNo\":null}";
    private static final String DOWNLOAD = "{\"url\":\"https://storage.local.test/obj?sig=1\",\"expiresAt\":\"2026-10-14T00:01:00Z\",\"disposition\":\"ATTACHMENT\"}";
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

    /** 详情：Risk 任务 + CCT 申请、分类三次 Owner 调用携带可信身份；Platform ACTIVE 行业组装为树，MCC 按分类与建议编码批量读取。 */
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
        assertEquals("AVAILABLE", body.path("industriesStatus").asString());
        var industries = body.path("industries");
        assertEquals(List.of("DIGITAL_GOODS", "RETAIL"), industries.valueStream().map(i -> i.path("industryCode").asString()).toList());
        assertEquals(0, industries.get(0).path("children").size());
        assertEquals("https://static.local.test/retail.svg", industries.get(1).path("logoPublicUrl").asString());
        assertEquals(List.of("RETAIL_GENERAL", "RETAIL_HOME"),
            industries.get(1).path("children").valueStream().map(i -> i.path("industryCode").asString()).toList());
        assertEquals("5399", industries.get(1).path("children").get(0).path("defaultMccCode").asString());
        assertFalse(response.body().contains("OTHER_RETAIL"), "上级大类不在列表中的细类不展示");
        assertEquals("AVAILABLE", body.path("mccsStatus").asString());
        assertEquals("INACTIVE", body.path("mccs").get(1).path("status").asString());
        assertEquals(3, owners.requests().size());
        assertTrustedCalls();
        var industryCall = platformCall(INDUSTRIES_PATH);
        assertEquals(Map.of("status", "ACTIVE"), query(industryCall.query()));
        assertNull(industryCall.header("X-Gateway-Actor-Subject"), "参考数据查询不携带操作人");
        assertEquals("client-review-browser", industryCall.header("X-Request-Id"));
        assertEquals(Map.of("codes", "5399,5411,5712", "page", "1", "size", "50"), query(platformCall(MCCS_PATH).query()));
    }

    /** Platform 行业不可用：只把行业标为 UNAVAILABLE，MCC 只按当前分类读取，其余部分照常返回。 */
    @Test
    void test_detailMarksUnavailableReferenceData() throws Exception {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE);
        owners.reply("GET", ROOT + "/cases/" + CASE + "/application", 200, APPLICATION);
        owners.reply("GET", ROOT + "/cases/" + CASE + "/classifications", 200, CLASSIFICATION);
        platform.problem(INDUSTRIES_PATH, 503, "SERVICE_UNAVAILABLE");
        platform.groupedResponses(Map.of(MCCS_PATH, """
            {"items":[{"mccCode":"5411","name":"Grocery Stores, Supermarkets","networkHighRisk":false,"status":"INACTIVE","version":2}],
             "page":1,"size":50,"total":1}
            """));

        var response = send("GET", ROOT + "/cases/" + CASE, "client-onboarding-o02", null);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertEquals("UNAVAILABLE", body.path("industriesStatus").asString());
        assertEquals(0, body.path("industries").size());
        assertEquals("AVAILABLE", body.path("classificationStatus").asString());
        assertEquals("AVAILABLE", body.path("mccsStatus").asString());
        assertEquals(Map.of("codes", "5411", "page", "1", "size", "50"), query(platformCall(MCCS_PATH).query()));
    }

    /** O03 分类用 MCC 搜索：只列 ACTIVE，关键字去除首尾空白，分页参数原样；非法分页在访问 Platform 前拒绝。 */
    @Test
    void test_mccSearchForClassification() throws Exception {
        platform.groupedResponses(Map.of(MCCS_PATH, MCCS.replace("\"size\":50", "\"size\":10")));

        var response = send("GET", ROOT + "/mccs?keyword=%20software%20&page=1&size=10", "client-onboarding-mcc-search", null);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(3, JSON.readTree(response.body()).path("items").size());
        assertEquals(Map.of("keyword", "software", "status", "ACTIVE", "page", "1", "size", "10"),
            query(platformCall(MCCS_PATH).query()));
        for (String bad : List.of("?size=15", "?page=0", "?status=ACTIVE", "?keyword=", "?keyword=a&keyword=b")) {
            assertEquals(400, send("GET", ROOT + "/mccs" + bad, "client-onboarding-mcc-search", null).statusCode(), bad);
        }
        assertEquals(1, platform.requests().stream().filter(request -> request.path().equals(MCCS_PATH)).count());
        assertTrue(owners.requests().isEmpty());
    }

    /**
     * client-master 分类目录校验（CLASSIFICATION_INVALID）：带 errors 时逐行字段路径到达页面；整组错误（无 errors）为表单级 422；
     * Platform 不可用时 client-master 的 503 映射为依赖不可用 503，不是 502。
     */
    @Test
    void test_classificationCatalogRejections() throws Exception {
        String body = "{\"items\":[{\"industryCode\":\"RETAIL\",\"mccCode\":\"5411\",\"primary\":true},"
            + "{\"industryCode\":\"RETAIL_HOME\",\"mccCode\":\"9999\",\"primary\":false}]}";
        owners.problemBody("PUT", ROOT + "/cases/" + CASE + "/classifications", 422, """
            {"type":"about:blank","title":"Unprocessable Content","detail":"分类不符合目录或规则","code":"CLASSIFICATION_INVALID",
             "requestId":"owner-request","timestamp":"2026-10-27T00:00:00.123456Z","errors":[
              {"field":"items[0].industryCode","message":"须选择细类，RETAIL 是大类"},{"field":"items[1].mccCode","message":"MCC 不存在: 9999"}]}
            """);
        var rows = send("PUT", ROOT + "/cases/" + CASE + "/classifications", "client-onboarding-o03", body);
        assertEquals(422, rows.statusCode(), rows.body());
        JsonNode rowBody = JSON.readTree(rows.body());
        assertEquals("CLASSIFICATION_INVALID", rowBody.path("code").asString());
        assertEquals(List.of("items[0].industryCode", "items[1].mccCode"),
            rowBody.path("errors").valueStream().map(e -> e.path("field").asString()).sorted().toList());

        owners.problem("PUT", ROOT + "/cases/" + CASE + "/classifications", 422, "CLASSIFICATION_INVALID");
        var whole = send("PUT", ROOT + "/cases/" + CASE + "/classifications", "client-onboarding-o03", body);
        assertProblem(whole, 422, "CLASSIFICATION_INVALID");
        assertEquals(0, JSON.readTree(whole.body()).path("errors").size());

        owners.problem("PUT", ROOT + "/cases/" + CASE + "/classifications", 503, "ONBOARDING_DEPENDENCY_UNAVAILABLE");
        var unavailable = send("PUT", ROOT + "/cases/" + CASE + "/classifications", "client-onboarding-o03", body);
        assertEquals(503, unavailable.statusCode(), unavailable.body());
        assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode(), JSON.readTree(unavailable.body()).path("code").asString());
    }

    /** CCT 分类 422：字段路径投影到页面请求体，页面据此把错误挂到对应行与控件；远端文案不外露。 */
    @Test
    void test_classificationValidationPathsProjected() throws Exception {
        owners.problemBody("PUT", ROOT + "/cases/" + CASE + "/classifications", 422, """
            {"type":"about:blank","title":"Unprocessable Content","detail":"remote-private-detail","code":"VALIDATION_ERROR",
             "requestId":"owner-request","timestamp":"2026-10-27T00:00:00.123456Z","errors":[
              {"field":"items[1].industryCode","message":"remote-private-message"},{"field":"items[0].mccCode","message":"x"},
              {"field":"privateField","message":"unknown"}]}
            """);

        var response = send("PUT", ROOT + "/cases/" + CASE + "/classifications", "client-onboarding-o03",
            "{\"items\":[{\"industryCode\":\"RETAIL_HOME\",\"mccCode\":\"5419\",\"primary\":true},"
                + "{\"industryCode\":\"RETAIL\",\"mccCode\":\"5712\",\"primary\":false}]}");

        assertEquals(422, response.statusCode(), response.body());
        JsonNode body = JSON.readTree(response.body());
        assertEquals("VALIDATION_ERROR", body.path("code").asString());
        assertEquals(List.of("items[0].mccCode", "items[1].industryCode"),
            body.path("errors").valueStream().map(e -> e.path("field").asString()).toList());
        assertFalse(response.body().contains("remote-private"));
        assertFalse(response.body().contains("privateField"));
    }

    /** CCT 申请资料或目录不可用：对应部分标为 UNAVAILABLE，详情仍返回。 */
    @Test
    void test_detailMarksUnavailableCctParts() throws Exception {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE);
        owners.problem("GET", ROOT + "/cases/" + CASE + "/application", 503, "SERVICE_UNAVAILABLE");
        owners.problem("GET", ROOT + "/cases/" + CASE + "/classifications", 404, "REVIEW_CASE_NOT_FOUND");
        platform.groupedResponses(Map.of(INDUSTRIES_PATH, INDUSTRIES, MCCS_PATH, MCCS));

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
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/return", "client-onboarding-o07", RETURN));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/approval", "client-onboarding-o08",
            "{\"reasonCode\":\"KYB_PASSED\",\"reason\":\"材料齐全\",\"publicNote\":null}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/rejection", "client-onboarding-o09",
            "{\"reasonCode\":\"KYB_FAILED\",\"reason\":\"主体不符\",\"publicNote\":\"未通过审核\"}"));
        assertOk(send("PUT", ROOT + "/cases/" + CASE + "/classifications", "client-onboarding-o03",
            "{\"items\":[{\"industryCode\":\"RETAIL\",\"mccCode\":\"5411\",\"primary\":true}]}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/classification-confirmation", "client-onboarding-o04", null));

        var calls = owners.requests();
        assertEquals(6, calls.size());
        assertEquals("ACCEPTED", JSON.readTree(calls.get(0).body()).path("outcome").asString());
        assertTrue(JSON.readTree(calls.get(0).body()).path("onBehalfOfPartnerId").isNull(), "平台自营不带代操作 PayFac");
        JsonNode returned = JSON.readTree(calls.get(1).body());
        assertEquals("document.PROOF_OF_ADDRESS", returned.path("errors").get(0).path("requirementKey").asString());
        assertEquals(14, returned.path("dueDays").asInt());
        assertEquals("请补充经营地址证明", returned.path("publicNote").asString());
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
        var returned = send("POST", ROOT + "/cases/" + CASE + "/return", "client-onboarding-o07", RETURN);

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
    @ValueSource(strings = {"{}", "{\"reasonCode\":\"R\",\"errors\":[],\"dueDays\":14,\"publicNote\":\"x\"}",
        "{\"reasonCode\":\"R\",\"errors\":[{\"requirementKey\":\"document X\",\"code\":\"DOCUMENT_MISSING\",\"remediation\":\"UPLOAD_DOCUMENT\"}],\"dueDays\":14,\"publicNote\":\"x\"}",
        "{\"reasonCode\":\"R\",\"errors\":[{\"requirementKey\":\"document.X\",\"code\":\"DOCUMENT_MISSING\",\"remediation\":\"UPLOAD_DOCUMENT\"}],\"dueDays\":91,\"publicNote\":\"x\"}",
        "{\"reasonCode\":\"R\",\"errors\":[{\"requirementKey\":\"document.X\",\"code\":\"DOCUMENT_MISSING\",\"remediation\":\"UPLOAD_DOCUMENT\"}],\"dueDays\":14,\"publicNote\":\"x\",\"internal\":\"y\"}",
        "{\"reasonCode\":\"R\",\"reasonCode\":\"S\",\"errors\":[],\"dueDays\":14,\"publicNote\":\"x\"}", "[]"})
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

    /** O10 下载：文件属于任务证据时，先取 Platform 元数据，再以操作人签发地址，拼成原响应。 */
    @Test
    void test_fileDownloadChecksCaseEvidence() throws Exception {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE_WITH_FILE);
        platform.groupedResponses(Map.of(FILE_PATH, STORED_FILE, DOWNLOAD_PATH, DOWNLOAD));

        var response = send("POST", ROOT + "/cases/" + CASE + "/files/" + FILE + "/download", "client-onboarding-o10", null);

        assertOk(response);
        JsonNode body = JSON.readTree(response.body());
        assertEquals(FILE, body.path("fileId").asString());
        assertEquals("registration.pdf", body.path("displayName").asString());
        assertEquals("application/pdf", body.path("mimeType").asString());
        assertEquals("1024", body.path("sizeBytes").asString());
        assertEquals("https://storage.local.test/obj?sig=1", body.path("url").asString());
        assertEquals("2026-10-14T00:01:00Z", body.path("expiresAt").asString());
        assertFalse(body.has("contentHash"));
        assertFalse(body.has("uploaderAccountId"));
        var meta = fileCalls(FILE_PATH);
        var grant = fileCalls(DOWNLOAD_PATH);
        assertEquals(1, meta.size());
        assertEquals(1, grant.size());
        assertEquals("GET", meta.getFirst().method());
        assertEquals("POST", grant.getFirst().method());
        assertTrue(meta.getFirst().sequence() < grant.getFirst().sequence());
        assertNull(meta.getFirst().header("X-Operator-Account-Id"));
        assertEquals(RISK_ACCOUNT_ID, grant.getFirst().header("X-Operator-Account-Id"));
        assertNull(grant.getFirst().query(), "不指定下载方式，按 Platform 默认附件下载");
        for (var call : List.of(meta.getFirst(), grant.getFirst())) {
            assertEquals("operations-console-bff", call.header("X-Caller-Service"));
            assertNull(call.header("Idempotency-Key"));
        }
        assertTrustedCalls();
    }

    /** 文件不在任务证据中：404 FILE_NOT_FOUND，不调用 Platform 文件接口。 */
    @Test
    void test_fileOutsideCaseIsNotFound() throws Exception {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE);

        var response = send("POST", ROOT + "/cases/" + CASE + "/files/" + FILE + "/download", "client-onboarding-o10", null);

        assertProblem(response, 404, "FILE_NOT_FOUND");
        assertTrue(fileCalls(FILE_PATH).isEmpty());
        assertTrue(fileCalls(DOWNLOAD_PATH).isEmpty());
    }

    /** Platform 查不到文件：保留 404 FILE_NOT_FOUND，不再签发地址。 */
    @Test
    void test_fileMissingStopsBeforeDownload() throws Exception {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE_WITH_FILE);
        platform.problem(FILE_PATH, 404, "FILE_NOT_FOUND");

        var response = send("POST", ROOT + "/cases/" + CASE + "/files/" + FILE + "/download", "client-onboarding-o10", null);

        assertProblem(response, 404, "FILE_NOT_FOUND");
        assertTrue(fileCalls(DOWNLOAD_PATH).isEmpty());
    }

    /** Platform 拒绝签发（文件尚不可用）：保留 409 与错误码。 */
    @Test
    void test_fileStateConflictKeepsCode() throws Exception {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE_WITH_FILE);
        platform.rawResponse(FILE_PATH, STORED_FILE);
        platform.problem(DOWNLOAD_PATH, 409, "FILE_STATE_CONFLICT");

        var response = send("POST", ROOT + "/cases/" + CASE + "/files/" + FILE + "/download", "client-onboarding-o10", null);

        assertProblem(response, 409, "FILE_STATE_CONFLICT");
    }

    /** Platform 签发不可用：503 透传，不外露地址。 */
    @Test
    void test_downloadUnavailableIsServiceUnavailable() throws Exception {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE_WITH_FILE);
        platform.rawResponse(FILE_PATH, STORED_FILE);
        platform.problem(DOWNLOAD_PATH, 503, "SERVICE_UNAVAILABLE");

        var response = send("POST", ROOT + "/cases/" + CASE + "/files/" + FILE + "/download", "client-onboarding-o10", null);

        assertEquals(503, response.statusCode(), response.body());
        assertFalse(response.body().contains("storage.local.test"));
    }

    /** Platform 返回其他文件的元数据：上游响应无效 502，不签发地址。 */
    @Test
    void test_mismatchedFileResponseIsRejected() throws Exception {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE_WITH_FILE);
        platform.groupedResponses(Map.of(FILE_PATH, STORED_FILE.replace("\"fileId\":\"" + FILE + "\"", "\"fileId\":\"9002\""),
            DOWNLOAD_PATH, DOWNLOAD));

        var response = send("POST", ROOT + "/cases/" + CASE + "/files/" + FILE + "/download", "client-onboarding-o10", null);

        assertEquals(502, response.statusCode(), response.body());
        assertFalse(response.body().contains("storage.local.test"));
        assertTrue(fileCalls(DOWNLOAD_PATH).isEmpty());
    }

    /** CP-5 六个工作台命令：请求体按 compliance 合同原样转发，路径参数到达对应资源。 */
    @Test
    void test_workbenchCommandsForwardOwnerContracts() throws Exception {
        for (String path : List.of("/checks/8701/result", "/screenings/run", "/screening-hits/8801/resolution", "/recommendation", "/requirements/8401/waiver")) {
            owners.reply("POST", ROOT + "/cases/" + CASE + path, 200, COMMAND);
        }
        owners.reply("PUT", ROOT + "/cases/" + CASE + "/risk-rating", 200, COMMAND);

        assertOk(send("POST", ROOT + "/cases/" + CASE + "/checks/8701/result", "client-onboarding-check-result",
            "{\"outcome\":\"FAIL\",\"reasonCode\":\"REGISTRY_NOT_FOUND\",\"note\":\"CR not in registry\"}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/screenings/run", "client-onboarding-screening-run", "{}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/screening-hits/8801/resolution", "client-onboarding-hit-resolution",
            "{\"resolution\":\"FALSE_POSITIVE\",\"reasonCode\":\"DOB_DIFFERENT\",\"note\":null}"));
        assertOk(send("PUT", ROOT + "/cases/" + CASE + "/risk-rating", "client-onboarding-risk-rating",
            "{\"riskRating\":\"HIGH\",\"factors\":[{\"factorCode\":\"PEP\",\"factorValue\":\"Yes\"}],\"note\":\"PEP director\"}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/recommendation", "client-onboarding-recommendation",
            "{\"recommendation\":\"APPROVE\",\"note\":\"All checks passed\"}"));
        assertOk(send("POST", ROOT + "/cases/" + CASE + "/requirements/8401/waiver", "client-onboarding-waiver",
            "{\"reasonCode\":\"LISTED_COMPANY\",\"note\":\"Listed on Tadawul\"}"));

        var calls = owners.requests();
        assertEquals(6, calls.size());
        assertEquals("REGISTRY_NOT_FOUND", JSON.readTree(calls.get(0).body()).path("reasonCode").asString());
        assertEquals("FALSE_POSITIVE", JSON.readTree(calls.get(2).body()).path("resolution").asString());
        assertEquals("PUT", calls.get(3).method());
        assertEquals("PEP", JSON.readTree(calls.get(3).body()).path("factors").get(0).path("factorCode").asString());
        assertEquals("APPROVE", JSON.readTree(calls.get(4).body()).path("recommendation").asString());
        assertEquals(ROOT + "/cases/" + CASE + "/requirements/8401/waiver", calls.get(5).path());
        assertTrustedCalls();
    }

    /** 不符合 compliance 合同的工作台 Body（缺必填原因码、未知处置、因子越界）在 BFF 拒绝，不调用 Owner。 */
    @Test
    void test_invalidWorkbenchBodiesNeverReachOwner() throws Exception {
        assertBadRequest(send("POST", ROOT + "/cases/" + CASE + "/checks/8701/result", "client-onboarding-check-result", "{\"outcome\":\"PASS\"}"));
        assertBadRequest(send("POST", ROOT + "/cases/" + CASE + "/screening-hits/8801/resolution", "client-onboarding-hit-resolution",
            "{\"resolution\":\"CONFIRMED_MATCH\",\"reasonCode\":\"X\"}"));
        assertBadRequest(send("PUT", ROOT + "/cases/" + CASE + "/risk-rating", "client-onboarding-risk-rating",
            "{\"riskRating\":\"HIGH\",\"factors\":[],\"note\":\"x\"}"));
        assertBadRequest(send("POST", ROOT + "/cases/" + CASE + "/recommendation", "client-onboarding-recommendation",
            "{\"recommendation\":\"APPROVE\",\"note\":\"x\",\"onBehalfOfPartnerId\":\"payfac\"}"));
        assertTrue(owners.requests().isEmpty());
    }

    /** 代 PayFac 操作：先核对 client-master Partner（ACTIVE + OE_PROVIDER），再写 compliance 并原样带上 onBehalfOfPartnerId。 */
    @Test
    void test_onBehalfPartnerCheckedBeforeWrite() throws Exception {
        owners.reply("GET", PARTNER_PATH, 200, PAYFAC);
        owners.reply("POST", ROOT + "/cases/" + CASE + "/approval", 200, COMMAND);

        assertOk(send("POST", ROOT + "/cases/" + CASE + "/approval", "client-onboarding-o08",
            "{\"reasonCode\":\"KYB_PASSED\",\"reason\":\"材料齐全\",\"publicNote\":null,\"onBehalfOfPartnerId\":\"" + PARTNER + "\"}"));

        var calls = owners.requests();
        assertEquals(List.of(PARTNER_PATH, ROOT + "/cases/" + CASE + "/approval"), calls.stream().map(ClientReviewOwnerSimulator.Request::path).toList());
        assertEquals(PARTNER, JSON.readTree(calls.get(1).body()).path("onBehalfOfPartnerId").asString());
        assertTrustedCalls();
    }

    /** 代操作 PayFac 不是 OE_PROVIDER、未生效或不存在：422 ON_BEHALF_PARTNER_INVALID，不调用 compliance。 */
    @Test
    void test_ineligibleOnBehalfPartnerRejected() throws Exception {
        String body = "{\"reasonCode\":\"KYB_PASSED\",\"reason\":\"材料齐全\",\"onBehalfOfPartnerId\":\"" + PARTNER + "\"}";
        for (String partner : List.of(PAYFAC.replace("\"OE_PROVIDER\"", "\"REFERRER\""), PAYFAC.replace("\"status\":\"ACTIVE\"", "\"status\":\"SUSPENDED\""))) {
            owners.reset();
            owners.reply("GET", PARTNER_PATH, 200, partner);
            assertProblem(send("POST", ROOT + "/cases/" + CASE + "/approval", "client-onboarding-o08", body), 422,
                OperationsBffErrorCode.ON_BEHALF_PARTNER_INVALID.getCode());
            assertEquals(1, owners.requests().size());
        }
        owners.reset();
        owners.problem("GET", PARTNER_PATH, 404, EntityAdminErrorCode.LOCAL_PARTNER_NOT_FOUND.getCode());

        assertProblem(send("POST", ROOT + "/cases/" + CASE + "/approval", "client-onboarding-o08", body), 422,
            OperationsBffErrorCode.ON_BEHALF_PARTNER_INVALID.getCode());
        assertEquals(1, owners.requests().size());
    }

    /** PayFac 选项：client-master ACTIVE + OE_PROVIDER 单页，裁剪为选项；列表按责任方与辖区过滤原样转发。 */
    @Test
    void test_responsiblePartnersAndListFilters() throws Exception {
        owners.reply("GET", "/client-master-admin/v1/local-partners", 200, "{\"items\":[{\"localPartnerId\":\"9001\",\"partnerNo\":\"LP9001\","
            + "\"legalName\":\"Our PayFac\",\"status\":\"ACTIVE\",\"roles\":[\"OE_PROVIDER\"],\"providedEntityCount\":1,\"changeVersion\":2}],"
            + "\"page\":1,\"size\":100,\"total\":1}");
        owners.reply("GET", ROOT + "/cases", 200, "{\"items\":[],\"page\":1,\"size\":20,\"total\":0}");

        var partners = send("GET", ROOT + "/responsible-partners", "client-onboarding-responsible-partners", null);
        var list = send("GET", ROOT + "/cases?responsiblePartnerId=9001&jurisdictionCode=SA", "client-onboarding-o01", null);

        assertOk(partners);
        JsonNode item = JSON.readTree(partners.body()).path("items").get(0);
        assertEquals("Our PayFac", item.path("legalName").asString());
        assertFalse(item.has("providedEntityCount"));
        assertEquals("status=ACTIVE&role=OE_PROVIDER&page=1&size=100", owners.requests().get(0).query());
        assertOk(list);
        assertEquals("responsiblePartnerId=9001&jurisdictionCode=SA", owners.requests().get(1).query());
        assertEquals(400, send("GET", ROOT + "/cases?jurisdictionCode=sa", "client-onboarding-o01", null).statusCode());
        assertEquals(400, send("GET", ROOT + "/cases?responsiblePartnerId=00", "client-onboarding-o01", null).statusCode());
        assertOk(send("GET", ROOT + "/cases?responsiblePartnerId=0", "client-onboarding-o01", null));
        assertEquals("responsiblePartnerId=0", owners.requests().get(2).query(), "0 表示平台自营，原样转发");
        assertEquals(3, owners.requests().size());
        assertTrustedCalls();
    }

    private static void assertBadRequest(HttpResponse<String> response) {
        assertTrue(response.statusCode() == 400 || response.statusCode() == 422, response.statusCode() + response.body());
    }

    /** Platform 文件接口的调用记录。 */
    private List<PlatformOperationsSimulator.RecordedRequest> fileCalls(String path) {
        return platform.requests().stream().filter(request -> request.path().equals(path)).toList();
    }

    private void detailOwners() {
        owners.reply("GET", ROOT + "/cases/" + CASE, 200, RISK_CASE);
        owners.reply("GET", ROOT + "/cases/" + CASE + "/application", 200, APPLICATION);
        owners.reply("GET", ROOT + "/cases/" + CASE + "/classifications", 200, CLASSIFICATION);
        platform.groupedResponses(Map.of(INDUSTRIES_PATH, INDUSTRIES, MCCS_PATH, MCCS));
    }

    /** 指定路径的唯一一次 Platform 参考数据调用。 */
    private PlatformOperationsSimulator.RecordedRequest platformCall(String path) {
        var calls = platform.requests().stream().filter(request -> request.path().equals(path)).toList();
        assertEquals(1, calls.size(), platform.requests().toString());
        return calls.getFirst();
    }

    /** 解析 Query 为键值。 */
    private static Map<String, String> query(String raw) {
        var result = new java.util.HashMap<String, String>();
        if (raw == null || raw.isEmpty()) {
            return result;
        }
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8),
                java.net.URLDecoder.decode(parts.length > 1 ? parts[1] : "", java.nio.charset.StandardCharsets.UTF_8));
        }
        return result;
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
