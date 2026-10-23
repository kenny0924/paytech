package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
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
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.card.api.CardApiConstants;
import com.paytech.card.api.enums.BinLibraryVersionStatus;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.ext.rest.CardBinLibraryController;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.CardOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.files.FileAddress;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 维护卡 BIN 库：Card 运营接口 7 条转发（B1—B5、B8、B9）与 Platform 内部文件上传 2 条（B6、B7）
 * <li>Card 与 Platform 均由 Loopback Simulator 承担；PLT-I02 授权与 Platform 文件调用记录在同一 Platform Simulator。</li>
 * <li>覆盖转发契约（调用方、操作人、Query、Body 与状态码）、授权拒绝、本地边界拒绝、Owner 错误透传、依赖故障与上传授权不进入日志。</li>
 *
 * @see CardBinLibraryController#listVersions(jakarta.servlet.http.HttpServletRequest, String, Integer, Integer)
 * @see CardBinLibraryController#getVersion(jakarta.servlet.http.HttpServletRequest, Integer)
 * @see CardBinLibraryController#listImportErrors(jakarta.servlet.http.HttpServletRequest, Integer, Integer, Integer)
 * @see CardBinLibraryController#listActivations(jakarta.servlet.http.HttpServletRequest, Integer, Integer)
 * @see CardBinLibraryController#lookupBin(jakarta.servlet.http.HttpServletRequest, String, Integer)
 * @see CardBinLibraryController#startUpload(jakarta.servlet.http.HttpServletRequest,
 *      com.paytech.operations.console.bff.service.model.OperatorContext,
 *      com.paytech.operations.console.bff.service.model.binlibrary.request.StartBinLibraryUploadRequestBody)
 * @see CardBinLibraryController#completeUpload(jakarta.servlet.http.HttpServletRequest, String)
 * @see CardBinLibraryController#createVersion(jakarta.servlet.http.HttpServletRequest,
 *      com.paytech.operations.console.bff.service.model.OperatorContext,
 *      com.paytech.card.api.binlibrary.request.CreateBinLibraryVersionRequestBody)
 * @see CardBinLibraryController#activateVersion(jakarta.servlet.http.HttpServletRequest,
 *      com.paytech.operations.console.bff.service.model.OperatorContext, Integer,
 *      com.paytech.card.api.binlibrary.request.ActivateBinLibraryVersionRequestBody)
 * @author Claude
 */
