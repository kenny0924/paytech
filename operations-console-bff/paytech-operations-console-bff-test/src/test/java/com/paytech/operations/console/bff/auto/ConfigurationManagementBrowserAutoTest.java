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
class ConfigurationManagementBrowserAutoTest extends OperationsBffHttpTestBase {
    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final HttpClient HTTP = HttpClient.newHttpClient();
    private static final String KEY = "configuration-boundary-command-001";
    @Autowired private PlatformOperationsSimulator platform;
    @LocalServerPort private int port;

    @BeforeEach void reset() { platform.reset(); }
    @AfterEach void verify() { assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()); }

    static Stream<JsonNode> endpoints() throws Exception {
        try (var input = ConfigurationManagementBrowserAutoTest.class.getResourceAsStream("/configuration-management-boundary.json")) {
            var cases = JSON.readTree(input); List<JsonNode> rows = new ArrayList<>(); cases.forEach(rows::add); return rows.stream();
        }
    }
    static JsonNode endpoint(String id) throws Exception { return endpoints().filter(c -> id.equals(c.path("id").asString())).findFirst().orElseThrow(); }

    @ParameterizedTest(name = "{index} 配置接口")
    @MethodSource("endpoints")
    void test_allEighteenEndpointsForwardExactlyOnce(JsonNode c) throws Exception {
        String path = c.path("path").asString();
        platform.rawResponse(path, JSON.writeValueAsString(c.path("response")));
        var response = send(c, c.path("query").asString(), c.path("request"), true);
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(c.path("response"), JSON.readTree(response.body()));
        assertEquals(List.of(AUTHORIZE_PATH, path), platform.requests().stream().map(RecordedRequest::path).toList());
        var call = platform.requests().getLast();
        assertEquals(c.path("method").asString(), call.method());
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject")); assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertEquals("configuration-boundary-request", call.header("X-Request-Id"));
        assertNull(call.header("Authorization")); assertNull(call.header("Cookie"));
        if (!c.path("request").isNull()) {
            assertNull(call.header("Idempotency-Key"));
            assertEquals(c.path("request"), JSON.readTree(call.body())); }
        else if (!c.path("query").asString().isEmpty()) assertEquals(query(c.path("query").asString()), query(call.query()));
    }

    @ParameterizedTest
    @MethodSource("endpoints")
    void test_allNewEndpointsStopWhenCurrentAuthorizationDenied(JsonNode c) throws Exception {
        platform.allow(false);
        var response = send(c, c.path("query").asString(), c.path("request"), true);
        assertEquals(403, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
    }

    @Test void test_selectorStillPropagatesOwnerUsageAuthorization() throws Exception {
        var c = endpoint("A18"); platform.problem(c.path("path").asString(), 403, "OPERATIONS_PERMISSION_DENIED");
        var response = send(c, c.path("query").asString(), c.path("request"), true);
        assertEquals(403, response.statusCode(), response.body()); assertEquals(2, platform.requests().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"?unknown=1", "?usage=CONSOLE_RESOURCE&usage=GATEWAY_ROUTE", "?usage="})
    void test_invalidQueryStopsBeforeOwner(String query) throws Exception {
        var c = endpoint("A18"); var response = send(c, query, c.path("request"), true);
        assertEquals(400, response.statusCode(), response.body());
        assertTrue(platform.requests().stream().noneMatch(r -> r.path().equals(c.path("path").asString())));
    }

    @Test void test_unknownBodyAndMissingPrimitiveStopBeforeOwner() throws Exception {
        var c = endpoint("A08"); var body = c.path("request").deepCopy(); ((tools.jackson.databind.node.ObjectNode) body).put("frontendRoute", "/injected");
        var response = send(c, "", body, true); assertEquals(400, response.statusCode(), response.body());
        ((tools.jackson.databind.node.ObjectNode) body).remove("frontendRoute"); ((tools.jackson.databind.node.ObjectNode) body).remove("sortOrder");
        response = send(c, "", body, true); assertEquals(400, response.statusCode(), response.body());
        assertTrue(platform.requests().stream().allMatch(r -> r.path().equals(AUTHORIZE_PATH)));
    }

    @Test void test_newMutationStillRequiresOrigin() throws Exception {
        var c = endpoint("A12"); var response = send(c, "", c.path("request"), false);
        assertEquals(403, response.statusCode(), response.body());
        assertTrue(platform.requests().stream().allMatch(r -> r.path().equals(AUTHORIZE_PATH)));
    }

    @ParameterizedTest
    @CsvSource({"404,NOT_FOUND", "409,CONCURRENT_REQUEST", "503,SERVICE_UNAVAILABLE"})
    void test_ownerProblemKeepsStatusAndUsesBrowserRequestId(int status, String code) throws Exception {
        var c = endpoint("A13"); platform.problem(c.path("path").asString(), status, code);
        var response = send(c, "", c.path("request"), true);
        assertEquals(status, response.statusCode(), response.body()); assertEquals("configuration-boundary-request", JSON.readTree(response.body()).path("requestId").asString());
        assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY)); assertEquals(2, platform.requests().size());
    }

    @Test void test_ownerFieldPathsAreProjectedWithoutRemoteTextOrUnknownCoordinates() throws Exception {
        var c = endpoint("A03");
        var problem = new LinkedHashMap<String, Object>();
        problem.put("type", "about:blank"); problem.put("title", "Unprocessable Content");
        problem.put("detail", "remote-private-detail"); problem.put("code", "VALIDATION_ERROR");
        problem.put("requestId", "upstream-fields-request"); problem.put("timestamp", "2026-09-13T00:00:00.123456Z");
        problem.put("errors", List.of(Map.of("field", "headers[0].headerName", "message", "remote-private-message"),
            Map.of("field", "headers[99].headerName", "message", "out-of-bounds"), Map.of("field", "privateField", "message", "unknown")));
        platform.rawResponse(c.path("path").asString(), 422, "application/problem+json", JSON.writeValueAsString(problem),
            Map.of("X-Request-Id", "upstream-fields-request"));
        var response = send(c, "", c.path("request"), true);
        assertEquals(422, response.statusCode(), response.body());
        var errors = JSON.readTree(response.body()).path("errors");
        assertEquals(1, errors.size()); assertEquals("headers[0].headerName", errors.get(0).path("field").asString());
        assertFalse(response.body().contains("remote-private")); assertFalse(response.body().contains("privateField"));
    }

    private HttpResponse<String> send(JsonNode c, String query, JsonNode body, boolean origin) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + c.path("path").asString() + query))
            .header("X-Request-Id", "configuration-boundary-request").header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", c.path("routeCode").asString()).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", KEY);
        if (origin) builder.header("Origin", "https://operations-console.local.test");
        if (!body.isNull()) builder.header("Content-Type", "application/json");
        return HTTP.send(builder.method(c.path("method").asString(), body.isNull() ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(), HttpResponse.BodyHandlers.ofString());
    }
    private Map<String, String> query(String raw) {
        if (raw == null || raw.isEmpty()) return Map.of(); var result = new HashMap<String, String>();
        for (String pair : raw.replaceFirst("^\\?", "").split("&")) { String[] parts = pair.split("=", 2);
            result.put(java.net.URLDecoder.decode(parts[0], java.nio.charset.StandardCharsets.UTF_8), java.net.URLDecoder.decode(parts[1], java.nio.charset.StandardCharsets.UTF_8)); }
        return result;
    }
}
