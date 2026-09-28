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
class ClientApiManagementBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String KEY = "client-api-boundary-command-001";
    @Autowired private PlatformOperationsSimulator platform;
    @LocalServerPort private int port;

    /** 清理模拟 Owner 的请求和响应状态，保证每个用例独立。 */
    @BeforeEach void reset() { platform.reset(); }
    /** 核对 Owner 实际收到的身份、请求和查询参数。 */
    @AfterEach void verify() { assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()); }

    /** 从独立边界清单提供九个端点的协议场景。 */
    static Stream<JsonNode> endpoints() throws Exception {
        try (var input = ClientApiManagementBrowserAutoTest.class.getResourceAsStream("/client-api-management-boundary.json")) {
            var cases = JSON.readTree(input); List<JsonNode> rows = new ArrayList<>(); cases.forEach(rows::add); return rows.stream();
        }
    }
    /** 读取指定端点的独立请求和响应样例。 */
    static JsonNode endpoint(String id) throws Exception { return endpoints().filter(c -> id.equals(c.path("id").asString())).findFirst().orElseThrow(); }

    /** 逐端点验证身份、筛选、幂等键及命令正文的准确转发。 */
    @ParameterizedTest(name = "{index} Client API 合同")
    @MethodSource("endpoints")
    void test_nineEndpointsPreserveIdentityQueryAndCommands(JsonNode c) throws Exception {
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
        else if (c.path("id").asString().equals("A06")) { assertTrue(call.query() == null || call.query().isEmpty()); }
        else { assertEquals(query(c.path("query").asString()), query(call.query())); }
    }

    /** 验证当前会话授权失败后不会继续访问业务 Owner。 */
    @ParameterizedTest @MethodSource("endpoints")
    void test_currentAuthorizationDenialStopsAllOwnerCalls(JsonNode c) throws Exception {
        platform.allow(false); var response = send(c, c.path("query").asString(), c.path("request"), true);
        assertEquals(403, response.statusCode()); assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    /** 验证凭证引用仅查询当前选择的 Owner 分页，不预取其他引用。 */
    @Test void test_selectedCredentialReferencesOnlyCallsThatOwnerQuery() throws Exception {
        var c = endpoint("A06"); var a1 = endpoint("A01");
        platform.groupedResponses(Map.of(c.path("path").asString(), JSON.writeValueAsString(c.path("upstreamResponse")),
            a1.path("path").asString(), JSON.writeValueAsString(a1.path("response"))));
        var response = send(c, "?referenceKind=CREDENTIAL&page=1&size=10", c.path("request"), true);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("CREDENTIAL", JSON.readTree(response.body()).path("references").path("kind").asString());
        assertEquals(List.of(AUTHORIZE_PATH, c.path("path").asString(), a1.path("path").asString()), platform.requests().stream().map(RecordedRequest::path).toList());
        assertEquals(Map.of("scopeCode", "checkout.read", "page", "1", "size", "10"), query(platform.requests().getLast().query()));
    }

    /** 验证引用权限不足明确返回拒绝，不伪装成空结果。 */
    @Test void test_referencePermissionFailureIsNotAnEmptyPage() throws Exception {
        var c = endpoint("A06"); platform.groupedResponses(Map.of(c.path("path").asString(), JSON.writeValueAsString(c.path("upstreamResponse"))));
        platform.problem("/api-credential-admin/v1/credentials", 403, "OPERATIONS_PERMISSION_DENIED");
        var response = send(c, "?referenceKind=CREDENTIAL", c.path("request"), true);
        assertEquals(403, response.statusCode()); assertFalse(response.body().contains("references"));
    }

    /** 验证非法引用类型和分页组合在访问 Owner 前被拒绝。 */
    @ParameterizedTest @ValueSource(strings = {"?referenceKind=UNKNOWN", "?referenceKind=NONE&page=1", "?referenceKind=NONE&size=10", "?referenceKind=ROUTE&size=200", "?referenceKind=NONE&referenceKind=ROUTE", "?unrecognized=1"})
    void test_referenceParameterRulesStopBeforeOwner(String query) throws Exception {
        var c = endpoint("A06"); assertEquals(400, send(c, query, c.path("request"), true).statusCode());
        assertTrue(platform.requests().stream().allMatch(r -> r.path().equals(AUTHORIZE_PATH)));
    }

    /** 验证 Owner 扩展的秘密字段不会出现在浏览器响应中。 */
    @Test void test_credentialResponseNeverProjectsUpstreamSecretFields() throws Exception {
        var c = endpoint("A02"); var upstream = c.path("response").deepCopy();
        ((tools.jackson.databind.node.ObjectNode) upstream).put("keyVerifierPhc", "SECRET-CANARY-PHC").put("credential", "SECRET-CANARY-FULL");
        platform.rawResponse(c.path("path").asString(), JSON.writeValueAsString(upstream));
        var response = send(c, "", c.path("request"), true);
        assertEquals(200, response.statusCode()); assertFalse(response.body().contains("SECRET-CANARY")); assertEquals(c.path("response"), JSON.readTree(response.body()));
    }

    /** 验证说明清空的显式合同和浏览器写请求 Origin 要求。 */
    @Test void test_updateMustExplicitlyClearDescriptionAndMutationsRequireOrigin() throws Exception {
        var c = endpoint("A08"); var request = (tools.jackson.databind.node.ObjectNode)c.path("request").deepCopy(); request.remove("description");
        assertEquals(400, send(c, "", request, true).statusCode());
        assertEquals(403, send(c, "", c.path("request"), false).statusCode());
        request.putNull("description"); request.put("status", "ACTIVE"); assertEquals(400, send(c, "", request, true).statusCode());
        assertTrue(platform.requests().stream().allMatch(r -> r.path().equals(AUTHORIZE_PATH)));
    }

    /** 验证上游 Problem 状态和请求身份经过 BFF 后保持可诊断。 */
    @ParameterizedTest @CsvSource({"404,NOT_FOUND", "409,CONCURRENT_REQUEST", "503,SERVICE_UNAVAILABLE"})
    void test_ownerProblemStatusAndRequestIdentitySurvive(int status, String code) throws Exception {
        var c = endpoint("A04"); platform.problem(c.path("path").asString(), status, code);
        var response = send(c, "", c.path("request"), true); assertEquals(status, response.statusCode());
        assertEquals("client-api-boundary-request", JSON.readTree(response.body()).path("requestId").asString());
        assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY));
    }

    /** 验证不一致身份及分页元数据作为上游错误被拒绝。 */
    @Test void test_invalidUpstreamIdentityAndPageAreRejected() throws Exception {
        var c = endpoint("A02"); var wrong = (tools.jackson.databind.node.ObjectNode)c.path("response").deepCopy(); wrong.put("credentialId", "999");
        platform.rawResponse(c.path("path").asString(), JSON.writeValueAsString(wrong));
        assertEquals(502, send(c, "", c.path("request"), true).statusCode());
        platform.reset(); c = endpoint("A01"); var page = (tools.jackson.databind.node.ObjectNode)c.path("response").deepCopy(); page.put("total", 0);
        platform.rawResponse(c.path("path").asString(), JSON.writeValueAsString(page));
        assertEquals(502, send(c, c.path("query").asString(), c.path("request"), true).statusCode());
    }

    /** 验证选定 Route 引用及审计对象筛选准确下传。 */
    @Test void test_selectedRouteReferencesAndAuditObjectFilters() throws Exception {
        var c = endpoint("A06"); String routes = "/gateway-admin/v1/routes";
        String empty = "{\"items\":[],\"page\":1,\"size\":10,\"total\":0}";
        platform.groupedResponses(Map.of(c.path("path").asString(), JSON.writeValueAsString(c.path("upstreamResponse")), routes, empty));
        var response = send(c, "?referenceKind=ROUTE&page=1&size=10", c.path("request"), true);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals("ROUTE", JSON.readTree(response.body()).path("references").path("kind").asString());
        assertEquals(List.of(AUTHORIZE_PATH, c.path("path").asString(), routes), platform.requests().stream().map(RecordedRequest::path).toList());
        assertEquals(Map.of("requiredScopeCode", "checkout.read", "page", "1", "size", "10"), query(platform.requests().getLast().query()));
        platform.reset(); platform.rawResponse("/operations/v1/audit-events", empty);
        var audit = (tools.jackson.databind.node.ObjectNode)c.deepCopy(); audit.put("path", "/audit/v1/events");
        response = send(audit, "?resourceType=API_SCOPE&resourceId=checkout.read", audit.path("request"), true);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(Map.of("resourceType", "API_SCOPE", "resourceId", "checkout.read", "page", "1", "size", "10"), query(platform.requests().getLast().query()));
    }

    /** 通过真实 BFF HTTP 入口发送浏览器请求，Owner 使用批准的 Simulator。 */
    private HttpResponse<String> send(JsonNode c, String query, JsonNode body, boolean origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + c.path("path").asString() + query))
            .header("X-Request-Id", "client-api-boundary-request").header("X-Gateway-Realm", "OPERATIONS")
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
