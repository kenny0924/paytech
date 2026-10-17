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
import com.paytech.operations.console.bff.ext.rest.NotificationManagementController;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

/**
 * 浏览器经真实 BFF 与 HttpExchange 转发通知管理的 16 个端点；Platform 由 Simulator 承担会话授权与响应。
 *
 * @see NotificationManagementController
 */
class NotificationManagementBrowserAutoTest extends OperationsBffHttpTestBase {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String REQUEST_ID = "notification-boundary-request";

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
        try (var input = NotificationManagementBrowserAutoTest.class
            .getResourceAsStream("/notification-management-boundary.json")) {
            var rows = new ArrayList<JsonNode>();
            JSON.readTree(input).forEach(rows::add);
            return rows.stream();
        }
    }

    /** 指定编号的端点样例。 */
    static JsonNode endpoint(String id) throws Exception {
        return endpoints().filter(c -> id.equals(c.path("id").asString())).findFirst().orElseThrow();
    }

    /** 身份、查询参数与命令正文准确转发，不转发 Idempotency-Key、Authorization 或 Cookie。 */
    @ParameterizedTest(name = "{index} 通知管理转发")
    @MethodSource("endpoints")
    void test_normalFlow(JsonNode c) throws Exception {
        String path = c.path("path").asString();
        platform.rawResponse(path, JSON.writeValueAsString(c.path("response")));

        var response = send(c, c.path("query").asString(), c.path("request"), true);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(c.path("response"), JSON.readTree(response.body()));
        assertEquals(List.of(AUTHORIZE_PATH, path), platform.requests().stream().map(RecordedRequest::path).toList());
        var call = platform.requests().getLast();
        assertAll(() -> assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (c.path("request").isNull()) {
            assertEquals(query(c.path("query").asString()), query(call.query()));
        } else {
            assertEquals(c.path("request"), JSON.readTree(call.body()));
        }
    }

    /** 当前会话授权失败时不访问通知 Owner。 */
    @ParameterizedTest(name = "{index} 授权拒绝")
    @MethodSource("endpoints")
    void test_authorizationDenied(JsonNode c) throws Exception {
        platform.allow(false);

        var response = send(c, c.path("query").asString(), c.path("request"), true);

        assertEquals(403, response.statusCode());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** Owner 的 400/404/409/503 带本次请求标识透传，不外露上游说明。 */
    @ParameterizedTest
    @CsvSource({"400,BUSINESS_CHECK_FAILED", "404,NOT_FOUND", "409,CONCURRENT_REQUEST", "503,SERVICE_UNAVAILABLE"})
    void test_ownerProblem(int status, String code) throws Exception {
        var c = endpoint("N04");
        platform.problem(c.path("path").asString(), status, code);

        var response = send(c, "", c.path("request"), true);

        assertEquals(status, response.statusCode());
        assertEquals(REQUEST_ID, JSON.readTree(response.body()).path("requestId").asString());
        assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY));
    }

    /** Owner 422 只保留本次请求体内的字段路径，说明换为安全文案。 */
    @Test
    void test_validationPathsProjected() throws Exception {
        var c = endpoint("N05");
        String upstream = """
            {"type":"about:blank","title":"Unprocessable Content","detail":"%s","code":"VALIDATION_ERROR",
             "requestId":"%s","timestamp":"2026-10-15T06:00:00.000000Z","errors":[
              {"field":"bodyTemplate","message":"认证邮件模板正文必须包含 ${link}"},{"field":"unknownRoot","message":"x"}]}
            """.formatted(UPSTREAM_DETAIL_CANARY, UPSTREAM_REQUEST_ID);
        platform.rawResponse(c.path("path").asString(), 422, "application/problem+json", upstream,
            Map.of("X-Request-Id", UPSTREAM_REQUEST_ID));

        var response = send(c, "", c.path("request"), true);

        assertEquals(422, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(CommonErrorCode.VALIDATION_ERROR.getCode(), body.path("code").asString()),
            () -> assertEquals(1, body.path("errors").size()),
            () -> assertEquals("bodyTemplate", body.path("errors").get(0).path("field").asString()),
            () -> assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY)));
    }

    /** 上游多出的字段不投影到浏览器；编码不一致或分页不自洽视为无效响应 502。 */
    @Test
    void test_invalidUpstreamResponse() throws Exception {
        var detail = endpoint("N02");
        var extra = (ObjectNode) detail.path("response").deepCopy();
        extra.put("credentialToken", "SECRET-CANARY");
        platform.rawResponse(detail.path("path").asString(), JSON.writeValueAsString(extra));
        var response = send(detail, "", detail.path("request"), true);
        assertEquals(200, response.statusCode());
        assertFalse(response.body().contains("SECRET-CANARY"));
        assertEquals(detail.path("response"), JSON.readTree(response.body()));

        platform.reset();
        var status = endpoint("N10");
        platform.rawResponse(status.path("path").asString(), "{\"code\":\"OTHER_ACCOUNT\",\"version\":2}");
        assertEquals(502, send(status, "", status.path("request"), true).statusCode());

        platform.reset();
        var list = endpoint("N09");
        var page = (ObjectNode) list.path("response").deepCopy();
        page.put("total", 0);
        platform.rawResponse(list.path("path").asString(), JSON.writeValueAsString(page));
        assertEquals(502, send(list, list.path("query").asString(), list.path("request"), true).statusCode());
    }

    /** 未知 Body 字段、未知 Query、缺少 Origin 的写请求在访问 Owner 前被拒绝。 */
    @Test
    void test_invalidParameter() throws Exception {
        var c = endpoint("N04");
        var body = (ObjectNode) c.path("request").deepCopy();
        body.put("medium", "SMS");
        assertEquals(400, send(c, "", body, true).statusCode());
        var missing = (ObjectNode) c.path("request").deepCopy();
        missing.remove("expectedVersion");
        assertEquals(400, send(c, "", missing, true).statusCode());
        assertEquals(403, send(c, "", c.path("request"), false).statusCode());
        var list = endpoint("N01");
        assertEquals(400, send(list, "?unknown=1", list.path("request"), true).statusCode());

        assertTrue(platform.requests().stream().allMatch(r -> r.path().equals(AUTHORIZE_PATH)));
    }

    /** 经真实 BFF HTTP 入口发送浏览器请求，带 Gateway 可信身份。 */
    private HttpResponse<String> send(JsonNode c, String query, JsonNode body, boolean origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + c.path("path").asString() + query))
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", c.path("routeCode").asString()).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", "notification-command-001");
        if (origin) {
            builder.header("Origin", "https://operations-console.local.test");
        }
        if (!body.isNull()) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(c.path("method").asString(), body.isNull() ? HttpRequest.BodyPublishers.noBody()
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
