package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.*;
import static org.junit.jupiter.api.Assertions.*;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.operations.console.bff.ext.rest.ExternalApiEditorController;
import com.paytech.operations.console.bff.ext.rest.IntegrationApiManagementController;
import com.paytech.operations.console.bff.ext.rest.IntegrationImplementationManagementController;
import com.paytech.operations.console.bff.ext.rest.IntegrationManagementController;
import com.paytech.operations.console.bff.ext.rest.IntegrationScriptManagementController;
import com.paytech.operations.console.bff.ext.rest.StandardApiManagementController;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 外部 API 配置管理 30 个浏览器端点经真实 BFF → Platform HttpExchange 的转发合同：
 * 先当前授权再转发、可信身份与 Body/Query 原样传递、不转发 Idempotency-Key、上游 Problem 与 422 路径投影、
 * 返回编码一致性及上游扩展字段不外露。
 *
 * @see StandardApiManagementController
 * @see IntegrationManagementController
 * @see IntegrationApiManagementController
 * @see IntegrationScriptManagementController
 * @see IntegrationImplementationManagementController
 * @see ExternalApiEditorController
 */
class ExternalApiBrowserAutoTest extends OperationsBffHttpTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String KEY = "external-api-boundary-command-001";
    private static final String REQUEST_ID = "external-api-boundary-request";

    @Autowired
    private PlatformOperationsSimulator platform;

    @LocalServerPort
    private int port;

    /** 每例清空 Owner 模拟的请求与响应配置。 */
    @BeforeEach
    void setUpClassMocks() {
        platform.reset();
    }

    /** Owner 未配置的请求让用例失败。 */
    @AfterEach
    void verifyOwnerCalls() {
        assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
    }

    /** 30 个端点的请求与 Owner 响应样例。 */
    static Stream<JsonNode> endpoints() throws Exception {
        try (var input = ExternalApiBrowserAutoTest.class.getResourceAsStream("/external-api-boundary.json")) {
            var rows = new ArrayList<JsonNode>();
            JSON.readTree(input).forEach(rows::add);
            return rows.stream();
        }
    }

    static JsonNode endpoint(String id) throws Exception {
        return endpoints().filter(c -> id.equals(c.path("id").asString())).findFirst().orElseThrow();
    }

    /** 授权后按原方法与路径转发，身份、Body 与 Query 原样传递，响应逐字段相同，不转发 Idempotency-Key。 */
    @ParameterizedTest(name = "{index} 外部 API 转发")
    @MethodSource("endpoints")
    void test_endpointsForwardIdentityBodyAndQuery(JsonNode c) throws Exception {
        String path = c.path("path").asString();
        platform.rawResponse(path, JSON.writeValueAsString(c.path("response")));

        var response = send(c, c.path("query").asString(), c.path("request"), true);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(c.path("response"), JSON.readTree(response.body()));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(List.of(AUTHORIZE_PATH, path), platform.requests().stream().map(RecordedRequest::path).toList());
        var call = platform.requests().getLast();
        assertAll(() -> assertEquals(c.path("method").asString(), call.method()),
            () -> assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Idempotency-Key")),
            () -> assertNull(call.header("Authorization")),
            () -> assertNull(call.header("Cookie")));
        if (c.path("request").isNull()) {
            assertEquals(query(c.path("query").asString()), query(call.query()));
        } else {
            assertEquals(c.path("request"), JSON.readTree(call.body()));
        }
    }

    /** 当前授权被拒绝时不访问 Owner。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenialStopsOwnerCalls(JsonNode c) throws Exception {
        platform.allow(false);

        var response = send(c, c.path("query").asString(), c.path("request"), true);

        assertEquals(403, response.statusCode());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** Owner 的 400/404/409 Problem 原样返回，503 归为依赖不可用；均带本次请求标识。 */
    @ParameterizedTest
    @CsvSource({"400,BUSINESS_CHECK_FAILED,BUSINESS_CHECK_FAILED", "404,NOT_FOUND,NOT_FOUND",
        "409,CONCURRENT_REQUEST,CONCURRENT_REQUEST", "503,SERVICE_UNAVAILABLE,DEPENDENCY_UNAVAILABLE"})
    void test_ownerProblemsRemainVisible(int status, String code, String browserCode) throws Exception {
        var c = endpoint("E26");
        platform.problem(c.path("path").asString(), status, code);

        var response = send(c, "", c.path("request"), true);

        assertEquals(status, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertEquals(browserCode, body.path("code").asString());
        assertEquals(REQUEST_ID, body.path("requestId").asString());
    }

    /** Owner 422 只保留属于本次请求体的字段路径（含 BODY:amount 这类键），说明统一为安全文案，不外露上游文本。 */
    @Test
    void test_validationPathsProjected() throws Exception {
        var c = endpoint("E25");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"%s","code":"VALIDATION_ERROR",
             "requestId":"%s","timestamp":"2026-10-13T06:00:00.000000Z","errors":[
              {"field":"flowDefinition.nodes[0].request.BODY:orderRef.path","message":"%s"},
              {"field":"flowDefinition.edges","message":"节点 createOrder 必须恰好有一条兜底连线"},
              {"field":"unknownRoot.value","message":"x"}]}
            """.formatted(UPSTREAM_DETAIL_CANARY, UPSTREAM_REQUEST_ID, UPSTREAM_EXTENSION_CANARY);
        platform.rawResponse(c.path("path").asString(), 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", UPSTREAM_REQUEST_ID));

        var response = send(c, "", c.path("request"), true);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        var fields = new ArrayList<String>();
        body.path("errors").forEach(error -> {
            fields.add(error.path("field").asString());
            assertEquals("请检查此处配置", error.path("message").asString());
        });
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(List.of("flowDefinition.edges", "flowDefinition.nodes[0].request.BODY:orderRef.path"),
                fields),
            () -> assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY)),
            () -> assertFalse(response.body().contains(UPSTREAM_EXTENSION_CANARY)));
    }

    /** 详情编码或写结果编码与请求不一致视为损坏响应。 */
    @ParameterizedTest
    @CsvSource({"E02,standardApiCode", "E12,apiCode", "E18,scriptCode", "E24,standardApiCode", "E13,objectCode",
        "E26,objectCode"})
    void test_identityMismatchRejected(String id, String field) throws Exception {
        var c = endpoint(id);
        var body = (ObjectNode) c.path("response").deepCopy();
        body.put(field, "OTHER");
        platform.rawResponse(c.path("path").asString(), JSON.writeValueAsString(body));

        assertEquals(502, send(c, "", c.path("request"), true).statusCode());
    }

    /** 分页窗口与上游不一致视为损坏响应。 */
    @Test
    void test_pageWindowMismatchRejected() throws Exception {
        var c = endpoint("E01");
        var body = (ObjectNode) c.path("response").deepCopy();
        ((ArrayNode) body.get("items")).add(body.get("items").get(0).deepCopy());
        platform.rawResponse(c.path("path").asString(), JSON.writeValueAsString(body));

        assertEquals(502, send(c, c.path("query").asString(), c.path("request"), true).statusCode());
    }

    /** 上游新增字段不投影到浏览器；定义 JSON 内部原样传递。 */
    @Test
    void test_extraFieldsNotExposed() throws Exception {
        var c = endpoint("E12");
        var body = (ObjectNode) c.path("response").deepCopy();
        body.put("internalApiId", "SECRET_CANARY");
        platform.rawResponse(c.path("path").asString(), JSON.writeValueAsString(body));

        var response = send(c, "", c.path("request"), true);

        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("SECRET_CANARY"));
        assertEquals(c.path("response").path("definition"), JSON.readTree(response.body()).path("definition"));
    }

    /** 本地字段约束返回 422，未知属性、流程内重复键与未知 Query 返回 400，写请求缺少 Origin 返回 403；均不访问 Owner。 */
    @Test
    void test_requestBoundaries() throws Exception {
        var create = endpoint("E03");
        var missingReason = (ObjectNode) create.path("request").deepCopy();
        missingReason.remove("reason");
        var unknown = (ObjectNode) create.path("request").deepCopy();
        unknown.put("version", 1);

        assertEquals(422, send(create, "", missingReason, true).statusCode());
        assertEquals(400, send(create, "", unknown, true).statusCode());
        assertEquals(400, sendRaw(endpoint("E25"), """
            {"standardApiCode":"PAY","flowDefinition":{"timeoutMs":1,"timeoutMs":2},"reason":"新增"}""").statusCode());
        assertEquals(400, send(endpoint("E01"), "?unknown=x", endpoint("E01").path("request"), true).statusCode());
        assertEquals(403, send(create, "", create.path("request"), false).statusCode());
        assertTrue(platform.requests().stream().allMatch(request -> request.path().equals(AUTHORIZE_PATH)));
    }

    /** 通过真实 BFF HTTP 入口发送浏览器请求；浏览器照常携带 Idempotency-Key。 */
    private HttpResponse<String> send(JsonNode c, String query, JsonNode body, boolean origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + c.path("path").asString() + query))
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", c.path("routeCode").asString()).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", KEY);
        if (origin) {
            builder.header("Origin", "https://operations-console.local.test");
        }
        if (!body.isNull()) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(c.path("method").asString(), body.isNull()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** 发送原始 JSON 文本，用于构造对象模型无法表达的重复键。 */
    private HttpResponse<String> sendRaw(JsonNode c, String json) throws Exception {
        return HTTP.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + c.path("path").asString()))
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", c.path("routeCode").asString()).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Origin", "https://operations-console.local.test")
            .header("Content-Type", "application/json")
            .method(c.path("method").asString(), HttpRequest.BodyPublishers.ofString(json)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** 解析查询串为键值对，用于比较转发前后的筛选条件。 */
    private static Map<String, String> query(String raw) {
        if (raw == null || raw.isEmpty()) {
            return Map.of();
        }
        var result = new HashMap<String, String>();
        for (String pair : raw.replaceFirst("^\\?", "").split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                URLDecoder.decode(parts[1], StandardCharsets.UTF_8));
        }
        return result;
    }
}