@ExtendWith(OutputCaptureExtension.class)
class CardBinLibraryBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final String BOUNDARY = "/card-bin-library-boundary.json";
    private static final String REQUEST_ID = "card-bin-boundary-request";
    private static final String ACTOR = PlatformOperationsSimulator.RISK_ACCOUNT_ID;
    private static final String UPLOAD_PATH = "/internal/files/v1/uploads";
    private static final String UPLOAD_ID = "7409912387001";
    private static final String FILE_ID = "7409912387650";
    private static final String PUT_URL_CANARY = "https://storage.local.test/bin?X-Amz-Signature=PUT-URL-CANARY";
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    private PlatformOperationsSimulator platform;

    @Autowired
    private CardOwnerSimulator card;

    @LocalServerPort
    private int port;

    /** 每例从空 Card 配置与默认授权开始。 */
    @BeforeEach
    void setUpCardMocks() {
        platform.reset();
        platform.useCommercialIdentity();
        card.reset();
    }

    /** Platform 与 Card 不得收到未配置的请求；无论断言结果都释放 Card 配置。 */
    @AfterEach
    void verifyCardOwnerCalls() {
        try {
            assertAll(() -> assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()),
                () -> assertTrue(card.unexpectedRequests().isEmpty(), card.unexpectedRequests().toString()));
        } finally {
            card.reset();
        }
    }

    static Stream<JsonNode> endpoints() {
        try (var input = CardBinLibraryBrowserAutoTest.class.getResourceAsStream(BOUNDARY)) {
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

    /** 方法、路径、Query、Body 与调用方 / 操作人 Header 准确转发一次，状态码与响应原样返回；浏览器凭据不转发。 */
    @ParameterizedTest(name = "{index} BIN 库转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        replyAsOwner(row);

        var response = send(row);

        assertEquals(row.path("status").asInt(), response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        assertEquals(1, card.requests().size(), card.requests().toString());
        var call = card.requests().getFirst();
        boolean command = !"GET".equals(row.path("method").asString());
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertEquals(row.path("ownerPath").asString(), call.path()),
            () -> assertEquals(query(row.path("ownerQuery").asString()), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals(CardApiConstants.OPERATIONS_CONSOLE_BFF, call.header(CardApiConstants.CALLER_HEADER)),
            () -> assertEquals(command ? ACTOR : null, call.header(CardApiConstants.ACTOR_HEADER)),
            () -> assertNull(call.header("X-Gateway-Session-Id")), () -> assertNull(call.header("Authorization")),
            () -> assertNull(call.header("Cookie")), () -> assertNull(call.header("Idempotency-Key")));
        if (command) {
            assertEquals(row.path("request"), JSON.readTree(call.body()));
        } else {
            assertTrue(call.body().isEmpty(), "查询不得转发 Body");
        }
    }

    /** 当前会话授权失败时只调用 PLT-I02，不访问 Card。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        var response = send(row);

        assertEquals(403, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        assertTrue(card.requests().isEmpty(), card.requests().toString());
    }

    /** 开始上传：固定上传原因、CSV 类型与会话上传人，带调用方；201 透传 PUT 授权，授权地址不进入日志。 */
    @Test
    void test_uploadStart(CapturedOutput output) throws Exception {
        platform.rawResponse(UPLOAD_PATH, 201, "application/json", """
            {"uploadId":"%s","authorization":{"method":"PUT","url":"%s","headers":{"Content-Type":"text/csv"},
             "expiresAt":"2026-10-22T08:15:00Z"},"expiresAt":"2026-10-22T08:15:00Z"}
            """.formatted(UPLOAD_ID, PUT_URL_CANARY));

        var response = send("POST", "/card-admin/v1/bin-library/uploads", "operations-card-bin-upload-start",
            JSON.createObjectNode().put("fileName", "bin-2026-10-22.csv").put("sizeBytes", "40265318"));

        assertEquals(201, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(UPLOAD_ID, body.path("uploadId").asString()),
            () -> assertEquals(PUT_URL_CANARY, body.path("authorization").path("url").asString()),
            () -> assertEquals("text/csv", body.path("authorization").path("headers").path("Content-Type").asString()));
        var call = platformCall(UPLOAD_PATH);
        var forwarded = JSON.readTree(call.body());
        assertAll(() -> assertEquals("POST", call.method()),
            () -> assertEquals(CardApiConstants.OPERATIONS_CONSOLE_BFF, call.header(CardApiConstants.CALLER_HEADER)),
            () -> assertEquals("CARD_BIN_LIBRARY_IMPORT", forwarded.path("reasonCode").asString()),
            () -> assertEquals("text/csv", forwarded.path("mimeType").asString()),
            () -> assertEquals("bin-2026-10-22.csv", forwarded.path("filename").asString()),
            () -> assertEquals("40265318", forwarded.path("sizeBytes").asString()),
            () -> assertEquals(ACTOR, forwarded.path("uploaderAccountId").asString()),
            () -> assertNull(call.header("Idempotency-Key")));
        assertFalse(output.getAll().contains("PUT-URL-CANARY"), "上传授权不得进入日志");
        assertTrue(card.requests().isEmpty(), card.requests().toString());
    }

    /** 完成上传后查询扫描状态：返回 Platform 完成回执中的文件地址与当前扫描状态，Card 零调用。 */
    @Test
    void test_uploadCompletion() throws Exception {
        platform.groupedResponses(Map.of(
            UPLOAD_PATH + "/" + UPLOAD_ID + "/completion",
            "{\"uploadId\":\"%s\",\"fileId\":\"%s\",\"fileAddress\":\"%s\",\"completedAt\":\"2026-10-22T08:01:00Z\",\"versionNo\":1,\"accessLevel\":\"PRIVATE\"}"
                .formatted(UPLOAD_ID, FILE_ID, FileAddress.of(Long.parseLong(FILE_ID)).value()),
            "/internal/files/v1/files/" + FILE_ID, fileMetadata(FILE_ID, FileScanStatus.SCANNING.getCode())));

        var response = send("POST", "/card-admin/v1/bin-library/uploads/" + UPLOAD_ID + "/completion",
            "operations-card-bin-upload-complete", null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(UPLOAD_ID, body.path("uploadId").asString()),
            () -> assertEquals(FILE_ID, body.path("fileId").asString()),
            () -> assertEquals(FileAddress.of(Long.parseLong(FILE_ID)).value(), body.path("fileAddress").asString()),
            () -> assertEquals(FileScanStatus.SCANNING.getCode(), body.path("scanStatus").asString()),
            () -> assertEquals("2026-10-22T08:01:00Z", body.path("completedAt").asString()));
        assertEquals(List.of(AUTHORIZE_PATH, UPLOAD_PATH + "/" + UPLOAD_ID + "/completion", "/internal/files/v1/files/" + FILE_ID),
            platform.requests().stream().map(RecordedRequest::path).toList());
        assertTrue(card.requests().isEmpty(), card.requests().toString());
    }

    /** 完成回执的文件 ID 与元数据不一致、缺文件地址或地址指向其他文件按上游无效响应 502；Platform 文件策略拒绝（422）按 BFF 统一的文件错误码状态以 422 外显并保留文件错误码，不外露 Platform detail。 */
    @Test
    void test_uploadOwnerFailures() throws Exception {
        platform.groupedResponses(Map.of(
            UPLOAD_PATH + "/" + UPLOAD_ID + "/completion",
            "{\"uploadId\":\"%s\",\"fileId\":\"%s\",\"fileAddress\":\"%s\",\"completedAt\":\"2026-10-22T08:01:00Z\",\"versionNo\":1,\"accessLevel\":\"PRIVATE\"}"
                .formatted(UPLOAD_ID, FILE_ID, FileAddress.of(Long.parseLong(FILE_ID)).value()),
            "/internal/files/v1/files/" + FILE_ID, fileMetadata("7409912387999", FileScanStatus.AVAILABLE.getCode())));
        var mismatched = send("POST", "/card-admin/v1/bin-library/uploads/" + UPLOAD_ID + "/completion",
            "operations-card-bin-upload-complete", null);
        assertEquals(502, mismatched.statusCode(), mismatched.body());
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode(), JSON.readTree(mismatched.body()).path("code").asString());

        resetBetweenCalls();
        platform.groupedResponses(Map.of(UPLOAD_PATH + "/" + UPLOAD_ID + "/completion",
            "{\"uploadId\":\"%s\",\"fileId\":\"%s\",\"completedAt\":\"2026-10-22T08:01:00Z\",\"versionNo\":1,\"accessLevel\":\"PRIVATE\"}".formatted(UPLOAD_ID, FILE_ID)));
        var addressless = send("POST", "/card-admin/v1/bin-library/uploads/" + UPLOAD_ID + "/completion",
            "operations-card-bin-upload-complete", null);
        assertEquals(502, addressless.statusCode(), addressless.body());

        resetBetweenCalls();
        platform.groupedResponses(Map.of(UPLOAD_PATH + "/" + UPLOAD_ID + "/completion",
            "{\"uploadId\":\"%s\",\"fileId\":\"%s\",\"fileAddress\":\"%s\",\"completedAt\":\"2026-10-22T08:01:00Z\",\"versionNo\":1,\"accessLevel\":\"PRIVATE\"}"
                .formatted(UPLOAD_ID, FILE_ID, FileAddress.of(7409912387999L).value())));
        var otherAddress = send("POST", "/card-admin/v1/bin-library/uploads/" + UPLOAD_ID + "/completion",
            "operations-card-bin-upload-complete", null);
        assertEquals(502, otherAddress.statusCode(), otherAddress.body());

        resetBetweenCalls();
        platform.problem(UPLOAD_PATH, 422, FileErrorCode.FILE_POLICY_REJECTED.getCode());
        var rejected = send("POST", "/card-admin/v1/bin-library/uploads", "operations-card-bin-upload-start",
            JSON.createObjectNode().put("fileName", "bin.csv").put("sizeBytes", "99999999999"));
        assertEquals(422, rejected.statusCode(), rejected.body());
        assertAll(() -> assertEquals(FileErrorCode.FILE_POLICY_REJECTED.getCode(),
                JSON.readTree(rejected.body()).path("code").asString()),
            () -> assertFalse(rejected.body().contains(PlatformOperationsSimulator.UPSTREAM_DETAIL_CANARY)));
    }

    /** Card 的业务拒绝、不存在与并发冲突保留公共 Code，并经 Owner detail 开关把原因带到页面；Owner Request ID 不外露。 */
    @Test
    void test_businessRuleViolation() throws Exception {
        assertOwnerProblem(row("B8"), 400, CommonErrorCode.BUSINESS_CHECK_FAILED);
        resetBetweenCalls();
        assertOwnerProblem(row("B2"), 404, CommonErrorCode.NOT_FOUND);
        resetBetweenCalls();
        assertOwnerProblem(row("B9"), 409, CommonErrorCode.CONCURRENT_REQUEST);
    }

    /** Card 不可用映射 503；回执版本号、分页窗口与请求不符映射 502。 */
    @Test
    void test_dependencyFailures() throws Exception {
        var activate = row("B9");
        card.problem("POST", activate.path("ownerPath").asString(), 503, "OWNER_UNAVAILABLE");
        var unavailable = send(activate);
        assertEquals(503, unavailable.statusCode(), unavailable.body());
        assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE.getCode(), JSON.readTree(unavailable.body()).path("code").asString());

        resetBetweenCalls();
        var other = (ObjectNode) activate.path("response").deepCopy();
        other.put("versionNo", 7);
        card.reply("POST", activate.path("ownerPath").asString(), 200, JSON.writeValueAsString(other));
        assertUpstreamInvalid(send(activate));

        resetBetweenCalls();
        var list = row("B1");
        var page = (ObjectNode) list.path("response").deepCopy();
        page.put("page", 2);
        card.reply("GET", list.path("ownerPath").asString(), 200, JSON.writeValueAsString(page));
        assertUpstreamInvalid(send(list));
    }

    /** BIN 格式（含完整卡号）、未知状态 / Query、非法版本号、非 CSV 文件名、请求体多余字段与完成上传带 Body 均在访问下游前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var lookup = row("B5");
        for (String bin : List.of("41111", "411111111", "4111111111111111", "41111a")) {
            assertRejectedLocally(400, send("GET", "/card-admin/v1/bin-library/bins/" + bin, lookup.path("routeCode").asString(), null));
            resetBetweenCalls();
        }
        var list = row("B1");
        assertRejectedLocally(400, send("GET", list.path("path").asString() + "?status=DELETED", list.path("routeCode").asString(), null));
        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", list.path("path").asString() + "?status="
            + BinLibraryVersionStatus.VALID.getCode() + "&unknown=1", list.path("routeCode").asString(), null));
        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", list.path("path").asString() + "?size=15", list.path("routeCode").asString(), null));
        resetBetweenCalls();
        assertRejectedLocally(400, send("GET", "/card-admin/v1/bin-library/versions/0", "operations-card-bin-version-detail", null));
        resetBetweenCalls();

        var create = row("B8");
        assertRejectedLocally(400, send("POST", create.path("path").asString(), create.path("routeCode").asString(),
            JSON.createObjectNode().put("fileAddress", FileAddress.of(7409912387650L).value()).put("operatorAccountId", ACTOR)));
        resetBetweenCalls();
        var badAddress = send("POST", create.path("path").asString(), create.path("routeCode").asString(),
            JSON.createObjectNode().put("fileAddress", "s3://bucket/bin.csv"));
        assertRejectedLocally(422, badAddress);
        assertEquals("fileAddress", JSON.readTree(badAddress.body()).path("errors").get(0).path("field").asString());
        resetBetweenCalls();

        var activate = row("B9");
        assertRejectedLocally(400, send("POST", activate.path("path").asString(), activate.path("routeCode").asString(),
            JSON.createObjectNode().put("expectedActiveVersionNo", "3")));
        resetBetweenCalls();

        var upload = send("POST", "/card-admin/v1/bin-library/uploads", "operations-card-bin-upload-start",
            JSON.createObjectNode().put("fileName", "bin.xlsx").put("sizeBytes", "1024"));
        assertRejectedLocally(422, upload);
        assertOnlyAuthorized();
        resetBetweenCalls();
        assertRejectedLocally(400, send("POST", "/card-admin/v1/bin-library/uploads/" + UPLOAD_ID + "/completion",
            "operations-card-bin-upload-complete", JSON.createObjectNode().put("fileId", FILE_ID)));
        assertOnlyAuthorized();
    }

    /** Owner Problem 的状态、公共 Code、detail 与本次 Request ID。 */
    private void assertOwnerProblem(JsonNode route, int status, CommonErrorCode code) throws Exception {
        card.problem(route.path("method").asString(), route.path("ownerPath").asString(), status, code.getCode());
        var response = send(route);
        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code.getCode(), body.path("code").asString()),
            () -> assertEquals(CardOwnerSimulator.OWNER_DETAIL_CANARY, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(CardOwnerSimulator.OWNER_REQUEST_ID)));
    }

    private static void assertUpstreamInvalid(HttpResponse<String> response) {
        assertEquals(502, response.statusCode(), response.body());
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode(), JSON.readTree(response.body()).path("code").asString());
    }

    /** 本地拒绝：指定状态，Card 零调用。 */
    private void assertRejectedLocally(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(card.requests().isEmpty(), card.requests().toString());
    }

    /** Platform 只收到 PLT-I02。 */
    private void assertOnlyAuthorized() {
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** 同一用例内的下一次独立调用：先确认没有意外调用，再清空记录并恢复默认授权。 */
    private void resetBetweenCalls() {
        assertAll(() -> assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()),
            () -> assertTrue(card.unexpectedRequests().isEmpty(), card.unexpectedRequests().toString()));
        platform.reset();
        platform.useCommercialIdentity();
        card.reset();
    }

    private RecordedRequest platformCall(String path) {
        return platform.requests().stream().filter(request -> path.equals(request.path())).findFirst().orElseThrow();
    }

    private static String fileMetadata(String fileId, String scanStatus) {
        return """
            {"fileId":"%s","sourceSystem":"operations-console-bff","reasonCode":"CARD_BIN_LIBRARY_IMPORT","displayName":"bin.csv",
             "mimeType":"text/csv","sizeBytes":"40265318","contentHash":"%s","scanStatus":"%s","objectStatus":"ACTIVE",
             "uploaderAccountId":"%s","createTime":"2026-10-22T08:01:00Z",
             "accessLevel":"PRIVATE","versionNo":1,"publicUrl":null,"versionedUrl":null,"pendingVersionNo":null}
            """.formatted(fileId, "c".repeat(64), scanStatus, ACTOR);
    }

    /** 按样例配置 Card 响应。 */
    private void replyAsOwner(JsonNode row) {
        card.reply(row.path("method").asString(), row.path("ownerPath").asString() + row.path("ownerQuery").asString(),
            row.path("ownerStatus").asInt(), JSON.writeValueAsString(row.path("response")));
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
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", "card-bin-command-0001")
            .header("Authorization", "Bearer browser-access-token").header("Cookie", "browser-session=canary")
            .header("Origin", "https://operations-console.local.test");
        if (!"GET".equals(method)) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(method, body == null || body.isNull() || body.isMissingNode()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
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
