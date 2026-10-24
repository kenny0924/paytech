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
import java.time.Instant;
import java.util.HashMap;
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

import com.paytech.card.api.CardApiConstants;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.ext.rest.FileUploadController;
import com.paytech.operations.console.bff.integration.PlatformPublicFileClient;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.platform.support.api.enums.FileErrorCode;
import com.paytech.platform.support.api.enums.FileScanStatus;
import com.paytech.platform.support.api.files.FileAddress;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 使用文件上传页：写入限制、开始上传、完成上传简单转发 Platform 内部文件接口；最近上传与单个上传
 * 经 Processor 整形，并只返回本入口（operations-console-bff、OPERATIONS_PUBLIC_ASSET）的公开上传
 * <li>Platform 由 Loopback Simulator 承担：PLT-I02 授权与内部文件调用记录在同一 Simulator，每例检查没有未配置的调用。</li>
 * <li>覆盖固定的原因、级别与上传人、mine 与缺省时间范围、其他文件 404、授权拒绝、本地边界拒绝、Platform 422 / 503 / 400 透传、
 * 回执无效 502，以及开始上传的临时授权不进入日志。</li>
 *
 * @see FileUploadController#getConstraints()
 * @see FileUploadController#listFiles(com.paytech.operations.console.bff.service.model.OperatorContext, Instant, Instant,
 *      Boolean, Integer, Integer)
 * @see FileUploadController#getFile(com.paytech.operations.console.bff.service.model.OperatorContext, String)
 * @see FileUploadController#startUpload(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      com.paytech.operations.console.bff.service.model.fileupload.request.StartPublicFileUploadRequestBody)
 * @see FileUploadController#completeUpload(String)
 * @author Claude
 */
