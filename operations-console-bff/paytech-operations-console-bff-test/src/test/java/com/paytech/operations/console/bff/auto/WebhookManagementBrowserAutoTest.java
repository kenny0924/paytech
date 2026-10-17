package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_DETAIL_CANARY;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_REQUEST_ID;
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
import com.paytech.operations.console.bff.ext.rest.WebhookManagementController;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发 Webhook 管理的 10 个端点；Platform 由 Simulator 承担会话授权与响应。
 *
 * @see WebhookManagementController
 */
class WebhookManagementBrowserAutoTest extends OperationsBffHttpTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String REQUEST_ID = "webhook-boundary-request";

    @Autowired
    private PlatformOperationsSimulator platform;

    @LocalServerPort
    private int port;

    /** 每例重置模拟 Owner 的请求与响应。 */
    @BeforeEach
    void setUpClassMocks() {
        platform.reset();
    }

    /** Owner 不得收到未配置的请求。 */
    @AfterEach
    void verifyOwnerCalls() {
        assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString());
    }

    /** 边界清单中的全部端点。 */
    static Stream<JsonNode> endpoints() throws Exception {
        try (var input = WebhookManagementBrowserAutoTest.class.getResourceAsStream("/webhook-management-boundary.json")) {
            var rows = new ArrayList<JsonNode>();
            JSON.readTree(input).forEach(rows::add);
            return rows.stream();
        }
    }

    /** 指定编号的端点样例。 */
    static JsonNode endpoint(String id) throws Exception {
        return endpoints().filter(c -> id.equals(c.path("id").asString())).findFirst().orElseThrow();
    }

    /**
     * 方法、路径、身份、查询参数与命令正文准确转发，状态码与响应原样返回且不缓存；
     * 不转发 Idempotency-Key、Authorization 或 Cookie，测试事件不带 Body
     */
    @ParameterizedTest(name = "{index} Webhook 管理转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode c) throws Exception {
        String path = c.path("path").asString();
        int status = c.path("status").asInt();
        platform.rawResponse(path, status, "application/json", JSON.writeValueAsString(c.path("response")));

        var response = send(c, c.path("query").asString(), c.path("request"), true);

        assertEquals(status, response.statusCode(), response.body());
        assertEquals(c.path("response"), JSON.readTree(response.body()));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        assertEquals(List.of(AUTHORIZE_PATH, path), platform.requests().stream().map(RecordedRequest::path).toList());
        var call = platform.requests().getLast();
        assertAll(() -> assertEquals(c.path("method").asString(), call.method()),
            () -> assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (c.path("request").isNull()) {
            assertEquals(query(c.path("query").asString()), query(call.query()));
            assertTrue(call.body() == null || call.body().isEmpty(), "无 Body 的请求不得转发 Body");
        } else {
            assertEquals(c.path("request"), JSON.readTree(call.body()));
        }
    }

    /** 当前会话授权失败时不访问 Webhook Owner。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode c) throws Exception {
        platform.allow(false);

        var response = send(c, c.path("query").asString(), c.path("request"), true);

        assertEquals(403, response.statusCode());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** Owner 的 400/403/404/409/503 带本次请求标识透传，不外露上游说明。 */
    @ParameterizedTest
    @CsvSource({"400,BUSINESS_CHECK_FAILED", "403,OPERATIONS_PERMISSION_DENIED", "404,NOT_FOUND",
        "409,CONCURRENT_REQUEST", "503,SERVICE_UNAVAILABLE"})
    void test_ownerProblem(int status, String code) throws Exception {
        var c = endpoint("W05");
        platform.problem(c.path("path").asString(), status, code);

        var response = send(c, "", c.path("request"), true);

        assertEquals(status, response.statusCode());
        assertEquals(REQUEST_ID, JSON.readTree(response.body()).path("requestId").asString());
        assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY));
    }

    /** Owner 422 只保留本次请求体内的字段路径，说明换为安全文案。 */
    @Test
    void test_validationPathsProjected() throws Exception {
        var c = endpoint("W03");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"%s","code":"VALIDATION_ERROR",
             "requestId":"%s","timestamp":"2026-10-16T06:00:00.000000Z","errors":[
              {"field":"url","message":"地址必须解析到公网"},{"field":"eventTypes[1]","message":"未知事件"},
              {"field":"clientId","message":"x"}]}
            """.formatted(UPSTREAM_DETAIL_CANARY, UPSTREAM_REQUEST_ID);
        platform.rawResponse(c.path("path").asString(), 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", UPSTREAM_REQUEST_ID));

        var response = send(c, "", c.path("request"), true);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(2, body.path("errors").size()),
            () -> assertEquals("eventTypes[1]", body.path("errors").get(0).path("field").asString()),
            () -> assertEquals("url", body.path("errors").get(1).path("field").asString()),
            () -> assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY)));
    }

    /** 上游多出的字段不投影到浏览器；端点或投递 ID 不一致、重复或分页不自洽视为无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var detail = endpoint("W09");
        var extra = (ObjectNode) detail.path("response").deepCopy();
        extra.put("signingSecret", "whsec_SECRET-CANARY");
        platform.rawResponse(detail.path("path").asString(), JSON.writeValueAsString(extra));
        var response = send(detail, "", detail.path("request"), true);
        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("SECRET-CANARY"));
        assertEquals(detail.path("response"), JSON.readTree(response.body()));

        platform.reset();
        var other = (ObjectNode) detail.path("response").deepCopy();
        other.put("deliveryId", "880000000000073999");
        platform.rawResponse(detail.path("path").asString(), JSON.writeValueAsString(other));
        assertEquals(502, send(detail, "", detail.path("request"), true).statusCode());

        platform.reset();
        var status = endpoint("W05");
        platform.rawResponse(status.path("path").asString(), "{\"endpointId\":\"880000000000072999\",\"version\":3}");
        assertEquals(502, send(status, "", status.path("request"), true).statusCode());

        platform.reset();
        var endpoints = endpoint("W02");
        var duplicated = (ArrayNode) endpoints.path("response").deepCopy();
        duplicated.add(duplicated.get(0).deepCopy());
        platform.rawResponse(endpoints.path("path").asString(), JSON.writeValueAsString(duplicated));
        assertEquals(502, send(endpoints, "", endpoints.path("request"), true).statusCode());

        platform.reset();
        var list = endpoint("W01");
        var page = (ObjectNode) list.path("response").deepCopy();
        page.put("total", 0);
        platform.rawResponse(list.path("path").asString(), JSON.writeValueAsString(page));
        assertEquals(502, send(list, list.path("query").asString(), list.path("request"), true).statusCode());
    }

    /** 未知或缺失 Body 字段、超限 Body、测试事件携带 Body、未知 Query、缺少 Origin 的写请求在访问 Owner 前被拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var c = endpoint("W04");
        var unknown = (ObjectNode) c.path("request").deepCopy();
        unknown.put("signingSecret", "whsec_x");
        assertEquals(400, send(c, "", unknown, true).statusCode());
        var missing = (ObjectNode) c.path("request").deepCopy();
        missing.remove("expectedVersion");
        assertEquals(400, send(c, "", missing, true).statusCode());
        var oversized = (ObjectNode) c.path("request").deepCopy();
        oversized.put("reason", "x".repeat(8192));
        assertEquals(413, send(c, "", oversized, true).statusCode());
        assertEquals(403, send(c, "", c.path("request"), false).statusCode());

        var rotate = endpoint("W06");
        var implicit = (ObjectNode) rotate.path("request").deepCopy();
        implicit.remove("immediate");
        assertEquals(400, send(rotate, "", implicit, true).statusCode());

        var test = endpoint("W07");
        assertEquals(400, send(test, "", JSON.createObjectNode(), true).statusCode());

        var list = endpoint("W08");
        assertEquals(400, send(list, list.path("query").asString() + "&unknown=1", list.path("request"), true)
            .statusCode());

        assertTrue(platform.requests().stream().allMatch(r -> r.path().equals(AUTHORIZE_PATH)));
    }

    /** 经真实 BFF HTTP 入口发送浏览器请求，带 Gateway 可信身份；写方法统一声明 JSON。 */
    private HttpResponse<String> send(JsonNode c, String query, JsonNode body, boolean origin) throws Exception {
        String method = c.path("method").asString();
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + c.path("path").asString() + query))
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", c.path("routeCode").asString()).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", "webhook-command-001");
        if (origin) {
            builder.header("Origin", "https://operations-console.local.test");
        }
        if (!"GET".equals(method)) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(method, body.isNull() ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** 解析 Query 为键值，不含空值。 */
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
