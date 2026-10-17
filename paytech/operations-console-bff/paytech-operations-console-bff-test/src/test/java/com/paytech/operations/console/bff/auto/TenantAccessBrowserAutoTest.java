package com.paytech.operations.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.*;
import java.net.URI;
import java.net.http.*;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 浏览器到真实 BFF/HttpExchange 的全端点合同；Platform Simulator 保留真实会话与鉴权状态。 */
class TenantAccessBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String KEY = "tenant-access-boundary-command-001";
    @Autowired private PlatformOperationsSimulator platform;
    @LocalServerPort private int port;

    /** 清理模拟 Owner 的请求和响应状态，保证每个用例独立。 */
    @BeforeEach void reset() { platform.reset(); }
    /** 核对 Owner 实际收到的身份、请求和查询参数。 */
    @AfterEach void verify() { assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()); }

    /** 从独立边界清单提供八个端点的协议场景。 */
    static Stream<JsonNode> endpoints() throws Exception {
        try (var input = TenantAccessBrowserAutoTest.class.getResourceAsStream("/tenant-access-boundary.json")) {
            var cases = JSON.readTree(input); List<JsonNode> rows = new ArrayList<>(); cases.forEach(rows::add); return rows.stream();
        }
    }
    /** 读取指定端点的独立请求和响应样例。 */
    static JsonNode endpoint(String id) throws Exception { return endpoints().filter(c -> id.equals(c.path("id").asString())).findFirst().orElseThrow(); }

    /** 逐端点验证身份、筛选及命令正文的准确转发；幂等键由 Gateway 处理，不再转发。 */
    @ParameterizedTest(name = "{index} Tenant Access 合同")
    @MethodSource("endpoints")
    void test_eightEndpointsPreserveIdentityQueryAndCommands(JsonNode c) throws Exception {
        String path = c.path("path").asString();
        platform.rawResponse(path, JSON.writeValueAsString(c.has("upstreamResponse") ? c.path("upstreamResponse") : c.path("response")));
        var response = send(c, c.path("query").asString(), c.path("request"), true);
        assertEquals(200, response.statusCode(), response.body()); assertEquals(c.path("response"), JSON.readTree(response.body()));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(List.of(AUTHORIZE_PATH, path), platform.requests().stream().map(RecordedRequest::path).toList());
        var call = platform.requests().getLast();
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")); assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertNull(call.header("Authorization")); assertNull(call.header("Cookie"));
        if (!c.path("request").isNull()) { assertNull(call.header("Idempotency-Key")); assertEquals(c.path("request"), JSON.readTree(call.body())); }
        else { assertEquals(query(c.path("query").asString()), query(call.query())); }
    }

    /** 验证当前会话授权失败后不会继续访问业务 Owner。 */
    @ParameterizedTest @MethodSource("endpoints")
    void test_currentAuthorizationDenialStopsAllOwnerCalls(JsonNode c) throws Exception {
        platform.allow(false); var response = send(c, c.path("query").asString(), c.path("request"), true);
        assertEquals(403, response.statusCode()); assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** 404/409/503 与本请求标识沿用现有 Problem 合同。 */
    @ParameterizedTest @CsvSource({"404,NOT_FOUND", "409,CONCURRENT_REQUEST", "503,SERVICE_UNAVAILABLE"})
    void test_ownerProblemsRemainVisible(int status, String code) throws Exception {
        var c = endpoint("A02"); platform.problem(c.path("path").asString(), status, code);
        var response = send(c, "", c.path("request"), true);
        assertEquals(status, response.statusCode());
        assertEquals("tenant-access-boundary-request", JSON.readTree(response.body()).path("requestId").asString());
    }

    /** 角色及成员响应不能串入另一个租户，权限汇总不接受无来源的有效值。 */
    @ParameterizedTest @CsvSource({"A02,tenantId", "A05,tenantId", "A06,roleTenantId", "A08,roleTenantId"})
    void test_foreignTenantResponseRejected(String id, String field) throws Exception {
        var c = endpoint(id); var response = c.path("response").deepCopy();
        var target = response.has("items") ? response.path("items").get(0) : response;
        ((tools.jackson.databind.node.ObjectNode)target).put(field, "999");
        platform.rawResponse(c.path("path").asString(), JSON.writeValueAsString(response));
        assertEquals(502, send(c, c.path("query").asString(), c.path("request"), true).statusCode());
    }

    /** 未允许字段及重复参数在进入 Owner 前失败，写入仍要求合法 Origin。 */
    @Test void test_requestBoundaries() throws Exception {
        var get = endpoint("A01");
        assertEquals(400, send(get, "?unknown=x", get.path("request"), true).statusCode());
        assertEquals(400, send(get, "?page=1&page=2", get.path("request"), true).statusCode());
        var post = endpoint("A03"); var body = (tools.jackson.databind.node.ObjectNode) post.path("request").deepCopy();
        body.put("ownerStatus", "ACTIVE");
        assertEquals(400, send(post, "", body, true).statusCode());
        assertEquals(403, send(post, "", post.path("request"), false).statusCode());
        assertTrue(platform.requests().stream().allMatch(r -> r.path().equals(AUTHORIZE_PATH)));
    }

    /** 上游扩展秘密列不投影到浏览器。 */
    @Test void test_extraFieldsNotExposed() throws Exception {
        var c = endpoint("A05"); var body = (tools.jackson.databind.node.ObjectNode)c.path("response").deepCopy();
        body.put("emailVaultToken", "SECRET_CANARY"); body.put("rawEmail", "SECRET_CANARY");
        platform.rawResponse(c.path("path").asString(), JSON.writeValueAsString(body));
        var response = send(c, "", c.path("request"), true);
        assertEquals(200, response.statusCode()); assertFalse(response.body().contains("SECRET_CANARY"));
    }

    /** 通过真实 BFF HTTP 入口发送浏览器请求，Owner 使用批准的 Simulator。 */
    private HttpResponse<String> send(JsonNode c, String query, JsonNode body, boolean origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + c.path("path").asString() + query))
            .header("X-Request-Id", "tenant-access-boundary-request").header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", c.path("routeCode").asString()).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", KEY);
        if (origin) { builder.header("Origin", "https://operations-console.local.test"); }
        if (!body.isNull()) { builder.header("Content-Type", "application/json"); }
        return HTTP.send(builder.method(c.path("method").asString(), body.isNull() ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    /** 按场景构造合法筛选，不把空值作为 Query 发送。 */
    private Map<String, String> query(String raw) {
        if (raw == null || raw.isEmpty()) { return Map.of(); } var result = new HashMap<String, String>();
        for (String pair : raw.replaceFirst("^\\?", "").split("&")) { String[] parts = pair.split("=", 2);
            result.put(java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8), java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8)); }
        return result;
    }
}
