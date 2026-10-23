package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_DETAIL_CANARY;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.ext.rest.FileStorageAdminController;
import com.paytech.operations.console.bff.test.base.FinanceBrowserAutoTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.platform.support.api.enums.FileStorageBucketStatus;
import com.paytech.platform.support.api.enums.FileStorageCredentialMode;
import com.paytech.platform.support.api.enums.FileStorageInstanceStatus;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 维护 Platform 存储实例、桶、写入路由与检查策略并查询文件记录：存储管理页与文件记录页
 * 24 个端点（S01—S24）
 * <li>Platform 由 Loopback Simulator 承担，PLT-I02 授权与 Owner 调用记录在同一 Simulator。</li>
 * <li>新建实例与更换密钥的请求含存储密钥：断言日志、错误响应都不含密钥原值；文件下载地址同样不进入日志。</li>
 * <li>写入路由以（来源系统, 访问级别）为路径键；文件记录列表与下载的 Query 原样转发。</li>
 *
 * @see FileStorageAdminController#listInstances(com.paytech.operations.console.bff.service.model.OperatorContext)
 * @see FileStorageAdminController#createInstance(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      com.paytech.platform.support.api.filestorage.request.CreateStorageInstanceRequestBody)
 * @see FileStorageAdminController#updateInstance(com.paytech.operations.console.bff.service.model.OperatorContext, String,
 *      com.paytech.platform.support.api.filestorage.request.UpdateStorageInstanceRequestBody)
 * @see FileStorageAdminController#replaceCredential(com.paytech.operations.console.bff.service.model.OperatorContext, String,
 *      com.paytech.platform.support.api.filestorage.request.ReplaceStorageCredentialRequestBody)
 * @see FileStorageAdminController#changeInstanceStatus(com.paytech.operations.console.bff.service.model.OperatorContext, String,
 *      com.paytech.platform.support.api.filestorage.request.ChangeStorageStatusRequestBody)
 * @see FileStorageAdminController#registerBucket(com.paytech.operations.console.bff.service.model.OperatorContext, String,
 *      com.paytech.platform.support.api.filestorage.request.RegisterStorageBucketRequestBody)
 * @see FileStorageAdminController#checkBucket(com.paytech.operations.console.bff.service.model.OperatorContext, String, String)
 * @see FileStorageAdminController#changeBucketStatus(com.paytech.operations.console.bff.service.model.OperatorContext, String,
 *      String, com.paytech.platform.support.api.filestorage.request.ChangeStorageStatusRequestBody)
 * @see FileStorageAdminController#listRoutes(com.paytech.operations.console.bff.service.model.OperatorContext)
 * @see FileStorageAdminController#createRoute(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      com.paytech.platform.support.api.filestorage.request.CreateStorageRouteRequestBody)
 * @see FileStorageAdminController#updateRoute(com.paytech.operations.console.bff.service.model.OperatorContext, String, String,
 *      com.paytech.platform.support.api.filestorage.request.UpdateStorageRouteRequestBody)
 * @see FileStorageAdminController#deleteRoute(com.paytech.operations.console.bff.service.model.OperatorContext, String, String,
 *      com.paytech.platform.support.api.filestorage.request.DeleteStorageRouteRequestBody)
 * @see FileStorageAdminController#updateBucket(com.paytech.operations.console.bff.service.model.OperatorContext, String, String,
 *      com.paytech.platform.support.api.filestorage.request.UpdateStorageBucketRequestBody)
 * @see FileStorageAdminController#listScanPolicies(com.paytech.operations.console.bff.service.model.OperatorContext)
 * @see FileStorageAdminController#getScanPolicy(com.paytech.operations.console.bff.service.model.OperatorContext, String)
 * @see FileStorageAdminController#createScanPolicy(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRequestBody)
 * @see FileStorageAdminController#createScanPolicyRevision(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      String, com.paytech.platform.support.api.filestorage.request.CreateScanPolicyRevisionRequestBody)
 * @see FileStorageAdminController#updateScanPolicyRevision(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      String, int, com.paytech.platform.support.api.filestorage.request.UpdateScanPolicyRevisionRequestBody)
 * @see FileStorageAdminController#discardScanPolicyRevision(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      String, int, com.paytech.platform.support.api.filestorage.request.DiscardScanPolicyRevisionRequestBody)
 * @see FileStorageAdminController#activateScanPolicyRevision(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      String, int, com.paytech.platform.support.api.filestorage.request.ActivateScanPolicyRevisionRequestBody)
 * @see FileStorageAdminController#retireScanPolicy(com.paytech.operations.console.bff.service.model.OperatorContext, String,
 *      com.paytech.platform.support.api.filestorage.request.RetireScanPolicyRequestBody)
 * @see FileStorageAdminController#listFileRecords(com.paytech.operations.console.bff.service.model.OperatorContext,
 *      java.time.Instant, java.time.Instant, String, String, String, String, String, Boolean, Integer, Integer)
 * @see FileStorageAdminController#getFileRecord(com.paytech.operations.console.bff.service.model.OperatorContext, String)
 * @see FileStorageAdminController#downloadFileRecord(com.paytech.operations.console.bff.service.model.OperatorContext, String,
 *      Integer)
 * @author Claude
 */
