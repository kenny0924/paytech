package com.paytech.client.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;

import com.paytech.client.console.bff.ClientConsoleBffApplication;
import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.sun.net.httpserver.*;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.*;
import org.springframework.test.context.*;

/** 真 BFF HTTP 链路消费三方协议模拟器；不冒充 Owner 持久化或生产联调。 */
@ActiveProfiles({ "auto", "onboarding-auto" })
@SpringBootTest(classes = ClientConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientOnboardingHttpAutoTest {

    private static final String KEY = "78d8c036-c414-4311-8715-d7c601a0ce48";
    private static final String ORIGIN = "https://client-console.local.test";
    private static final String FILES = "/platform-support/internal/files/v1";
    private static final tools.jackson.databind.json.JsonMapper JSON = tools.jackson.databind.json.JsonMapper.builder().build();
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final AtomicInteger CONTEXT = new AtomicInteger();
    private static final HttpServer OWNER = startOwner();
    private static volatile String mode = "ok";
    private static volatile boolean associated = true;
    /** 文件 40 的上传人与来源系统；文件 42 固定属于他人且不在任何申请中。 */
    private static volatile String fileUploader = "1";
    private static volatile String fileSource = "client-console-bff";

    private record Call(
        String method,
        String path,
        String query,
        String body,
        String key,
        String caller,
        String operator,
        String account,
        String session,
        String requestId
    ) {}

    @LocalServerPort
    int port;

    @LocalManagementPort
    int managementPort;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** 三 Owner 只替换传输地址，不替换 Controller、Processor 或适配逻辑。 */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + OWNER.getAddress().getPort();
        registry.add("client.platform-test-base-url", () -> base + "/platform-support");
        registry.add("client.cct-test-base-url", () -> base);
        registry.add("client.risk-test-base-url", () -> base);
    }

    @BeforeEach
    void prepare() {
        CALLS.clear();
        CONTEXT.set(0);
        mode = "ok";
        associated = true;
        fileUploader = "1";
        fileSource = "client-console-bff";
    }

    @AfterAll
    static void stopOwner() {
        OWNER.stop(0);
    }

    @Test
    void missingIdentityIsRejectedBeforeOwners() throws Exception {
        var result = http.send(
            HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/client-onboarding/v1/start")).build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(401, result.statusCode());
        assertTrue(CALLS.isEmpty());
    }

    @Test
    void startQueriesTwiceValidatedSessionWithoutCreating() throws Exception {
        var result = send("GET", "/client-onboarding/v1/start", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"activeApplicationId\":\"10\""));
        assertEquals(2, CONTEXT.get());
        assertTrue(CALLS.stream().allMatch(call -> call.method().equals("GET")));
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
    }

    @Test
    void aggregatesMainAndRelatedRequirementsAndDeduplicatesFileRead() throws Exception {
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"subjectPartyId\":\"30\""));
        assertTrue(result.body().contains("fixture.pdf"));
        assertTrue(result.body().contains("\"fileId\":\"40\",\"fileAddress\":\"/files/v1/40\""), result.body());
        assertFalse(result.body().contains("contentHash"));
        assertFalse(result.body().contains("programVersionId"));
        assertFalse(result.body().contains("https://storage"));
        var rules = CALLS.stream()
            .filter(call -> call.path().contains("material-requirements"))
            .toList();
        assertEquals(2, rules.size());
        assertTrue(rules.stream().allMatch(call -> "client-console-bff".equals(call.caller())));
        assertTrue(rules.stream().noneMatch(call -> call.query().contains("countryCode")));
        assertTrue(rules.stream().anyMatch(call -> call.query().equals("qualificationType=ORGANIZATION_KYB")));
        assertTrue(rules.stream().anyMatch(call -> call.query().equals("qualificationType=RELATED_PERSON_KYC")));
        assertEquals(
            1,
            CALLS.stream()
                .filter(call -> call.path().endsWith("/files/40"))
                .count()
        );
        for (var call : CALLS) {
            assertNotNull(call.requestId(), call.path());
        }
    }

    @Test
    void unavailableDependenciesKeepOriginalReferencesAndFailClosed() throws Exception {
        mode = "unavailable";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"documentId\":\"50\""));
        assertTrue(result.body().contains("\"requirementsStatus\":\"UNAVAILABLE\""));
        assertTrue(result.body().contains("\"fileStatus\":\"UNAVAILABLE\""));
        assertFalse(result.body().contains("DOWNLOAD"));
    }

    /** 退回：从 Risk 读取客户可见的退回分区与说明，只用申请 ID 和固定调用方。 */
    @Test
    void returnedApplicationShowsPublicReviewResult() throws Exception {
        mode = "returned";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"reviewStatus\":\"AVAILABLE\""), result.body());
        assertTrue(result.body().contains("\"returnSection\":\"BUSINESS\""));
        assertTrue(result.body().contains("\"publicNote\":\"请补充经营地址\""));
        assertFalse(result.body().contains("caseId"));
        var call = CALLS.stream().filter(c -> c.path().endsWith("/result")).toList();
        assertEquals(1, call.size());
        assertEquals("/client-onboarding-review/v1/applications/10/result", call.getFirst().path());
        assertEquals("client-console-bff", call.getFirst().caller());
    }

    /** 通过：结果可读，无退回分区。 */
    @Test
    void approvedApplicationShowsOutcome() throws Exception {
        mode = "approved";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"outcome\":\"APPROVED\""), result.body());
        assertTrue(result.body().contains("\"returnSection\":null"));
    }

    /** Risk 结果与 CCT 状态不一致或 Risk 不可用：结果标记不可用，不伪造，表单照常返回。 */
    @Test
    void reviewResultUnavailableIsExplicit() throws Exception {
        for (String scenario : List.of("review-mismatch", "review-down")) {
            mode = scenario;
            var result = send("GET", "/client-onboarding/v1/applications/10", null);
            assertEquals(200, result.statusCode(), result.body());
            assertTrue(result.body().contains("\"reviewStatus\":\"UNAVAILABLE\""), scenario + result.body());
            assertTrue(result.body().contains("\"review\":null"));
            assertTrue(result.body().contains("\"documentId\":\"50\""));
        }
    }

    /** 草稿不读取审核结果。 */
    @Test
    void draftDoesNotReadReviewResult() throws Exception {
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"reviewStatus\":\"NONE\""));
        assertTrue(CALLS.stream().noneMatch(c -> c.path().endsWith("/result")));
    }

    /** 材料按资格类型通用：主体未填国家时仍读取材料要求，不出现国家前置状态。 */
    @Test
    void missingCountryStillLoadsGenericRequirements() throws Exception {
        mode = "no-country";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertFalse(result.body().contains("COUNTRY_REQUIRED"));
        assertFalse(result.body().contains("countryCode"));
        assertTrue(result.body().contains("\"requirementsStatus\":\"AVAILABLE\""));
        assertEquals(2, CALLS.stream().filter(call -> call.path().contains("material-requirements")).count());
    }

    /** 同一资格类型的多个关联人员只查询一次通用材料要求。 */
    @Test
    void relatedPeopleShareOneRequirementQuery() throws Exception {
        mode = "two-people";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"subjectPartyId\":\"33\""), result.body());
        assertEquals(1, CALLS.stream().filter(call -> call.path().contains("material-requirements")
            && call.query().equals("qualificationType=RELATED_PERSON_KYC")).count());
    }

    @Test
    void unknownFileStateCannotEnableDownload() throws Exception {
        mode = "unknown-file";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"fileStatus\":\"UNAVAILABLE\""));
        assertFalse(result.body().contains("DOWNLOAD"));
    }

    @Test
    void sessionRevokedDuringFanoutDiscardsPage() throws Exception {
        mode = "revoke";
        var result = send("GET", "/client-onboarding/v1/applications/10", null);
        assertEquals(401, result.statusCode(), result.body());
        assertFalse(result.body().contains("fixture.pdf"));
    }

    @Test
    void commandsPreserveOriginalIdentityAndNeverCallOtherOwners() throws Exception {
        associated = false;
        String body =
            "{\"subjectPartyId\":\"30\",\"requirementCode\":\"REGISTRATION\",\"documentType\":\"REGISTRATION_CERTIFICATE\",\"fileAddress\":\"/files/v1/40\"}";
        for (int i = 0; i < 2; i++) {
            var result = send("POST", "/client-onboarding/v1/applications/10/documents", body);
            assertEquals(i == 0 ? 201 : 200, result.statusCode(), result.body());
        }
        assertEquals(2, CALLS.size());
        for (var call : CALLS) {
            assertNull(call.key(), "请求幂等归 Gateway，BFF 不转发 Idempotency-Key");
            assertEquals("1", call.account());
            assertEquals("2", call.session());
            assertEquals(body, call.body());
            assertEquals("client-console-bff", call.caller());
        }
        assertEquals(204, send("DELETE", "/client-onboarding/v1/applications/10/documents/50", null).statusCode());
        assertFalse(associated);
    }

    @Test
    void emptyFileCommandsStayEmptyAndDownloadNeedsNoIdempotencyKey() throws Exception {
        assertEquals(200, send("POST", "/onboarding-files/v1/uploads/60/completion", null).statusCode());
        assertEquals(200, send("POST", "/onboarding-files/v1/files/40/download", null).statusCode());
        assertTrue(CALLS.stream().allMatch(call -> call.body().isEmpty()));
        assertNull(CALLS.getLast().key());
    }

    /** 开始上传只声明文件本身；BFF 固定用途与上传人，以调用身份 client-console-bff 请求 Platform 内部文件接口。 */
    @Test
    void test_startUploadFixesReasonAndUploader() throws Exception {
        var result = send("POST", "/onboarding-files/v1/uploads",
            "{\"filename\":\"fixture.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":\"4\"}");

        assertEquals(201, result.statusCode(), result.body());
        var body = JSON.readTree(result.body());
        assertEquals("60", body.path("uploadId").asString());
        assertEquals("PUT", body.path("authorization").path("method").asString());
        var start = CALLS.stream().filter(call -> call.path().equals(FILES + "/uploads")).toList();
        assertEquals(1, start.size());
        var sent = JSON.readTree(start.getFirst().body());
        assertEquals("CLIENT_ONBOARDING_MATERIAL", sent.path("reasonCode").asString());
        assertEquals("1", sent.path("uploaderAccountId").asString());
        assertEquals("fixture.pdf", sent.path("filename").asString());
        assertTrue(sent.path("applicationId").isMissingNode());
        assertTrue(CALLS.stream().filter(call -> call.path().startsWith("/platform-support/"))
            .allMatch(call -> "client-console-bff".equals(call.caller())));
    }

    /** 旧页面仍提交申请与主体：入口按未知字段拒绝，不访问任何 Owner。 */
    @Test
    void test_startUploadRejectsApplicationTarget() throws Exception {
        var result = send("POST", "/onboarding-files/v1/uploads",
            "{\"applicationId\":\"10\",\"subjectPartyId\":\"30\",\"filename\":\"fixture.pdf\",\"mimeType\":\"application/pdf\",\"sizeBytes\":\"4\"}");

        assertEquals(400, result.statusCode(), result.body());
        assertTrue(CALLS.isEmpty());
    }

    /** 完成上传：本人上传返回文件地址；他人上传按文件不存在拒绝且不返回地址。 */
    @Test
    void test_completionRequiresOwnUpload() throws Exception {
        var own = send("POST", "/onboarding-files/v1/uploads/60/completion", null);
        assertEquals(200, own.statusCode(), own.body());
        var body = JSON.readTree(own.body());
        assertEquals("60", body.path("commandId").asString());
        assertEquals("40", body.path("fileId").asString());
        assertEquals("/files/v1/40", body.path("fileAddress").asString());

        fileUploader = "9";
        var foreign = send("POST", "/onboarding-files/v1/uploads/60/completion", null);
        assertProblem(foreign, 404, FileErrorCode.FILE_NOT_FOUND.getCode());
        assertFalse(foreign.body().contains("/files/v1/40"));
    }

    /** 本人上传、尚未关联的文件可查可下载，不需要查询 CCT 申请；客户下载不带操作人。 */
    @Test
    void test_ownUploadCanReadAndDownload() throws Exception {
        var read = send("GET", "/onboarding-files/v1/files/40", null);
        assertEquals(200, read.statusCode(), read.body());
        var file = JSON.readTree(read.body());
        assertEquals("/files/v1/40", file.path("fileAddress").asString());
        assertEquals("DOWNLOAD", file.path("allowedActions").get(0).asString());
        assertTrue(file.path("contentHash").isMissingNode());
        assertTrue(file.path("uploaderAccountId").isMissingNode());

        var download = send("POST", "/onboarding-files/v1/files/40/download", null);
        assertEquals(200, download.statusCode(), download.body());
        assertTrue(download.body().contains("https://storage.example.test/object"));
        var grant = CALLS.stream().filter(call -> call.path().equals(FILES + "/files/40/download")).toList();
        assertEquals(1, grant.size());
        assertNull(grant.getFirst().operator());
        assertNull(grant.getFirst().query(), "不指定下载方式，按 Platform 默认附件下载");
        assertTrue(CALLS.stream().noneMatch(call -> call.path().startsWith("/client-onboarding/")));
    }

    /** 他人上传、但地址已关联在本人申请中的材料可读、可下载。 */
    @Test
    void test_linkedMaterialIsReadable() throws Exception {
        fileUploader = "9";
        fileSource = "platform-support";

        assertEquals(200, send("GET", "/onboarding-files/v1/files/40", null).statusCode());
        assertEquals(200, send("POST", "/onboarding-files/v1/files/40/download", null).statusCode());
        assertTrue(CALLS.stream().anyMatch(call -> call.path().endsWith("/start")));
        assertTrue(CALLS.stream().anyMatch(call -> call.path().endsWith("/applications/10")));
    }

    /** 他人文件既非本人上传也不在本人申请中：查与下载都按文件不存在拒绝，不签发地址。 */
    @Test
    void test_foreignFileIsNotFound() throws Exception {
        assertProblem(send("GET", "/onboarding-files/v1/files/42", null), 404,
            FileErrorCode.FILE_NOT_FOUND.getCode());
        assertProblem(send("POST", "/onboarding-files/v1/files/42/download", null), 404,
            FileErrorCode.FILE_NOT_FOUND.getCode());
        assertTrue(CALLS.stream().noneMatch(call -> call.path().endsWith("/download")));
    }

    /** Platform 的文件错误与不可用原样到达浏览器；会话失效时不查询文件。 */
    @Test
    void test_platformFailuresReachBrowser() throws Exception {
        mode = "file-missing";
        assertProblem(send("GET", "/onboarding-files/v1/files/40", null), 404,
            FileErrorCode.FILE_NOT_FOUND.getCode());
        mode = "download-conflict";
        assertProblem(send("POST", "/onboarding-files/v1/files/40/download", null), 409,
            FileErrorCode.FILE_STATE_CONFLICT.getCode());
        mode = "unavailable";
        assertEquals(503, send("GET", "/onboarding-files/v1/files/40", null).statusCode());
        CALLS.clear();
        mode = "session-invalid";
        assertEquals(401, send("GET", "/onboarding-files/v1/files/40", null).statusCode());
        assertTrue(CALLS.stream().noneMatch(call -> call.path().startsWith(FILES)));
    }

    @Test
    void cctSaveAndPeopleCommandsForwardOnlyApprovedBody() throws Exception {
        assertEquals(
            200,
            send(
                "PATCH",
                "/client-onboarding/v1/applications/10/draft",
                "{\"dirtyFields\":[\"organization.legalName\"],\"values\":{\"organization\":{\"legalName\":\"Fixture\"}}}"
            ).statusCode()
        );
        assertEquals(
            201,
            send(
                "POST",
                "/client-onboarding/v1/applications/10/people",
                "{\"person\":{\"dirtyFields\":[\"individual.givenName\"],\"values\":{\"individual\":{\"givenName\":\"Fixture\"}}},\"relation\":{\"relationType\":\"DIRECTOR\",\"ownershipPercentage\":null,\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":null}}"
            ).statusCode()
        );
        assertEquals(2, CALLS.size());
        assertTrue(CALLS.stream().allMatch(call -> call.path().startsWith("/client-onboarding/")));
    }

    /** CCT 422 INVALID_DRAFT 的字段清单经 BFF 原样保留；Request ID 为浏览器请求的值，不泄露上游 ID 与 detail。 */
    @Test
    void invalidDraftFieldErrorsReachBrowser() throws Exception {
        mode = "draft-invalid";
        var result = send(
            "PATCH",
            "/client-onboarding/v1/applications/10/draft",
            "{\"dirtyFields\":[\"contact.phone\",\"individual.birthDate\"],\"values\":{\"contact\":{\"phone\":\"13800138000\"},\"individual\":{\"birthDate\":\"1990/05/01\"}}}"
        );
        assertEquals(422, result.statusCode(), result.body());
        assertEquals("application/problem+json", result.headers().firstValue("Content-Type").orElseThrow());
        assertEquals("browser-onboarding-fixture", result.headers().firstValue("X-Request-Id").orElseThrow());
        var body = tools.jackson.databind.json.JsonMapper.builder().build().readTree(result.body());
        assertEquals("INVALID_DRAFT", body.path("code").asString());
        assertEquals("入驻字段不符合约定", body.path("detail").asString());
        assertEquals("browser-onboarding-fixture", body.path("requestId").asString());
        assertEquals(
            "[{\"field\":\"contact.phone\",\"message\":\"手机号须为 E.164 格式，如 +8613800138000\"},"
                + "{\"field\":\"individual.birthDate\",\"message\":\"须为 YYYY-MM-DD 格式的有效日期\"}]",
            body.path("errors").toString()
        );
        assertFalse(result.body().contains("fixture-owner"));
        assertFalse(result.body().contains("Owner detail"));
        assertEquals(1, CALLS.size());
    }

    @Test
    void malformedQueryIdentifiersAndEmptyCommandsNeverReachOwner() throws Exception {
        for (String path : List.of(
            "/client-onboarding/v1/start?create=true",
            "/client-onboarding/v1/applications/01",
            "/client-onboarding/v1/applications/9223372036854775808",
            "/client-onboarding/v1/form-options?clientType=INDIVIDUAL&clientType=ORGANIZATION"
        )) {
            assertEquals(400, send("GET", path, null).statusCode(), path);
        }
        assertEquals(400, send("POST", "/onboarding-files/v1/uploads/60/completion", "{}").statusCode());
        assertTrue(CALLS.isEmpty());
    }

    /** 目录只接受固定客户类型；旧国家参数与非法类型都不会查询目录或材料 Owner。 */
    @Test
    void optionsOnlyAllowsFixedClientTypesWithoutCountry() throws Exception {
        var result = send("GET", "/client-onboarding/v1/form-options?clientType=INDIVIDUAL", null);
        assertEquals(200, result.statusCode(), result.body());
        assertFalse(result.body().contains("COUNTRY_REQUIRED"));
        assertFalse(result.body().contains("countryCode"));
        assertTrue(result.body().contains("\"requirementsStatus\":\"AVAILABLE\""));
        assertTrue(CALLS.stream().anyMatch(call -> call.path().endsWith("/material-requirements")
            && call.query().equals("qualificationType=INDIVIDUAL_KYC")));
        CALLS.clear();
        assertEquals(400, send("GET", "/client-onboarding/v1/form-options?clientType=INDIVIDUAL&countryCode=ZZ", null).statusCode());
        assertEquals(400, send("GET", "/client-onboarding/v1/form-options?clientType=PARTNER", null).statusCode());
        assertTrue(CALLS.stream().noneMatch(call -> call.path().endsWith("/form-options") || call.path().endsWith("/material-requirements")));
    }

    /** CCT 目录与 Risk 要求分别聚合，单边失败不能吞掉另一边事实。 */
    @Test void optionsExposeControlledCatalogAndPreserveIndependentFailures() throws Exception {
        var available=send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL",null);
        assertEquals(200,available.statusCode());
        assertTrue(available.body().contains("SYNTHETIC_PERSON_ID"),available.body());
        assertTrue(available.body().contains("\"catalogStatus\":\"AVAILABLE\""));
        var catalogCall=CALLS.stream().filter(call -> call.path().endsWith("/form-options")).findFirst().orElseThrow();
        assertEquals("client-console-bff",catalogCall.caller());
        mode="catalog-unavailable";
        var missing=send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL",null);
        assertEquals(200,missing.statusCode());
        assertTrue(missing.body().contains("\"catalogStatus\":\"UNAVAILABLE\""));
        assertTrue(missing.body().contains("\"requirementsStatus\":\"AVAILABLE\""));
        mode="unavailable";
        var riskMissing=send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL",null);
        assertEquals(200,riskMissing.statusCode());
        assertTrue(riskMissing.body().contains("\"catalogStatus\":\"AVAILABLE\""));
        assertTrue(riskMissing.body().contains("\"requirementsStatus\":\"UNAVAILABLE\""));
    }
    /** 未识别的 Owner 状态不开放目录；聚合后撤销会话不能返回数据。 */
    @Test void optionsFailClosedForUnknownCatalogAndLateRevocation() throws Exception {
        mode="unknown-catalog";
        var result=send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL",null);
        assertEquals(200,result.statusCode());
        assertTrue(result.body().contains("\"catalogStatus\":\"UNAVAILABLE\""));
        mode="unknown-mode";
        var unknownMode=send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL",null);
        assertEquals(200,unknownMode.statusCode());
        assertTrue(unknownMode.body().contains("\"catalogStatus\":\"UNAVAILABLE\""));
        assertFalse(unknownMode.body().contains("SYNTHETIC_PERSON_ID"));
        CONTEXT.set(0);mode="revoke";
        assertEquals(401,send("GET","/client-onboarding/v1/form-options?clientType=INDIVIDUAL",null).statusCode());
    }

    /** C14 不要求 Idempotency-Key，原样转发 CCT 的 202/200，且不把请求键传给 Owner。 */
    @Test
    void submissionForwardsWithoutIdempotencyKey() throws Exception {
        var created = submit(false, null);
        assertEquals(202, created.statusCode(), created.body());
        assertTrue(created.body().contains("\"status\":\"SUBMITTED\""));
        assertEquals("no-store", created.headers().firstValue("Cache-Control").orElseThrow());
        var call = CALLS.stream().filter(c -> c.path().endsWith("/submissions")).findFirst().orElseThrow();
        assertEquals("client-console-bff", call.caller());
        assertEquals("1", call.account());
        assertEquals("2", call.session());
        assertNull(call.key());
        assertEquals("", call.body());
        mode = "submit-existing";
        var existing = submit(false, null);
        assertEquals(200, existing.statusCode(), existing.body());
        assertTrue(existing.body().contains("\"status\":\"UNDER_REVIEW\""));
    }

    /** 不完整保持 422 SUBMISSION_INCOMPLETE、身份冲突保持 409；逐项问题由核对查询原样返回。 */
    @Test
    void submissionKeepsOwnerErrorsAndCheckListsIssues() throws Exception {
        mode = "submit-incomplete";
        var incomplete = submit(false, null);
        assertEquals(422, incomplete.statusCode(), incomplete.body());
        assertTrue(incomplete.body().contains("\"code\":\"SUBMISSION_INCOMPLETE\""));
        mode = "submit-conflict";
        var conflict = submit(false, null);
        assertEquals(409, conflict.statusCode(), conflict.body());
        assertTrue(conflict.body().contains("\"code\":\"IDENTITY_CONFLICT\""));
        var check = send("GET", "/client-onboarding/v1/applications/10/submission-check", null);
        assertEquals(200, check.statusCode(), check.body());
        assertTrue(check.body().contains("\"ready\":false"));
        assertTrue(check.body().contains("\"field\":\"materials[30].REGISTRATION\""));
        assertEquals("no-store", check.headers().firstValue("Cache-Control").orElseThrow());
    }

    /** C14 Body 必须为空；带 Body 的请求不会到达 CCT。 */
    @Test
    void submissionRejectsBody() throws Exception {
        assertEquals(400, submit(false, "{}").statusCode());
        assertTrue(CALLS.stream().noneMatch(c -> c.path().endsWith("/submissions")));
    }

    @Test
    void managementListenerDoesNotExposeOnboarding() throws Exception {
        var result = http.send(
            HttpRequest.newBuilder(
                URI.create("http://127.0.0.1:" + managementPort + "/client-onboarding/v1/start")
            ).build(),
            HttpResponse.BodyHandlers.ofString()
        );
        assertEquals(404, result.statusCode());
        assertTrue(CALLS.isEmpty());
    }

    /** 断言浏览器收到的 Problem 状态与业务 Code。 */
    private static void assertProblem(HttpResponse<String> result, int status, String code) {
        assertEquals(status, result.statusCode(), result.body());
        assertEquals(code, JSON.readTree(result.body()).path("code").asString(), result.body());
    }

    /** 经 Gateway 唯一重建的测试身份；不使用真实凭据。 */
    /** 发送 C14，可选择是否附带 Idempotency-Key。 */
    private HttpResponse<String> submit(boolean withKey, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/client-onboarding/v1/applications/10/submissions"))
            .header("X-Gateway-Realm", "CUSTOMER")
            .header("X-Gateway-Channel", "CLIENT")
            .header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Context-Type", "ACCOUNT")
            .header("X-Gateway-Actor-Subject", "1")
            .header("X-Gateway-Session-Id", "2")
            .header("Origin", ORIGIN)
            .header("X-Request-Id", "browser-onboarding-fixture");
        if (withKey) {
            builder.header("Idempotency-Key", KEY);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return http.send(builder.POST(body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("X-Gateway-Realm", "CUSTOMER")
            .header("X-Gateway-Channel", "CLIENT")
            .header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Context-Type", "ACCOUNT")
            .header("X-Gateway-Actor-Subject", "1")
            .header("X-Gateway-Session-Id", "2")
            .header("Origin", ORIGIN)
            .header("X-Request-Id", "browser-onboarding-fixture");
        if (!method.equals("GET") && !path.endsWith("/download")) {
            builder.header("Idempotency-Key", KEY);
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return http.send(
            builder
                .method(
                    method,
                    body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body)
                )
                .build(),
            HttpResponse.BodyHandlers.ofString()
        );
    }

    /** 协议模拟器保留关联状态与调用记录，驱动重放及部分失败场景。 */
    private static HttpServer startOwner() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", ClientOnboardingHttpAutoTest::respond);
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** 三 Owner 只返回各自合同事实，不预组装 BFF 页面。 */
    private static void respond(HttpExchange exchange) throws java.io.IOException {
        var h = exchange.getRequestHeaders();
        String path = exchange.getRequestURI().getPath(),
            method = exchange.getRequestMethod();
        CALLS.add(
            new Call(
                method,
                path,
                exchange.getRequestURI().getQuery(),
                new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
                h.getFirst("Idempotency-Key"),
                h.getFirst("X-Caller-Service"),
                h.getFirst("X-Operator-Account-Id"),
                h.getFirst("X-Gateway-Actor-Subject"),
                h.getFirst("X-Gateway-Session-Id"),
                h.getFirst("X-Request-Id")
            )
        );
        int status = 200;
        String body;
        if (path.endsWith("/context")) {
            if (CONTEXT.incrementAndGet() > 1 && mode.equals("revoke") || mode.equals("session-invalid")) {
                failure(exchange, 401);
                return;
            }
            body =
                "{\"accountId\":\"1\",\"sessionId\":\"2\",\"emailMasked\":\"f***@example.test\",\"emailVerified\":true,\"contextType\":\"ACCOUNT\",\"tenantId\":null,\"memberId\":null,\"allowedActions\":[\"ONBOARDING\"]}";
        } else if (path.endsWith("/start")) {
            body =
                "{\"applications\":[{\"applicationId\":\"10\",\"applicationNo\":\"FIXTURE\",\"clientType\":\"ORGANIZATION\",\"status\":\"DRAFT\",\"active\":true,\"savedAt\":\"2026-09-16T00:00:00.000000Z\"}],\"hasMore\":false}";
        } else if (path.endsWith("/submissions") && method.equals("POST")) {
            exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
            if (mode.equals("submit-incomplete")) {
                write(exchange, 422, "{\"type\":\"about:blank\",\"title\":\"Unprocessable Content\",\"detail\":\"资料或材料尚不完整，请按提交核对结果补全\",\"code\":\"SUBMISSION_INCOMPLETE\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-09-16T00:00:00.000000Z\"}", "application/problem+json");
                return;
            }
            if (mode.equals("submit-conflict")) {
                write(exchange, 409, "{\"type\":\"about:blank\",\"title\":\"Conflict\",\"detail\":\"证件号码已被其他主体使用，请核对后联系客服\",\"code\":\"IDENTITY_CONFLICT\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-09-16T00:00:00.000000Z\"}", "application/problem+json");
                return;
            }
            status = mode.equals("submit-existing") ? 200 : 202;
            body = "{\"applicationId\":\"10\",\"submissionId\":\"6b4df4d5-e910-49eb-9695-3d083a3c4bbd\",\"status\":\""
                + (mode.equals("submit-existing") ? "UNDER_REVIEW" : "SUBMITTED") + "\"}";
        } else if (path.endsWith("/submission-check")) {
            body = "{\"ready\":false,\"issues\":[{\"field\":\"individual.givenName\",\"message\":\"提交前请填写此项\"},{\"field\":\"materials[30].REGISTRATION\",\"message\":\"请上传基础必需材料\"}]}";
        } else if (path.endsWith("/form-options")) {
            if(mode.equals("catalog-unavailable")) { failure(exchange,503);return; }
            body="{\"clientType\":\"INDIVIDUAL\",\"catalogStatus\":\""+(mode.equals("unknown-catalog")?"FUTURE":"AVAILABLE")+"\",\"organizationTypes\":[],\"businessModels\":[{\"code\":\"SYNTHETIC_SOLE\",\"label\":\"合成模式\"}],\"identifierTypes\":[{\"code\":\"SYNTHETIC_PERSON_ID\",\"label\":\"合成证件\",\"issuingCountryMode\":\""+(mode.equals("unknown-mode")?"ISSUER":"REQUIRED")+"\"}]}";
        } else if (path.endsWith("/material-requirements")) {
            if (mode.equals("unavailable")) {
                failure(exchange, 503);
                return;
            }
            body =
                "{\"programId\":\"70\",\"programVersionId\":\"71\",\"items\":[{\"requirementCode\":\"REGISTRATION\",\"documentType\":\"REGISTRATION_CERTIFICATE\",\"requiredFlag\":true}]}";
        } else if (path.endsWith("/applications/10") && method.equals("GET")) {
            String organization = mode.equals("no-country") ? "{}" : "{\"incorporationCountry\":\"ZZ\"}";
            String individual = mode.equals("no-country") ? "{}" : "{\"residenceCountry\":\"AA\"}";
            body =
                "{\"applicationId\":\"10\",\"applicationNo\":\"FIXTURE\",\"clientId\":\"20\",\"clientType\":\"ORGANIZATION\",\"subjectPartyId\":\"30\",\"status\":\"" + applicationStatus() + "\",\"savedAt\":\"2026-09-16T00:00:00.000000Z\",\"editable\":true,\"values\":{\"organization\":" +
                organization +
                "},\"people\":[{\"partyId\":\"31\",\"editable\":true,\"values\":{\"individual\":" +
                individual +
                "}}"+(mode.equals("two-people") ? ",{\"partyId\":\"33\",\"editable\":true,\"values\":{}}" : "")+"],\"relations\":[{\"relationId\":\"32\",\"relatedPartyId\":\"31\",\"relationType\":\"DIRECTOR\",\"ownershipPercentage\":null,\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":null,\"status\":\"DRAFT\",\"editable\":true}"+(mode.equals("two-people") ? ",{\"relationId\":\"34\",\"relatedPartyId\":\"33\",\"relationType\":\"UBO\",\"ownershipPercentage\":\"30\",\"effectiveFrom\":\"2026-01-01\",\"effectiveTo\":null,\"status\":\"DRAFT\",\"editable\":true}" : "")+"],\"documents\":[{\"documentId\":\"50\",\"subjectPartyId\":\"30\",\"requirementCode\":\"REGISTRATION\",\"documentType\":\"REGISTRATION_CERTIFICATE\",\"fileAddress\":\"/files/v1/40\",\"subjectInScope\":true},{\"documentId\":\"51\",\"subjectPartyId\":\"30\",\"requirementCode\":\"ADDRESS\",\"documentType\":\"ADDRESS_PROOF\",\"fileAddress\":\"/files/v1/40\",\"subjectInScope\":true}]}";
        } else if (path.endsWith("/applications/10/result")) {
            if (mode.equals("review-down")) {
                failure(exchange, 503);
                return;
            }
            String outcome = mode.equals("review-mismatch") ? "null" : "\"" + applicationStatus() + "\"";
            String section = mode.equals("returned") ? "\"BUSINESS\"" : "null";
            body = "{\"applicationId\":\"10\",\"caseId\":\"90\",\"status\":\"" + (mode.equals("review-mismatch") ? "UNDER_REVIEW" : applicationStatus())
                + "\",\"outcome\":" + outcome + ",\"returnSection\":" + section
                + ",\"publicNote\":\"请补充经营地址\",\"decidedAt\":\"2026-10-13T00:00:00.000000Z\"}";
        } else if (path.startsWith(FILES + "/files/") && method.equals("GET")) {
            if (mode.equals("unavailable")) {
                failure(exchange, 503);
                return;
            }
            if (mode.equals("file-missing")) {
                problem(exchange, 404, FileErrorCode.FILE_NOT_FOUND.getCode());
                return;
            }
            String id = path.substring(path.lastIndexOf('/') + 1);
            body = storedFile(id, id.equals("42") ? "9" : fileUploader, id.equals("42") ? "client-console-bff" : fileSource,
                mode.equals("unknown-file") ? "UNKNOWN" : FileScanStatus.AVAILABLE.getCode());
        } else if (path.startsWith(FILES + "/files/") && path.endsWith("/download")) {
            if (mode.equals("download-conflict")) {
                problem(exchange, 409, FileErrorCode.FILE_STATE_CONFLICT.getCode());
                return;
            }
            body = "{\"url\":\"https://storage.example.test/object\",\"expiresAt\":\"2099-01-01T00:00:00Z\",\"disposition\":\"ATTACHMENT\"}";
        } else if (path.equals(FILES + "/uploads/60/completion")) {
            body =
                "{\"uploadId\":\"60\",\"fileId\":\"40\",\"fileAddress\":\"/files/v1/40\",\"completedAt\":\"2026-09-16T00:00:00Z\"}";
        } else if (path.equals(FILES + "/uploads") && method.equals("POST")) {
            status = 201;
            body =
                "{\"uploadId\":\"60\",\"authorization\":{\"method\":\"PUT\",\"url\":\"https://storage.example.test/upload\",\"headers\":{\"Content-Type\":\"application/pdf\"},\"expiresAt\":\"2099-01-01T00:00:00Z\"},\"expiresAt\":\"2099-01-01T00:00:00Z\"}";
        } else if (path.endsWith("/documents") && method.equals("POST")) {
            status = associated ? 200 : 201;
            associated = true;
            body =
                "{\"commandId\":null,\"applicationId\":\"10\",\"documentId\":\"50\",\"subjectPartyId\":\"30\",\"fileAddress\":\"/files/v1/40\",\"operation\":\"ASSOCIATED\",\"completedAt\":\"2026-09-16T00:00:00.000000Z\"}";
        } else if (method.equals("DELETE")) {
            associated = false;
            exchange.sendResponseHeaders(204, -1);
            exchange.close();
            return;
        } else if (path.endsWith("/people")) {
            status = 201;
            body =
                "{\"commandId\":\"80\",\"applicationId\":\"10\",\"partyId\":\"31\",\"relationId\":\"32\",\"operation\":\"PERSON_CREATED\",\"completedAt\":\"2026-09-16T00:00:00.000000Z\"}";
        } else if (path.endsWith("/draft")) {
            if (mode.equals("draft-invalid")) {
                exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
                write(exchange, 422, "{\"type\":\"about:blank\",\"title\":\"Unprocessable Content\",\"detail\":\"Owner detail\",\"code\":\"INVALID_DRAFT\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-09-16T00:00:00.000000Z\",\"errors\":[{\"field\":\"contact.phone\",\"message\":\"手机号须为 E.164 格式，如 +8613800138000\"},{\"field\":\"individual.birthDate\",\"message\":\"须为 YYYY-MM-DD 格式的有效日期\"}]}", "application/problem+json");
                return;
            }
            body =
                "{\"commandId\":\"80\",\"applicationId\":\"10\",\"applicationNo\":\"FIXTURE\",\"clientId\":\"20\",\"clientType\":\"ORGANIZATION\",\"operation\":\"SAVED\",\"completedAt\":\"2026-09-16T00:00:00.000000Z\"}";
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        write(exchange, status, body, "application/json");
    }

    /** CCT 申请状态按场景切换；未指定时为草稿。 */
    private static String applicationStatus() {
        return switch (mode) {
            case "returned", "review-mismatch" -> "RETURNED";
            case "approved" -> "APPROVED";
            case "review-down" -> "REJECTED";
            default -> "DRAFT";
        };
    }

    /** Platform 内部文件接口的元数据合同：带文件地址、来源系统与上传人。 */
    private static String storedFile(String id, String uploader, String source, String scanStatus) {
        return "{\"fileId\":\"" + id + "\",\"fileAddress\":\"/files/v1/" + id + "\",\"sourceSystem\":\"" + source
            + "\",\"reasonCode\":\"CLIENT_ONBOARDING_MATERIAL\",\"displayName\":\"fixture.pdf\",\"mimeType\":\"application/pdf\","
            + "\"sizeBytes\":\"4\",\"contentHash\":\"" + "a".repeat(64) + "\",\"scanStatus\":\"" + scanStatus
            + "\",\"objectStatus\":\"" + FileObjectStatus.ACTIVE.getCode() + "\",\"uploaderAccountId\":\"" + uploader
            + "\",\"createTime\":\"2026-09-16T00:00:00Z\"}";
    }

    /** 带指定业务 Code 的严格 Problem。 */
    private static void problem(HttpExchange exchange, int status, String code) throws java.io.IOException {
        exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
        write(exchange, status, "{\"type\":\"about:blank\",\"title\":\""
            + org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase() + "\",\"detail\":\"Fixture problem\",\"code\":\""
            + code + "\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-09-16T00:00:00.000000Z\"}",
            "application/problem+json");
    }

    /** 严格 Problem 协议，测试业务失败与依赖失败的实际映射。 */
    private static void failure(HttpExchange exchange, int status) throws java.io.IOException {
        exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
        write(
            exchange,
            status,
            "{\"type\":\"about:blank\",\"title\":\"" +
                (status == 401 ? "Unauthorized" : "Service Unavailable") +
                "\",\"detail\":\"Fixture unavailable\",\"code\":\"" +
                (status == 401 ? "CLIENT_SESSION_INVALID" : "SERVICE_UNAVAILABLE") +
                "\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-09-16T00:00:00.000000Z\"}",
            "application/problem+json"
        );
    }

    /** 每次真实 HTTP 响应均按 JSON 媒体类型和实际长度发送。 */
    private static void write(HttpExchange exchange, int status, String body, String type) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