@ExtendWith(OutputCaptureExtension.class)
class FileUploadBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final String REQUEST_ID = "file-upload-boundary-request";
    private static final String ACTOR = PlatformOperationsSimulator.RISK_ACCOUNT_ID;
    private static final String OTHER_ACCOUNT = "7409912380042";
    private static final String CONSTRAINTS_PATH = "/internal/files/v1/upload-constraints";
    private static final String UPLOAD_PATH = "/internal/files/v1/uploads";
    private static final String FILES_PATH = "/internal/files/v1/files";
    private static final String UPLOAD_ID = "7409912388001";
    private static final String FILE_ID = "7409912388650";
    private static final String OTHER_FILE_ID = "7409912388651";
    private static final String PUT_URL_CANARY = "https://storage.local.test/public?X-Amz-Signature=PUT-URL-CANARY";
    private static final String PUBLIC_URL = "https://public.local.test/assets/1b0c-logo";
    private static final String CONSTRAINTS_ROUTE = "operations-file-upload-constraints";
    private static final String LIST_ROUTE = "operations-file-upload-list";
    private static final String DETAIL_ROUTE = "operations-file-upload-detail";
    private static final String START_ROUTE = "operations-file-upload-start";
    private static final String COMPLETE_ROUTE = "operations-file-upload-complete";
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    private PlatformOperationsSimulator platform;

    @LocalServerPort
    private int port;

    /** 每例从空 Platform 配置与默认授权开始。 */
    @BeforeEach
    void setUpFileUploadMocks() {
        platform.reset();
        platform.useCommercialIdentity();
    }

    /** Platform 不得收到未配置的请求。 */
    @AfterEach
    void verifyPlatformCalls() {
        assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
    }

    /** 写入限制固定查询 PUBLIC 并带调用方，已配置与未配置的响应都原样返回。 */
    @Test
    void test_constraints() throws Exception {
        String configured = """
            {"configured":true,"accessLevel":"PUBLIC","routeSourceSystem":"operations-console-bff","instanceCode":"S3_TEST",
             "bucketName":"mor-test-platform-public","publicBaseUrl":"https://public.local.test/assets",
             "scanPolicyCode":"PUBLIC_DOCUMENT","scanPolicyRevisionNo":1,
             "allowedMimeTypes":["application/pdf","image/png","image/jpeg"],"maxSizeBytes":"20971520","maxUploadSeconds":900}
            """;
        platform.rawResponse(CONSTRAINTS_PATH, configured);

        var response = send("GET", "/file-upload/v1/constraints", CONSTRAINTS_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(JSON.readTree(configured), JSON.readTree(response.body()));
        var call = platformCall(CONSTRAINTS_PATH);
        assertAll(() -> assertEquals("GET", call.method()), () -> assertEquals(Map.of("accessLevel", "PUBLIC"), query(call.query())),
            () -> assertEquals(PlatformPublicFileClient.SOURCE_SYSTEM, call.header(CardApiConstants.CALLER_HEADER)),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")));

        resetBetweenCalls();
        platform.rawResponse(CONSTRAINTS_PATH, "{\"configured\":false,\"accessLevel\":\"PUBLIC\"}");
        var unconfigured = send("GET", "/file-upload/v1/constraints", CONSTRAINTS_ROUTE, null);
        assertEquals(200, unconfigured.statusCode(), unconfigured.body());
        var body = JSON.readTree(unconfigured.body());
        assertAll(() -> assertFalse(body.path("configured").asBoolean()),
            () -> assertEquals("PUBLIC", body.path("accessLevel").asString()));
    }

    /** 开始上传：固定原因、PUBLIC 与会话上传人，转发浏览器声明的文件名、类型与大小；201 透传 PUT 授权，授权地址不进入日志。 */
    @Test
    void test_uploadStart(CapturedOutput output) throws Exception {
        platform.rawResponse(UPLOAD_PATH, 201, "application/json", """
            {"uploadId":"%s","authorization":{"method":"PUT","url":"%s","headers":{"Content-Type":"image/png"},
             "expiresAt":"2026-10-24T08:15:00Z"},"expiresAt":"2026-10-24T08:15:00Z"}
            """.formatted(UPLOAD_ID, PUT_URL_CANARY));

        var response = send("POST", "/file-upload/v1/uploads", START_ROUTE, startBody());

        assertEquals(201, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(UPLOAD_ID, body.path("uploadId").asString()),
            () -> assertEquals(PUT_URL_CANARY, body.path("authorization").path("url").asString()),
            () -> assertEquals("image/png", body.path("authorization").path("headers").path("Content-Type").asString()));
        var call = platformCall(UPLOAD_PATH);
        var forwarded = JSON.readTree(call.body());
        assertAll(() -> assertEquals("POST", call.method()),
            () -> assertEquals(PlatformPublicFileClient.SOURCE_SYSTEM, call.header(CardApiConstants.CALLER_HEADER)),
            () -> assertEquals(PlatformPublicFileClient.REASON_CODE, forwarded.path("reasonCode").asString()),
            () -> assertEquals(PlatformPublicFileClient.ACCESS_LEVEL, forwarded.path("accessLevel").asString()),
            () -> assertEquals(ACTOR, forwarded.path("uploaderAccountId").asString()),
            () -> assertEquals("logo.png", forwarded.path("filename").asString()),
            () -> assertEquals("image/png", forwarded.path("mimeType").asString()),
            () -> assertEquals("20480", forwarded.path("sizeBytes").asString()),
            () -> assertNull(call.header("Idempotency-Key")));
        assertFalse(output.getAll().contains("PUT-URL-CANARY"), "上传授权不得进入日志");
    }

    /** 完成上传后按回执的文件 ID 复查元数据：本入口的公开上传原样返回 Platform 回执（含文件 ID 与文件地址）。 */
    @Test
    void test_uploadCompletion() throws Exception {
        String receipt = completion(FileAddress.of(Long.parseLong(FILE_ID)).value());
        platform.groupedResponses(Map.of(UPLOAD_PATH + "/" + UPLOAD_ID + "/completion", receipt,
            FILES_PATH + "/" + FILE_ID, metadata(FILE_ID, PlatformPublicFileClient.SOURCE_SYSTEM,
                PlatformPublicFileClient.REASON_CODE, ACTOR, FileScanStatus.PENDING_SCAN.getCode(), null)));

        var response = send("POST", "/file-upload/v1/uploads/" + UPLOAD_ID + "/completion", COMPLETE_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(JSON.readTree(receipt), JSON.readTree(response.body()));
        assertEquals(List.of(AUTHORIZE_PATH, UPLOAD_PATH + "/" + UPLOAD_ID + "/completion", FILES_PATH + "/" + FILE_ID),
            platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /**
     * 完成的是同来源其他业务的上传（原因不同，如卡 BIN 导入）或非公开文件：复查后按不存在返回 404，与单个上传查询的 404 相同，
     * 不外露回执与元数据；WARN 只记上传 ID 与文件 ID。
     */
    @Test
    void test_completionOfOtherEntryNotFound(CapturedOutput output) throws Exception {
        String receipt = completion(FileAddress.of(Long.parseLong(FILE_ID)).value());
        for (String metadata : List.of(
            metadata(FILE_ID, PlatformPublicFileClient.SOURCE_SYSTEM, "CARD_BIN_LIBRARY_IMPORT", ACTOR,
                FileScanStatus.PENDING_SCAN.getCode(), null),
            metadata(FILE_ID, PlatformPublicFileClient.SOURCE_SYSTEM, PlatformPublicFileClient.REASON_CODE, ACTOR,
                FileScanStatus.PENDING_SCAN.getCode(), null).replace("\"accessLevel\":\"PUBLIC\"", "\"accessLevel\":\"PRIVATE\""))) {
            platform.groupedResponses(Map.of(UPLOAD_PATH + "/" + UPLOAD_ID + "/completion", receipt,
                FILES_PATH + "/" + FILE_ID, metadata));

            var response = send("POST", "/file-upload/v1/uploads/" + UPLOAD_ID + "/completion", COMPLETE_ROUTE, null);

            assertNotFound(response);
            assertAll(() -> assertFalse(response.body().contains(FILE_ID)),
                () -> assertEquals(List.of(AUTHORIZE_PATH, UPLOAD_PATH + "/" + UPLOAD_ID + "/completion",
                    FILES_PATH + "/" + FILE_ID), platform.requests().stream().map(RecordedRequest::path).toList()));
            resetBetweenCalls();
        }
        var warnings = output.getAll().lines().filter(line -> line.contains("Completed upload is not a public upload"))
            .toList();
        assertEquals(2, warnings.size(), warnings.toString());
        assertTrue(warnings.stream().allMatch(line -> line.contains("uploadId=" + UPLOAD_ID) && line.contains("fileId=" + FILE_ID)
            && !line.contains("logo.png") && !line.contains("CARD_BIN_LIBRARY_IMPORT")), warnings.toString());
    }

    /** 单个上传：本入口的文件整形为页面记录，本人上传标记 uploadedByMe，公开地址原样返回。 */
    @Test
    void test_fileDetail() throws Exception {
        platform.rawResponse(FILES_PATH + "/" + FILE_ID, metadata(FILE_ID, PlatformPublicFileClient.SOURCE_SYSTEM,
            PlatformPublicFileClient.REASON_CODE, ACTOR, FileScanStatus.AVAILABLE.getCode(), PUBLIC_URL));

        var response = send("GET", "/file-upload/v1/files/" + FILE_ID, DETAIL_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(FILE_ID, body.path("fileId").asString()),
            () -> assertEquals(FileAddress.of(Long.parseLong(FILE_ID)).value(), body.path("fileAddress").asString()),
            () -> assertEquals("logo.png", body.path("displayName").asString()),
            () -> assertEquals("image/png", body.path("mimeType").asString()),
            () -> assertEquals("20480", body.path("sizeBytes").asString()),
            () -> assertEquals(FileScanStatus.AVAILABLE.getCode(), body.path("scanStatus").asString()),
            () -> assertEquals(1, body.path("versionNo").asInt()),
            () -> assertEquals(ACTOR, body.path("uploaderAccountId").asString()),
            () -> assertTrue(body.path("uploadedByMe").asBoolean()),
            () -> assertEquals("2026-10-24T08:01:00Z", body.path("createTime").asString()),
            () -> assertEquals(PUBLIC_URL, body.path("publicUrl").asString()),
            () -> assertEquals(PUBLIC_URL + "?v=1", body.path("versionedUrl").asString()),
            () -> assertFalse(body.has("sourceSystem")), () -> assertFalse(body.has("contentHash")));
        assertEquals(List.of(AUTHORIZE_PATH, FILES_PATH + "/" + FILE_ID),
            platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** 其他原因（如卡 BIN 导入）或其他来源的文件按不存在返回 404，不外露元数据；Platform 不存在同样 404。 */
    @Test
    void test_otherFileNotFound() throws Exception {
        platform.rawResponse(FILES_PATH + "/" + FILE_ID, metadata(FILE_ID, PlatformPublicFileClient.SOURCE_SYSTEM,
            "CARD_BIN_LIBRARY_IMPORT", ACTOR, FileScanStatus.AVAILABLE.getCode(), null));
        assertNotFound(send("GET", "/file-upload/v1/files/" + FILE_ID, DETAIL_ROUTE, null));

        resetBetweenCalls();
        platform.rawResponse(FILES_PATH + "/" + FILE_ID, metadata(FILE_ID, "client-console-bff",
            PlatformPublicFileClient.REASON_CODE, ACTOR, FileScanStatus.AVAILABLE.getCode(), PUBLIC_URL));
        assertNotFound(send("GET", "/file-upload/v1/files/" + FILE_ID, DETAIL_ROUTE, null));

        resetBetweenCalls();
        platform.problem(FILES_PATH + "/" + FILE_ID, 404, FileErrorCode.FILE_NOT_FOUND.getCode());
        assertNotFound(send("GET", "/file-upload/v1/files/" + FILE_ID, DETAIL_ROUTE, null));
    }

    /** 最近上传：缺省近 7 天、第 1 页 10 条，固定原因与 PUBLIC，不限上传人；逐条整形并计算 uploadedByMe，分页原样保留。 */
    @Test
    void test_recentUploads() throws Exception {
        platform.rawResponse(FILES_PATH, page(1, 10, 12));

        var response = send("GET", "/file-upload/v1/files", LIST_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(1, body.path("page").asInt()), () -> assertEquals(10, body.path("size").asInt()),
            () -> assertEquals(12, body.path("total").asLong()), () -> assertEquals(2, body.path("items").size()),
            () -> assertEquals(FILE_ID, body.path("items").get(0).path("fileId").asString()),
            () -> assertTrue(body.path("items").get(0).path("uploadedByMe").asBoolean()),
            () -> assertEquals(PUBLIC_URL, body.path("items").get(0).path("publicUrl").asString()),
            () -> assertEquals(OTHER_FILE_ID, body.path("items").get(1).path("fileId").asString()),
            () -> assertFalse(body.path("items").get(1).path("uploadedByMe").asBoolean()),
            () -> assertTrue(body.path("items").get(1).path("publicUrl").isNull()));
        var forwarded = query(platformCall(FILES_PATH).query());
        Instant from = Instant.parse(forwarded.get("createdFrom"));
        Instant to = Instant.parse(forwarded.get("createdTo"));
        assertAll(() -> assertEquals(PlatformPublicFileClient.REASON_CODE, forwarded.get("reasonCode")),
            () -> assertEquals(PlatformPublicFileClient.ACCESS_LEVEL, forwarded.get("accessLevel")),
            () -> assertEquals("1", forwarded.get("page")), () -> assertEquals("10", forwarded.get("size")),
            () -> assertFalse(forwarded.containsKey("uploaderAccountId")),
            () -> assertEquals(Duration.ofDays(7), Duration.between(from, to)),
            () -> assertTrue(Duration.between(to, Instant.now()).abs().compareTo(Duration.ofMinutes(1)) < 0));
    }

    /** mine=true 以当前会话账号筛选上传人；显式时间范围与分页原样转发。 */
    @Test
    void test_recentUploadsMineAndRange() throws Exception {
        platform.rawResponse(FILES_PATH, page(2, 20, 21));

        var response = send("GET", "/file-upload/v1/files?mine=true&createdFrom=2026-10-01T00:00:00Z"
            + "&createdTo=2026-10-20T00:00:00Z&page=2&size=20", LIST_ROUTE, null);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(Map.of("createdFrom", "2026-10-01T00:00:00Z", "createdTo", "2026-10-20T00:00:00Z",
            "reasonCode", PlatformPublicFileClient.REASON_CODE, "accessLevel", PlatformPublicFileClient.ACCESS_LEVEL,
            "uploaderAccountId", ACTOR, "page", "2", "size", "20"), query(platformCall(FILES_PATH).query()));
    }

    /** Platform 文件策略拒绝 422、未配置 503 与范围不合法 400 按原状态与公共 Code 外显；回执不自洽按上游无效 502。 */
    @Test
    void test_platformFailures() throws Exception {
        platform.problem(UPLOAD_PATH, 422, FileErrorCode.FILE_POLICY_REJECTED.getCode());
        var rejected = send("POST", "/file-upload/v1/uploads", START_ROUTE, startBody());
        assertEquals(422, rejected.statusCode(), rejected.body());
        assertAll(() -> assertEquals(FileErrorCode.FILE_POLICY_REJECTED.getCode(),
                JSON.readTree(rejected.body()).path("code").asString()),
            () -> assertFalse(rejected.body().contains(PlatformOperationsSimulator.UPSTREAM_DETAIL_CANARY)));

        resetBetweenCalls();
        platform.problem(UPLOAD_PATH, 503, CommonErrorCode.SERVICE_UNAVAILABLE.getCode());
        var unavailable = send("POST", "/file-upload/v1/uploads", START_ROUTE, startBody());
        assertEquals(503, unavailable.statusCode(), unavailable.body());

        resetBetweenCalls();
        platform.problem(FILES_PATH, 400, CommonErrorCode.INVALID_REQUEST.getCode());
        var invalidRange = send("GET", "/file-upload/v1/files?createdFrom=2026-08-01T00:00:00Z"
            + "&createdTo=2026-10-20T00:00:00Z", LIST_ROUTE, null);
        assertEquals(400, invalidRange.statusCode(), invalidRange.body());
        assertEquals(CommonErrorCode.INVALID_REQUEST.getCode(), JSON.readTree(invalidRange.body()).path("code").asString());

        resetBetweenCalls();
        platform.rawResponse(UPLOAD_PATH + "/" + UPLOAD_ID + "/completion", completion(FileAddress.of(7409912388999L).value()));
        assertUpstreamInvalid(send("POST", "/file-upload/v1/uploads/" + UPLOAD_ID + "/completion", COMPLETE_ROUTE, null));

        resetBetweenCalls();
        platform.rawResponse(FILES_PATH + "/" + FILE_ID, metadata(OTHER_FILE_ID, PlatformPublicFileClient.SOURCE_SYSTEM,
            PlatformPublicFileClient.REASON_CODE, ACTOR, FileScanStatus.AVAILABLE.getCode(), PUBLIC_URL));
        assertUpstreamInvalid(send("GET", "/file-upload/v1/files/" + FILE_ID, DETAIL_ROUTE, null));

        resetBetweenCalls();
        platform.rawResponse(FILES_PATH, page(3, 10, 12));
        assertUpstreamInvalid(send("GET", "/file-upload/v1/files", LIST_ROUTE, null));
    }

    /** 当前会话授权失败时只调用 PLT-I02，不访问 Platform 文件接口。 */
    @Test
    void test_authorizationDenied() throws Exception {
        for (var request : List.of(
            List.of("GET", "/file-upload/v1/constraints", CONSTRAINTS_ROUTE),
            List.of("GET", "/file-upload/v1/files", LIST_ROUTE),
            List.of("GET", "/file-upload/v1/files/" + FILE_ID, DETAIL_ROUTE),
            List.of("POST", "/file-upload/v1/uploads", START_ROUTE),
            List.of("POST", "/file-upload/v1/uploads/" + UPLOAD_ID + "/completion", COMPLETE_ROUTE))) {
            platform.allow(false);
            var response = send(request.get(0), request.get(1), request.get(2),
                request.get(2).equals(START_ROUTE) ? startBody() : null);
            assertEquals(403, response.statusCode(), request + " " + response.body());
            assertOnlyAuthorized();
            resetBetweenCalls();
        }
    }

    /** 浏览器指定原因、级别、上传人或其他多余字段，非法类型或大小，未知 / 重复 Query、非法条数、半个时间范围、非法 ID、完成上传带 Body：访问 Platform 文件接口前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        for (String extra : List.of("reasonCode", "accessLevel", "uploaderAccountId", "operatorAccountId")) {
            assertRejectedLocally(400, send("POST", "/file-upload/v1/uploads", START_ROUTE, startBody().put(extra, "X")));
        }
        assertRejectedLocally(422, send("POST", "/file-upload/v1/uploads", START_ROUTE, startBody().put("mimeType", "Image/PNG")));
        assertRejectedLocally(422, send("POST", "/file-upload/v1/uploads", START_ROUTE, startBody().put("sizeBytes", "020480")));
        assertRejectedLocally(400, send("POST", "/file-upload/v1/uploads", START_ROUTE, startBody().put("sizeBytes", 20480)));
        assertRejectedLocally(400, send("POST", "/file-upload/v1/uploads/" + UPLOAD_ID + "/completion", COMPLETE_ROUTE,
            JSON.createObjectNode().put("fileId", FILE_ID)));
        for (String query : List.of("?sourceSystem=card", "?size=15", "?size=100", "?mine=true&mine=false",
            "?createdFrom=2026-10-01T00:00:00Z", "?createdTo=2026-10-01T00:00:00Z", "?page=0")) {
            assertRejectedLocally(400, send("GET", "/file-upload/v1/files" + query, LIST_ROUTE, null));
        }
        assertRejectedLocally(400, send("GET", "/file-upload/v1/files/abc", DETAIL_ROUTE, null));
        assertRejectedLocally(400, send("GET", "/file-upload/v1/constraints?accessLevel=PRIVATE", CONSTRAINTS_ROUTE, null));
        assertRejectedLocally(400, send("GET", "/file-upload/v1/constraints", CONSTRAINTS_ROUTE,
            JSON.createObjectNode().put("accessLevel", "PRIVATE")));
    }

    private void assertNotFound(HttpResponse<String> response) {
        assertEquals(404, response.statusCode(), response.body());
        assertAll(() -> assertEquals(FileErrorCode.FILE_NOT_FOUND.getCode(), JSON.readTree(response.body()).path("code").asString()),
            () -> assertFalse(response.body().contains("logo.png")));
    }

    private static void assertUpstreamInvalid(HttpResponse<String> response) {
        assertEquals(502, response.statusCode(), response.body());
        assertEquals(CommonErrorCode.UPSTREAM_INVALID_RESPONSE.getCode(), JSON.readTree(response.body()).path("code").asString());
    }

    /** 本地拒绝：指定状态，Platform 只收到 PLT-I02（或在授权前即被拒绝）；之后重置记录。 */
    private void assertRejectedLocally(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(platform.requests().stream().allMatch(request -> AUTHORIZE_PATH.equals(request.path())),
            platform.requests().toString());
        resetBetweenCalls();
    }

    /** Platform 只收到 PLT-I02。 */
    private void assertOnlyAuthorized() {
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** 同一用例内的下一次独立调用：先确认没有意外调用，再清空记录并恢复默认授权。 */
    private void resetBetweenCalls() {
        assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
        platform.reset();
        platform.useCommercialIdentity();
    }

    private RecordedRequest platformCall(String path) {
        return platform.requests().stream().filter(request -> path.equals(request.path())).findFirst().orElseThrow();
    }

    /** 页面开始上传请求体。 */
    private static ObjectNode startBody() {
        return JSON.createObjectNode().put("filename", "logo.png").put("mimeType", "image/png").put("sizeBytes", "20480");
    }

    /** Platform 完成回执。 */
    private static String completion(String fileAddress) {
        return "{\"uploadId\":\"%s\",\"fileId\":\"%s\",\"fileAddress\":\"%s\",\"completedAt\":\"2026-10-24T08:01:00Z\",\"versionNo\":1,\"accessLevel\":\"PUBLIC\"}"
            .formatted(UPLOAD_ID, FILE_ID, fileAddress);
    }

    /** Platform 文件元数据；公开地址为空时两个地址都为空。 */
    private static String metadata(String fileId, String source, String reason, String uploader, String scanStatus,
        String publicUrl) {
        String url = publicUrl == null ? "null" : "\"" + publicUrl + "\"";
        String versioned = publicUrl == null ? "null" : "\"" + publicUrl + "?v=1\"";
        return """
            {"fileId":"%s","fileAddress":"%s","sourceSystem":"%s","reasonCode":"%s","displayName":"logo.png",
             "mimeType":"image/png","sizeBytes":"20480","contentHash":"%s","scanStatus":"%s","objectStatus":"ACTIVE",
             "uploaderAccountId":"%s","createTime":"2026-10-24T08:01:00Z",
             "accessLevel":"PUBLIC","versionNo":1,"publicUrl":%s,"versionedUrl":%s,"pendingVersionNo":null}
            """.formatted(fileId, FileAddress.of(Long.parseLong(fileId)).value(), source, reason, "c".repeat(64), scanStatus,
            uploader, url, versioned);
    }

    /** Platform 分页：本人上传且可用的文件一条，他人上传且检查中的文件一条。 */
    private static String page(int page, int size, long total) {
        return "{\"items\":[%s,%s],\"page\":%d,\"size\":%d,\"total\":%d}".formatted(
            metadata(FILE_ID, PlatformPublicFileClient.SOURCE_SYSTEM, PlatformPublicFileClient.REASON_CODE, ACTOR,
                FileScanStatus.AVAILABLE.getCode(), PUBLIC_URL),
            metadata(OTHER_FILE_ID, PlatformPublicFileClient.SOURCE_SYSTEM, PlatformPublicFileClient.REASON_CODE,
                OTHER_ACCOUNT, FileScanStatus.SCANNING.getCode(), null), page, size, total);
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
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", "file-upload-command-0001")
            .header("Authorization", "Bearer browser-access-token").header("Cookie", "browser-session=canary")
            .header("Origin", "https://operations-console.local.test");
        if (!"GET".equals(method) || body != null) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(method, body == null
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** 解析 Query 为键值。 */
    private static Map<String, String> query(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        var result = new HashMap<String, String>();
        for (String pair : raw.split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }
}