@ExtendWith(OutputCaptureExtension.class)
class FileStorageBrowserAutoTest extends FinanceBrowserAutoTestBase {
    private static final String BOUNDARY = "/file-storage-boundary.json";
    private static final String OWNER_REQUEST_ID = "platform-storage-request";
    private static final String SECRET_CANARY = "SECRET-CANARY-storage-key";
    private static final String DOWNLOAD_CANARY = "synthetic-download-canary";

    static Stream<JsonNode> endpoints() {
        return rows(BOUNDARY);
    }

    /** 方法、Owner 路径、Query、Body 与操作人 Header 准确转发一次，响应原样返回；查询同样携带操作人，浏览器凭据不转发。 */
    @ParameterizedTest(name = "{index} 存储管理转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode row) throws Exception {
        platform.rawResponse(ownerPath(row), JSON.writeValueAsString(row.path("response")));

        var response = send(row);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
        var call = ownerCall();
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertEquals(ownerPath(row), call.path()),
            () -> assertEquals(query(row.path("query").asString()), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (row.path("request").isNull()) {
            assertTrue(call.body().isEmpty(), "无 Body 的请求不得转发 Body");
        } else {
            assertEquals(row.path("request"), JSON.readTree(call.body()));
        }
    }

    /** 当前会话授权失败时只调用 PLT-I02，不访问存储管理接口。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode row) throws Exception {
        platform.allow(false);

        var response = send(row);

        assertEquals(403, response.statusCode(), response.body());
        assertOnlyAuthorized();
    }

    /** 未知或缺失字段、类型强转、越界取值、非法路径、列表带 Query 或 Body、检查带 Body 均在访问 Platform 前拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var list = row(BOUNDARY, "S01");
        assertRejectedBeforePlatform(400, send(list, "?status=ACTIVE", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(list, "", JSON.createObjectNode(), true));

        var create = row(BOUNDARY, "S02");
        assertRejectedBeforePlatform(400, send(create, "", with(create, "status", FileStorageInstanceStatus.ACTIVE.getCode()), true));
        assertRejectedBeforePlatform(400, send(create, "", with(create, "pathStyleAccess", "true"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "instanceCode", "minio"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "credentialMode", "ROOT"), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "connectTimeoutMs", 60000), true));
        assertRejectedBeforePlatform(422, send(create, "", with(create, "reason", " "), true));

        var update = row(BOUNDARY, "S03");
        var missingVersion = (ObjectNode) update.path("request").deepCopy();
        missingVersion.remove("expectedVersion");
        assertRejectedBeforePlatform(422, send(update, "", missingVersion, true));
        assertRejectedBeforePlatform(400, send(withPath(update, "/storage-admin/v1/instances/minio"), "", update.path("request"), true));

        var check = row(BOUNDARY, "S07");
        assertRejectedBeforePlatform(400, send(check, "", JSON.createObjectNode().put("force", true), true));
        assertRejectedBeforePlatform(400,
            send(withPath(check, "/storage-admin/v1/instances/MINIO/buckets/Bad_Bucket/check"), "", JSON.nullNode(), true));

        var status = row(BOUNDARY, "S08");
        assertRejectedBeforePlatform(422, send(status, "", with(status, "status", FileStorageBucketStatus.DRAFT.getCode()), true));

        var route = row(BOUNDARY, "S10");
        assertRejectedBeforePlatform(422, send(route, "", with(route, "maxUploadSeconds", 30), true));
        assertRejectedBeforePlatform(422, send(route, "", with(route, "sourceSystem", "Payment"), true));
        var delete = row(BOUNDARY, "S12");
        assertRejectedBeforePlatform(400, send(withPath(delete, "/storage-admin/v1/routes/Bad_Source/PUBLIC"), "", delete.path("request"), true));
        assertRejectedBeforePlatform(400,
            send(withPath(delete, "/storage-admin/v1/routes/client-master-service/INTERNAL"), "", delete.path("request"), true));
        var bucket = row(BOUNDARY, "S06");
        assertRejectedBeforePlatform(422, send(bucket, "", with(bucket, "accessLevel", "INTERNAL"), true));
        var missingLevel = (ObjectNode) route.path("request").deepCopy();
        missingLevel.remove("accessLevel");
        assertRejectedBeforePlatform(422, send(route, "", missingLevel, true));

        var policy = row(BOUNDARY, "S16");
        assertRejectedBeforePlatform(422, send(policy, "", with(policy, "maxSizeBytes", "0"), true));
        assertRejectedBeforePlatform(400, send(policy, "", with(policy, "maxSizeBytes", 2097152), true));
        assertRejectedBeforePlatform(422, send(policy, "", with(policy, "policyCode", "logo"), true));
        var revision = row(BOUNDARY, "S18");
        assertRejectedBeforePlatform(400, send(withPath(revision, "/storage-admin/v1/scan-policies/CLIENT_ONBOARDING_FORMAT/revisions/0"), "",
            revision.path("request"), true));
        assertRejectedBeforePlatform(400, send(withPath(revision, "/storage-admin/v1/scan-policies/lower/revisions/3"), "",
            revision.path("request"), true));

        var files = row(BOUNDARY, "S22");
        assertRejectedBeforePlatform(400, send(files, "?createdFrom=yesterday", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(files, "?fileId=abc", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(files, "?accessLevel=ALL", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(files, "?size=500", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(files, "?keyword=logo", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(files, "?scanStatus=", JSON.nullNode(), true));
        var download = row(BOUNDARY, "S24");
        assertRejectedBeforePlatform(400, send(download, "?versionNo=0", JSON.nullNode(), true));
        assertRejectedBeforePlatform(400, send(download, "", JSON.createObjectNode().put("versionNo", 1), true));
        assertRejectedBeforePlatform(400, send(withPath(download, "/storage-admin/v1/files/0123/download"), "", JSON.nullNode(), true));

        assertEquals(403, send(create, "", create.path("request"), false).statusCode());
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals));
    }

    /** 业务拒绝保留 Platform 状态、公共 Code 与原因（如被写入路由引用不能停用、默认路由不能删除），使用浏览器 Request ID。 */
    @ParameterizedTest
    @CsvSource({"S05,400,BUSINESS_CHECK_FAILED,存储实例有写入路由引用，不能停用: MINIO",
        "S08,400,BUSINESS_CHECK_FAILED,存储桶最近一次检查未通过或已超过 10 分钟: MINIO/mor-workflow-files",
        "S11,400,BUSINESS_CHECK_FAILED,目标存储桶未启用: MINIO/mor-workflow-files",
        "S03,404,NOT_FOUND,存储实例不存在: MINIO", "S04,409,CONCURRENT_REQUEST,存储实例已被修改: MINIO",
        "S13,400,BUSINESS_CHECK_FAILED,只有草稿公开桶可以修改对外地址: MINIO/mor-public-assets",
        "S17,409,CONCURRENT_REQUEST,检查策略已有草稿修订: CLIENT_ONBOARDING_FORMAT",
        "S21,400,BUSINESS_CHECK_FAILED,检查策略被写入路由引用，不能停用: WORKFLOW_ATTACHMENT",
        "S23,404,NOT_FOUND,文件不存在: 239061120044503040",
        "S24,409,FILE_STATE_CONFLICT,文件版本不可下载: 239061120044503040"})
    void test_ownerRejectionShowsPlatformReason(String id, int status, String code, String detail) throws Exception {
        var row = row(BOUNDARY, id);
        platform.rawResponse(ownerPath(row), status, "application/problem+json", problem(status, code, detail),
            Map.of("X-Request-Id", OWNER_REQUEST_ID));

        var response = send(row);

        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code, body.path("code").asString()),
            () -> assertEquals(detail, body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()));
        ownerCall();
    }

    /** Platform 503（Vault 或存储不可用）映射为依赖不可用，不外露 Platform 原文。 */
    @ParameterizedTest
    @CsvSource({"S02", "S04", "S07", "S24"})
    void test_ownerUnavailable(String id) throws Exception {
        var row = row(BOUNDARY, id);
        platform.problem(ownerPath(row), 503, "SERVICE_UNAVAILABLE");

        var response = send(row);

        assertEquals(503, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals("DEPENDENCY_UNAVAILABLE", body.path("code").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY)));
        ownerCall();
    }

    /** Platform 422 只保留本次请求体内存在的字段路径，不传播远端文案与密钥原值。 */
    @Test
    void test_ownerValidationPathsProjected() throws Exception {
        var row = row(BOUNDARY, "S02");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"remote-private-detail","code":"VALIDATION_ERROR",
             "requestId":"platform-fields-request","timestamp":"2026-10-22T00:00:00.123456Z","errors":[
              {"field":"endpoint","message":"remote-private-message"},{"field":"privateField","message":"unknown"}]}
            """;
        platform.rawResponse(ownerPath(row), 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", "platform-fields-request"));

        var response = send(row, "", with(row, "secretKey", SECRET_CANARY), true);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(1, body.path("errors").size()),
            () -> assertEquals("endpoint", body.path("errors").get(0).path("field").asString()),
            () -> assertFalse(response.body().contains("remote-private")),
            () -> assertFalse(response.body().contains("privateField")),
            () -> assertFalse(response.body().contains(SECRET_CANARY)));
    }

    /** 新建实例、更换密钥成功或被本地拒绝时，密钥原值不进入日志与响应；Platform 收到原值且只收到一次。 */
    @Test
    void test_secretsNeverLoggedOrEchoed(CapturedOutput output) throws Exception {
        var create = row(BOUNDARY, "S02");
        platform.rawResponse(ownerPath(create), JSON.writeValueAsString(create.path("response")));
        var created = send(create, "", with(create, "secretKey", SECRET_CANARY), true);
        assertEquals(200, created.statusCode(), created.body());
        assertTrue(ownerCall().body().contains(SECRET_CANARY), "Platform 应收到本次密钥");
        resetBetweenCalls();

        var credential = row(BOUNDARY, "S04");
        var invalid = with(credential, "secretKey", SECRET_CANARY);
        invalid.put("credentialMode", "ROOT");
        var rejected = send(credential, "", invalid, true);
        assertEquals(422, rejected.statusCode(), rejected.body());
        assertOnlyAuthorized();

        assertAll(() -> assertFalse(created.body().contains(SECRET_CANARY)),
            () -> assertFalse(rejected.body().contains(SECRET_CANARY)),
            () -> assertFalse(output.getAll().contains(SECRET_CANARY), "存储密钥不得进入日志"),
            () -> assertFalse(output.getAll().contains("synthetic-access-key"), "Access Key 不得进入日志"));
    }

    /** Platform 返回合同外字段（如凭据 Token）时 BFF 不投影该字段。 */
    @Test
    void test_unknownUpstreamFieldNotProjected() throws Exception {
        var list = row(BOUNDARY, "S01");
        var upstream = (ObjectNode) list.path("response").deepCopy();
        ((ObjectNode) upstream.path("items").get(0)).put("credentialVaultToken", "vault-token-canary");
        platform.rawResponse(ownerPath(list), JSON.writeValueAsString(upstream));

        var response = send(list);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(list.path("response"), JSON.readTree(response.body()));
        assertFalse(response.body().contains("vault-token-canary"));
    }

    /** 文件下载地址只出现在响应中，不进入日志；缺省版本号时不向 Platform 传 versionNo。 */
    @Test
    void test_downloadUrlNotLogged(CapturedOutput output) throws Exception {
        var download = row(BOUNDARY, "S24");
        platform.rawResponse(ownerPath(download), JSON.writeValueAsString(download.path("response")));

        var response = send(download, "", JSON.nullNode(), true);

        assertEquals(200, response.statusCode(), response.body());
        assertTrue(response.body().contains(DOWNLOAD_CANARY));
        var call = ownerCall();
        assertAll(() -> assertEquals(Map.of(), query(call.query())), () -> assertTrue(call.body().isEmpty()),
            () -> assertFalse(output.getAll().contains(DOWNLOAD_CANARY), "下载地址不得进入日志"));
    }

    /** 默认路由以 * 原样作为 Platform 路径段并带访问级别，凭据方式切换为 IAM 角色时不需要密钥。 */
    @Test
    void test_defaultRouteAndIamRoleCredential() throws Exception {
        var route = row(BOUNDARY, "S11");
        platform.rawResponse("/storage-admin/v1/routes/*/PRIVATE", JSON.writeValueAsString(route.path("response")));
        assertEquals(200, send(route).statusCode());
        assertEquals("/storage-admin/v1/routes/*/PRIVATE", ownerCall().path());
        resetBetweenCalls();

        var credential = row(BOUNDARY, "S04");
        var iamRole = with(credential, "credentialMode", FileStorageCredentialMode.IAM_ROLE.getCode());
        iamRole.putNull("accessKey");
        iamRole.putNull("secretKey");
        platform.rawResponse(ownerPath(credential), JSON.writeValueAsString(credential.path("response")));

        var response = send(credential, "", iamRole, true);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(iamRole, JSON.readTree(ownerCall().body()));
    }

    /** Platform 严格 Problem 响应体。 */
    private static String problem(int status, String code, String detail) {
        return JSON.writeValueAsString(JSON.createObjectNode().put("type", "about:blank")
            .put("title", org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase()).put("detail", detail)
            .put("code", code).put("requestId", OWNER_REQUEST_ID).put("timestamp", "2026-10-22T00:00:00.123456Z"));
    }

    /** 本地拒绝：指定状态且 Platform 至多收到授权请求；之后为下一次调用重置记录。 */
    private void assertRejectedBeforePlatform(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(platform.requests().stream().map(RecordedRequest::path).allMatch(AUTHORIZE_PATH::equals),
            platform.requests().toString());
        resetBetweenCalls();
    }

    /** Platform 只收到一次 PLT-I02 授权。 */
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

    /** 复制样例并替换 BFF 路径。 */
    private static JsonNode withPath(JsonNode row, String path) {
        var copy = (ObjectNode) row.deepCopy();
        copy.put("path", path);
        return copy;
    }

    /** 复制样例请求并覆盖一个字段。 */
    private static ObjectNode with(JsonNode row, String field, Object value) {
        var node = (ObjectNode) row.path("request").deepCopy();
        if (value instanceof Integer number) {
            node.put(field, number);
        } else {
            node.put(field, value.toString());
        }
        return node;
    }
}
