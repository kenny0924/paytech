package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_DETAIL_CANARY;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.LocalizationUploadPurpose;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.LocalizationCatalogController;
import com.paytech.operations.console.bff.ext.rest.LocalizationContentController;
import com.paytech.operations.console.bff.ext.rest.LocalizationReleaseController;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.LocalizationErrorCode;
import com.paytech.platform.support.api.files.FileAddress;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 维护多语言：Platform 管理接口 23 条转发与导入文件上传 2 条（Platform 内部文件接口）
 * <li>Platform 由 Loopback Simulator 承担，PLT-I02 授权与 Owner 调用记录在同一 Simulator。</li>
 * <li>覆盖转发契约（操作人、Query、Body 与状态码）、授权拒绝、本地边界拒绝、多语言错误码与 errors 透传、字段路径投影、
 * 回执核对、依赖故障、上传原因码与上传授权不进入日志。</li>
 *
 * @see LocalizationCatalogController
 * @see LocalizationContentController
 * @see LocalizationReleaseController
 * @author Claude
 */
@ExtendWith(OutputCaptureExtension.class)
class LocalizationBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final String BOUNDARY = "/localization-boundary.json";
    private static final String REQUEST_ID = "localization-boundary-request";
    private static final String OWNER_REQUEST_ID = "platform-localization-request";
    private static final String ACTOR = PlatformOperationsSimulator.RISK_ACCOUNT_ID;
    private static final String UPLOAD_PATH = "/internal/files/v1/uploads";
    private static final String UPLOAD_ID = "7409912387001";
    private static final String FILE_ID = "7409912387650";
    private static final String PUT_URL_CANARY = "https://storage.local.test/i18n?X-Amz-Signature=PUT-URL-CANARY";
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    private PlatformOperationsSimulator platform;

    @LocalServerPort
    private int port;

    /** 每例从空 Platform 配置与默认授权开始。 */
    @BeforeEach
    void setUpLocalizationMocks() {
        platform.reset();
        platform.useCommercialIdentity();
    }

    /** Platform 不得收到未配置的请求。 */
    @AfterEach
    void verifyPlatformCalls() {
        assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
    }

    static Stream<JsonNode> endpoints() {
        try (var input = LocalizationBrowserAutoTest.class.getResourceAsStream(BOUNDARY)) {
            var rows = new ArrayList<JsonNode>();
            JSON.readTree(input).forEach(rows::add);
            return rows.stream();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("无法读取边界清单 " + BOUNDARY, failure);
        }
    }

    private static JsonNode row(String id) {
        return endpoints().filter(row -> id.equals(row.path("id").asString())).findFirst().orElseThrow();
    }

    /** 23 个管理接口全部登记在边界清单中，路径为 Platform 路径去掉 /internal/localization/v1。 */
    @Test
    void test_boundaryCoversAllPlatformEndpoints() {
        var routes = endpoints().map(row -> row.path("method").asString() + " " + row.path("ownerPath").asString())
            .distinct().toList();
        assertEquals(23, routes.size(), routes.toString());
        endpoints().forEach(row -> assertEquals(row.path("ownerPath").asString(),
            "/internal/localization/v1" + row.path("path").asString().substring("/localization-admin/v1".length())));
    }

    /** 方法、路径、Query、Body 与操作人 Header 准确转发一次，响应原样返回；浏览器凭据与幂等键不转发。 */
    @ParameterizedTest(name = "{index} 多语言转发 {0}")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        String body = row.path("response").isNull() ? "" : JSON.writeValueAsString(row.path("response"));
        platform.rawResponse(row.path("ownerPath").asString(), 200, "application/json", body);

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        if (row.path("response").isNull()) {
            assertTrue(response.body().isEmpty(), response.body());
        } else {
            assertEquals(row.path("response"), JSON.readTree(response.body()));
        }
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        var call = ownerCall();
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertEquals(row.path("ownerPath").asString(), call.path()),
            () -> assertEquals(query(row.path("ownerQuery").asString()), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals(ACTOR, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")),
            () -> assertEquals("operations-console-bff", call.header("X-Caller-Service")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (row.path("request").isNull()) {
            assertTrue(call.body().isEmpty(), "无 Body 的请求不得转发 Body");
        } else {
            assertEquals(row.path("request"), JSON.readTree(call.body()));
        }
    }

    /** 当前会话授权失败时只调用 PLT-I02，不访问多语言接口。 */
    @ParameterizedTest(name = "{index} 授权拒绝 {0}")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        var response = send(row);

        assertEquals(403, response.statusCode(), response.body());
        assertOnlyAuthorized();
    }

    /** 覆盖率不足：422 与 Code 原样，errors 的缺口与总数（含 Platform 文案）完整到达页面。 */
    @Test
    void test_coverageInsufficientKeepsGapList() throws Exception {
        var publish = row("R02");
        platform.rawResponse(publish.path("ownerPath").asString(), 422, "application/problem+json",
            problem(422, LocalizationErrorCode.I18N_COVERAGE_INSUFFICIENT.getCode(), "zh-CN 覆盖率 98%，低于 100%", """
                [{"field":"missing.zh-CN","message":"team.invite.title"},{"field":"missingTotal","message":"2"}]"""),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(publish);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(LocalizationErrorCode.I18N_COVERAGE_INSUFFICIENT.getCode(), body.path("code").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertEquals(List.of("missing.zh-CN", "missingTotal"),
                body.path("errors").valueStream().map(error -> error.path("field").asString()).toList()),
            () -> assertEquals(List.of("team.invite.title", "2"),
                body.path("errors").valueStream().map(error -> error.path("message").asString()).toList()));
        ownerCall();
    }

    /** 未创建的命名空间：422 I18N_NAMESPACE_NOT_FOUND，errors 列出缺失命名空间。 */
    @Test
    void test_namespaceNotFoundKeepsMissingNamespaces() throws Exception {
        var confirm = row("T02");
        platform.rawResponse(confirm.path("ownerPath").asString(), 422, "application/problem+json",
            problem(422, LocalizationErrorCode.I18N_NAMESPACE_NOT_FOUND.getCode(), "导入文件包含尚未创建的命名空间", """
                [{"field":"namespaces.billing","message":"命名空间不存在"}]"""),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(confirm);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(LocalizationErrorCode.I18N_NAMESPACE_NOT_FOUND.getCode(), body.path("code").asString()),
            () -> assertEquals("namespaces.billing", body.path("errors").get(0).path("field").asString()));
    }

    /** ICU 文案不合法：保存与翻译导入的 422 I18N_MESSAGE_INVALID 原样到达页面，errors 的字段与带字符位置的原因完整保留。 */
    @ParameterizedTest
    @CsvSource({"T04,translations[0].text", "T06,namespaces.team.team.invite.title"})
    void test_messageInvalidKeepsIcuReasons(String id, String field) throws Exception {
        var row = row(id);
        String reason = "第 8 个字符：plural 缺少 other 分支";
        platform.rawResponse(row.path("ownerPath").asString(), 422, "application/problem+json",
            problem(422, LocalizationErrorCode.I18N_MESSAGE_INVALID.getCode(), "1 条翻译未通过 ICU 校验",
                JSON.writeValueAsString(JSON.createArrayNode().add(JSON.createObjectNode().put("field", field)
                    .put("message", reason)))),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(LocalizationErrorCode.I18N_MESSAGE_INVALID.getCode(), body.path("code").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertEquals(1, body.path("errors").size()),
            () -> assertEquals(field, body.path("errors").get(0).path("field").asString()),
            () -> assertEquals(reason, body.path("errors").get(0).path("message").asString()),
            () -> assertFalse(response.body().contains(OWNER_REQUEST_ID)));
        ownerCall();
    }

    /** 导入预览过期与文案已变：409 与多语言 Code 原样。 */
    @Test
    void test_conflictCodesPassThrough() throws Exception {
        assertCodedProblem(row("T02"), 409, LocalizationErrorCode.I18N_IMPORT_PREVIEW_STALE.getCode(), "导入预览已过期，请重新预览");
        resetBetweenCalls();
        assertCodedProblem(row("R02"), 409, LocalizationErrorCode.I18N_CONTENT_CHANGED.getCode(), "发布期间文案已变化，请重新发布");
    }

    /** 导入文件仍在检查：key 导入预览、确认与翻译导入的 409 I18N_IMPORT_FILE_NOT_READY 与原因原样到达页面。 */
    @ParameterizedTest
    @CsvSource({"T01", "T02", "T06"})
    void test_importFileNotReadyPassesThrough(String id) throws Exception {
        assertCodedProblem(row(id), 409, LocalizationErrorCode.I18N_IMPORT_FILE_NOT_READY.getCode(),
            "导入文件仍在检查（SCANNING），请稍后重试: 7409912387650");
    }

    /** 公共业务拒绝、不存在与并发冲突保留 Code，并把 Platform 原因带到页面。 */
    @Test
    void test_businessRuleViolation() throws Exception {
        assertCodedProblem(row("C11"), 400, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), "命名空间 legacy 下还有 3 个 key，不能删除");
        resetBetweenCalls();
        assertCodedProblem(row("C05"), 404, CommonErrorCode.NOT_FOUND.getCode(), "应用不存在: client-console");
        resetBetweenCalls();
        assertCodedProblem(row("C03"), 409, CommonErrorCode.CONCURRENT_REQUEST.getCode(), "语言已被修改: zh-CN");
    }

    /** 保存翻译的 422 只保留本次 Body 内存在的字段路径；翻译导入只保留文件内路径形态；均不传播远端文案。 */
    @Test
    void test_validationPathsProjected() throws Exception {
        var save = row("T04");
        platform.rawResponse(save.path("ownerPath").asString(), 422, "application/problem+json",
            problem(422, CommonErrorCode.VALIDATION_ERROR.getCode(), "remote-private-detail", """
                [{"field":"translations[0].text","message":"remote-private-message"},
                 {"field":"translations[5].text","message":"remote-private-message"},{"field":"privateField","message":"x"}]"""),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));
        var saved = send(save);
        assertEquals(422, saved.statusCode(), saved.body());
        assertEquals(List.of("translations[0].text"), fields(saved));
        assertFalse(saved.body().contains("remote-private"), saved.body());

        resetBetweenCalls();
        var imports = row("T06");
        platform.rawResponse(imports.path("ownerPath").asString(), 422, "application/problem+json",
            problem(422, CommonErrorCode.VALIDATION_ERROR.getCode(), "remote-private-detail", """
                [{"field":"namespaces.team.team.invite.title","message":"remote-private-message"},
                 {"field":"namespaces.team.team.invite.title","message":"duplicate"},
                 {"field":"namespaces.Team\\"x","message":"x"},{"field":"fileId","message":"x"}]"""),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));
        var imported = send(imports);
        assertEquals(422, imported.statusCode(), imported.body());
        assertEquals(List.of("namespaces.team.team.invite.title"), fields(imported));
        assertFalse(imported.body().contains("remote-private"), imported.body());
    }

    /** Platform 503 映射为依赖不可用，不外露 Platform 原文。 */
    @Test
    void test_ownerUnavailable() throws Exception {
        var list = row("C04");
        platform.problem(list.path("ownerPath").asString(), 503, "SERVICE_UNAVAILABLE");

        var response = send(list);

        assertEquals(503, response.statusCode(), response.body());
        assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode(), JSON.readTree(response.body()).path("code").asString());
        assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY));
    }

    /** 回执中的语言、应用、命名空间、发布号或分页窗口与本次请求不符，按上游无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        assertUpstreamInvalid(row("C03"), "localeCode", "en");
        assertUpstreamInvalid(row("C07"), "applicationCode", "checkout");
        assertUpstreamInvalid(row("C10"), "namespaceCode", "billing");
        assertUpstreamInvalid(row("R05"), "releaseNo", 8);
        assertUpstreamInvalid(row("R06"), "releaseNo", 7);
        assertUpstreamInvalid(row("R03"), "page", 3);
        assertUpstreamInvalid(row("T05"), "locale", "en");
    }

    /** 未知、重复或空 Query，非法路径变量、填写状态与分页条数，未知 Body 字段、查询或删除带 Body 均在访问 Platform 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var locales = row("C01");
        assertRejectedLocally(400, send("GET", locales.path("path").asString() + "?status=ENABLED", locales.path("routeCode").asString(), null));
        assertRejectedLocally(400, send("GET", locales.path("path").asString(), locales.path("routeCode").asString(),
            JSON.createObjectNode().put("x", 1)));
        var translations = row("T03");
        String path = translations.path("path").asString();
        String route = translations.path("routeCode").asString();
        assertRejectedLocally(400, send("GET", path + "?locale=zh-CN&state=DONE", route, null));
        assertRejectedLocally(400, send("GET", path + "?locale=zh-CN&size=15", route, null));
        assertRejectedLocally(400, send("GET", path + "?locale=zh-CN&locale=en", route, null));
        assertRejectedLocally(400, send("GET", path + "?locale=ZH", route, null));
        assertRejectedLocally(400, send("GET", path, route, null));
        assertRejectedLocally(400, send("GET", "/localization-admin/v1/applications/Client_Console", "operations-i18n-application-detail", null));
        assertRejectedLocally(400, send("GET", "/localization-admin/v1/applications/client-console/releases/0", "operations-i18n-release-detail", null));
        var update = row("C03");
        assertRejectedLocally(400, send("PUT", "/localization-admin/v1/locales/zh_cn", update.path("routeCode").asString(), update.path("request")));
        var create = row("C02");
        assertRejectedLocally(400, send("POST", create.path("path").asString(), create.path("routeCode").asString(),
            ((ObjectNode) create.path("request").deepCopy()).put("operatorAccountId", ACTOR)));
        assertRejectedLocally(400, send("POST", create.path("path").asString(), create.path("routeCode").asString(),
            ((ObjectNode) create.path("request").deepCopy()).put("sortOrder", "20")));
        assertRejectedLocally(422, send("POST", create.path("path").asString(), create.path("routeCode").asString(),
            ((ObjectNode) create.path("request").deepCopy()).put("textDirection", "TTB")));
        var delete = row("C11");
        assertRejectedLocally(400, send("DELETE", delete.path("path").asString(), delete.path("routeCode").asString(), null));
        assertRejectedLocally(400, send("DELETE", delete.path("path").asString() + "?expectedVersion=0", delete.path("routeCode").asString(), null));
    }

    /** 开始上传：按用途固定原因码，JSON 类型与会话上传人；201 透传 PUT 授权，授权地址不进入日志。 */
    @ParameterizedTest
    @EnumSource(LocalizationUploadPurpose.class)
    void test_uploadStart(LocalizationUploadPurpose purpose, CapturedOutput output) throws Exception {
        platform.rawResponse(UPLOAD_PATH, 201, "application/json", """
            {"uploadId":"%s","authorization":{"method":"PUT","url":"%s","headers":{"Content-Type":"application/json"},
             "expiresAt":"2026-10-23T08:15:00Z"},"expiresAt":"2026-10-23T08:15:00Z"}
            """.formatted(UPLOAD_ID, PUT_URL_CANARY));

        var response = send("POST", "/localization-admin/v1/uploads", "operations-i18n-upload-start",
            JSON.createObjectNode().put("purpose", purpose.getCode()).put("fileName", "client-console.en.json")
                .put("sizeBytes", "20480"));

        assertEquals(201, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(UPLOAD_ID, body.path("uploadId").asString()),
            () -> assertEquals(PUT_URL_CANARY, body.path("authorization").path("url").asString()));
        var call = ownerCall();
        var forwarded = JSON.readTree(call.body());
        assertAll(() -> assertEquals(UPLOAD_PATH, call.path()), () -> assertEquals("POST", call.method()),
            () -> assertEquals(purpose.getReasonCode(), forwarded.path("reasonCode").asString()),
            () -> assertEquals("application/json", forwarded.path("mimeType").asString()),
            () -> assertEquals("client-console.en.json", forwarded.path("filename").asString()),
            () -> assertEquals("20480", forwarded.path("sizeBytes").asString()),
            () -> assertEquals(ACTOR, forwarded.path("uploaderAccountId").asString()),
            () -> assertEquals("operations-console-bff", call.header("X-Caller-Service")),
            () -> assertNull(call.header("Idempotency-Key")));
        assertFalse(output.getAll().contains("PUT-URL-CANARY"), "上传授权不得进入日志");
    }

    /** 完成上传后查询扫描状态，返回文件 ID 供导入使用；回执地址指向其他文件按 502；文件策略拒绝 422 保留文件错误码。 */
    @Test
    void test_uploadCompletion() throws Exception {
        String completion = UPLOAD_PATH + "/" + UPLOAD_ID + "/completion";
        platform.groupedResponses(Map.of(completion,
            "{\"uploadId\":\"%s\",\"fileId\":\"%s\",\"fileAddress\":\"%s\",\"completedAt\":\"2026-10-23T08:01:00Z\"}"
                .formatted(UPLOAD_ID, FILE_ID, FileAddress.of(Long.parseLong(FILE_ID)).value()),
            "/internal/files/v1/files/" + FILE_ID, fileMetadata(FileScanStatus.AVAILABLE.getCode())));

        var response = send("POST", "/localization-admin/v1/uploads/" + UPLOAD_ID + "/completion", "operations-i18n-upload-complete", null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(FILE_ID, body.path("fileId").asString()),
            () -> assertEquals(FileAddress.of(Long.parseLong(FILE_ID)).value(), body.path("fileAddress").asString()),
            () -> assertEquals(FileScanStatus.AVAILABLE.getCode(), body.path("scanStatus").asString()),
            () -> assertEquals("2026-10-23T08:01:00Z", body.path("completedAt").asString()));
        assertEquals(List.of(AUTHORIZE_PATH, completion, "/internal/files/v1/files/" + FILE_ID),
            platform.requests().stream().map(RecordedRequest::path).toList());

        resetBetweenCalls();
        platform.groupedResponses(Map.of(completion,
            "{\"uploadId\":\"%s\",\"fileId\":\"%s\",\"fileAddress\":\"%s\",\"completedAt\":\"2026-10-23T08:01:00Z\"}"
                .formatted(UPLOAD_ID, FILE_ID, FileAddress.of(7409912387999L).value())));
        var otherAddress = send("POST", "/localization-admin/v1/uploads/" + UPLOAD_ID + "/completion", "operations-i18n-upload-complete", null);
        assertEquals(502, otherAddress.statusCode(), otherAddress.body());

        resetBetweenCalls();
        platform.problem(UPLOAD_PATH, 422, FileErrorCode.FILE_POLICY_REJECTED.getCode());
        var rejected = send("POST", "/localization-admin/v1/uploads", "operations-i18n-upload-start",
            JSON.createObjectNode().put("purpose", LocalizationUploadPurpose.KEY_IMPORT.getCode()).put("fileName", "keys.json")
                .put("sizeBytes", "99999999999"));
        assertEquals(422, rejected.statusCode(), rejected.body());
        assertEquals(FileErrorCode.FILE_POLICY_REJECTED.getCode(), JSON.readTree(rejected.body()).path("code").asString());
    }

    /** 未知用途、非 JSON 文件名、页面自带原因码与完成上传带 Body 均在访问 Platform 前拒绝。 */
    @Test
    void test_uploadInvalidParameter() throws Exception {
        String route = "operations-i18n-upload-start";
        assertRejectedLocally(422, send("POST", "/localization-admin/v1/uploads", route,
            JSON.createObjectNode().put("purpose", "RELEASE_BUNDLE").put("fileName", "keys.json").put("sizeBytes", "1024")));
        assertRejectedLocally(422, send("POST", "/localization-admin/v1/uploads", route,
            JSON.createObjectNode().put("purpose", "KEY_IMPORT").put("fileName", "keys.csv").put("sizeBytes", "1024")));
        assertRejectedLocally(400, send("POST", "/localization-admin/v1/uploads", route,
            JSON.createObjectNode().put("purpose", "KEY_IMPORT").put("fileName", "keys.json").put("sizeBytes", "1024")
                .put("reasonCode", "I18N_RELEASE_BUNDLE")));
        assertRejectedLocally(400, send("POST", "/localization-admin/v1/uploads/" + UPLOAD_ID + "/completion",
            "operations-i18n-upload-complete", JSON.createObjectNode().put("fileId", FILE_ID)));
    }

    /** Owner Problem 的状态、Code、detail 与本次 Request ID；Owner Request ID 不外露。 */
    private void assertCodedProblem(JsonNode route, int status, String code, String detail) throws Exception {
        platform.rawResponse(route.path("ownerPath").asString(), status, "application/problem+json",
            problem(status, code, detail, null), Map.of("X-Request-Id", OWNER_REQUEST_ID));
        var response = send(route);
        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code, body.path("code").asString()),
            () -> assertEquals(detail, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(OWNER_REQUEST_ID)));
        ownerCall();
    }

    /** 复制样例响应并改写一个字段后转发，期望 502 且 Owner 只调用一次。 */
    private void assertUpstreamInvalid(JsonNode row, String field, Object value) throws Exception {
        resetBetweenCalls();
        var changed = (ObjectNode) row.path("response").deepCopy();
        if (value instanceof Integer number) {
            changed.put(field, number);
        } else {
            changed.put(field, value.toString());
        }
        platform.rawResponse(row.path("ownerPath").asString(), JSON.writeValueAsString(changed));
        var response = send(row);
        assertEquals(502, response.statusCode(), row.path("id").asString() + " " + response.body());
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode(), JSON.readTree(response.body()).path("code").asString());
        ownerCall();
    }

    /** Platform 严格 Problem 响应体；errors 为 JSON 数组文本或 null。 */
    private static String problem(int status, String code, String detail, String errors) {
        var node = JSON.createObjectNode().put("type", "about:blank")
            .put("title", org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase()).put("detail", detail)
            .put("code", code).put("requestId", OWNER_REQUEST_ID).put("timestamp", "2026-10-23T00:00:00.123456Z");
        if (errors != null) {
            node.set("errors", JSON.readTree(errors));
        }
        return JSON.writeValueAsString(node);
    }

    private static List<String> fields(HttpResponse<String> response) {
        return JSON.readTree(response.body()).path("errors").valueStream().map(error -> error.path("field").asString()).toList();
    }

    /** 本地拒绝：指定状态，Platform 至多收到 PLT-I02；之后为下一次调用重置记录。 */
    private void assertRejectedLocally(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals),
            platform.requests().toString());
        resetBetweenCalls();
    }

    /** Platform 只收到一次 PLT-I02。 */
    private void assertOnlyAuthorized() {
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** 授权之后恰好一次 Owner 调用，返回该调用。 */
    private RecordedRequest ownerCall() {
        var requests = platform.requests();
        assertEquals(2, requests.size(), requests.toString());
        assertEquals(AUTHORIZE_PATH, requests.getFirst().path());
        return requests.getLast();
    }

    /** 同一用例内的下一次独立调用：先确认没有意外调用，再清空记录并恢复默认授权。 */
    private void resetBetweenCalls() {
        assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
        platform.reset();
        platform.useCommercialIdentity();
    }

    private static String fileMetadata(String scanStatus) {
        return """
            {"fileId":"%s","sourceSystem":"operations-console-bff","reasonCode":"I18N_KEY_IMPORT","displayName":"keys.json",
             "mimeType":"application/json","sizeBytes":"20480","contentHash":"%s","scanStatus":"%s","objectStatus":"ACTIVE",
             "uploaderAccountId":"%s","createTime":"2026-10-23T08:01:00Z"}
            """.formatted(FILE_ID, "c".repeat(64), scanStatus, ACTOR);
    }

    private HttpResponse<String> send(JsonNode row) throws Exception {
        return send(row.path("method").asString(), row.path("path").asString() + row.path("query").asString(),
            row.path("routeCode").asString(), row.path("request"));
    }

    /** 经真实 BFF HTTP 入口发送浏览器请求，带 Gateway 可信身份与浏览器凭据（证明不向下游转发）。 */
    private HttpResponse<String> send(String method, String pathAndQuery, String routeCode, JsonNode body) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery))
            .timeout(Duration.ofSeconds(15))
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACTOR).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", routeCode).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", "i18n-command-0001")
            .header("Authorization", "Bearer browser-access-token").header("Cookie", "browser-session=canary")
            .header("Origin", "https://operations-console.local.test");
        boolean hasBody = body != null && !body.isNull() && !body.isMissingNode();
        if (!"GET".equals(method) && !"DELETE".equals(method) || hasBody) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(method, hasBody ? HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))
            : HttpRequest.BodyPublishers.noBody()).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 解析 Query 为键值。 */
    private static Map<String, String> query(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("?")) {
            return Map.of();
        }
        var result = new HashMap<String, String>();
        for (String pair : raw.replaceFirst("^\\?", "").split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }
}
