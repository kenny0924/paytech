package com.paytech.operations.console.bff.auto;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.paytech.operations.console.bff.OperationsConsoleBffApplication;
import com.paytech.operations.console.bff.test.configuration.OperationsBffTestConfiguration;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import tools.jackson.databind.json.JsonMapper;

import static com.paytech.operations.console.bff.test.OperationsProblemChecker.assertProblem;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.*;
import static org.junit.jupiter.api.Assertions.*;

/** test Profile 通过真实 HTTP 与发现实例访问 Simulator，保持生产 @HttpExchange 和授权链。 */
@ActiveProfiles("test")
@SpringBootTest(classes = {OperationsConsoleBffApplication.class, OperationsBffTestConfiguration.class},
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "OPERATIONS_TEST_HTTP_ENABLED=true", "OPERATIONS_CONSOLE_ORIGIN=http://localhost:28080",
        "spring.cloud.nacos.discovery.enabled=false", "spring.cloud.nacos.discovery.register-enabled=false",
        "spring.cloud.service-registry.auto-registration.enabled=false", "spring.cloud.loadbalancer.cache.enabled=false"
    })
class OperationsHttpTestModeAutoTest {
    private static final String ORIGIN = "http://localhost:28080";
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    @Autowired private PlatformOperationsSimulator platform;
    @LocalServerPort private int port;

    @DynamicPropertySource
    static void discoveredPlatform(DynamicPropertyRegistry registry) {
        registry.add("spring.cloud.discovery.client.simple.instances.platform-support-service[0].uri",
            () -> URI.create(OperationsBffTestConfiguration.PLATFORM.baseUrl()).resolve("/").toString());
    }

    @BeforeEach void reset() { platform.reset(); }
    @AfterEach void unexpectedRequests() { assertTrue(platform.unexpectedRequests().isEmpty()); }

    @Test
    void test_loginRefreshAndLogoutUseJsonTokensWithoutCookieOrCsrfHeader() throws Exception {
        HttpResponse<String> login = send("/auth/v1/login", json.writeValueAsString(Map.of("loginIdentifier", EMAIL, "password", PASSWORD)), headers());
        assertEquals(200, login.statusCode(), login.body());
        assertTokenGrant(login);
        String original = platform.refreshToken();

        HttpResponse<String> refresh = send("/auth/v1/refresh", refreshBody(original), headers());
        assertEquals(200, refresh.statusCode(), refresh.body());
        assertNotEquals(original, platform.refreshToken());
        assertTokenGrant(refresh);
        assertEquals(original, json.readTree(platform.requests().getLast().body()).path("refreshToken").asString());

        HttpResponse<String> logout = send("/auth/v1/logout", refreshBody(platform.refreshToken()), identity("operations-auth-logout"));
        assertEquals(204, logout.statusCode(), logout.body());
        assertFalse(platform.sessionActive());
        assertEquals(List.of(AUTH_PATH + "/login", AUTH_PATH + "/refresh", AUTHORIZE_PATH, AUTH_PATH + "/logout"),
            platform.requests().stream().map(PlatformOperationsSimulator.RecordedRequest::path).toList());
        assertTrue(platform.requests().stream().allMatch(call -> call.header("Cookie") == null && call.header("X-CSRF-Token") == null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"legacy", "problem"})
    void test_discoveredPlatformAcceptsBothRollingMigrationErrorFormats(String format) throws Exception {
        if (format.equals("legacy")) {
            platform.legacyError(AUTH_PATH + "/login", 400, "OPERATIONS_AUTHENTICATION_FAILED");
        } else {
            platform.problem(AUTH_PATH + "/login", 400, "OPERATIONS_AUTHENTICATION_FAILED");
        }

        HttpResponse<String> response = send("/auth/v1/login",
            json.writeValueAsString(Map.of("loginIdentifier", EMAIL, "password", PASSWORD)), headers());

        var body = assertProblem(json, response, 401, "OPERATIONS_AUTHENTICATION_FAILED");
        assertEquals("账号或凭据无效", body.path("detail").asString());
        assertFalse(response.body().contains(UPSTREAM_REQUEST_ID));
        assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY));
        assertFalse(response.body().contains(UPSTREAM_EXTENSION_CANARY));
        assertEquals(1, platform.requests().size(), "滚动迁移错误响应不得触发重试");
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "bootstrap/administrator"})
    void test_passwordAndBootstrapRevokeSessionWithoutCookies(String operation) throws Exception {
        Map<String, String> values = operation.equals("password")
            ? Map.of("currentPassword", PASSWORD, "newPassword", "new-password-for-http-test")
            : Map.of("email", EMAIL, "initialPassword", PASSWORD);
        String route = operation.equals("password") ? "operations-auth-password" : "operations-bootstrap-administrator";

        HttpResponse<String> response = send("/auth/v1/" + operation, json.writeValueAsString(values), identity(route));

        assertEquals(operation.equals("password") ? 204 : 200, response.statusCode(), response.body());
        assertFalse(platform.sessionActive());
        assertEquals(List.of(AUTHORIZE_PATH, AUTH_PATH + "/" + operation),
            platform.requests().stream().map(PlatformOperationsSimulator.RecordedRequest::path).toList());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-origin", "wrong-port", "wrong-host", "https-origin", "not-json"})
    void test_httpModePreservesExactOriginAndJsonRequirements(String fault) throws Exception {
        Map<String, String> values = headers();
        switch (fault) {
            case "missing-origin" -> values.remove("Origin");
            case "wrong-port" -> values.put("Origin", "http://localhost:28081");
            case "wrong-host" -> values.put("Origin", "http://127.0.0.1:28080");
            case "https-origin" -> values.put("Origin", "https://localhost:28080");
            case "not-json" -> values.put("Content-Type", "text/plain");
            default -> throw new AssertionError(fault);
        }

        HttpResponse<String> response = send("/auth/v1/refresh", refreshBody(platform.refreshToken()), values);

        assertProblem(json, response, fault.equals("not-json") ? 415 : 403,
            fault.equals("not-json") ? "OPERATIONS_UNSUPPORTED_MEDIA_TYPE" : "OPERATIONS_ORIGIN_REJECTED");
        assertTrue(platform.requests().isEmpty());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-body-token", "query-token", "upstream-rejected"})
    void test_rejectedRefreshNeverReadsOrWritesCookies(String fault) throws Exception {
        Map<String, String> values = headers();
        values.put("Cookie", "operations-refresh-test=" + platform.refreshToken());
        String path = "/auth/v1/refresh";
        if (fault.equals("query-token")) path += "?refreshToken=" + platform.refreshToken();
        boolean upstream = fault.equals("upstream-rejected");
        if (upstream) platform.fail(AUTH_PATH + "/refresh", 400, "OPERATIONS_SESSION_INVALID");

        HttpResponse<String> response = send(path, upstream ? refreshBody(platform.refreshToken()) : "{}", values);

        assertProblem(json, response, upstream ? 401 : 422,
            upstream ? "OPERATIONS_SESSION_INVALID" : "VALIDATION_ERROR");
        assertEquals(upstream ? 1 : 0, platform.requests().size());
        if (upstream) assertNull(platform.requests().getFirst().header("Cookie"));
        assertFalse(response.body().contains(platform.refreshToken()));
    }

    @Test
    void test_httpModeStillRequiresTrustedIdentityAndCurrentAuthorization() throws Exception {
        Map<String, String> values = identity("operations-auth-password");
        values.remove("X-Gateway-Realm");
        String body = json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "new-password-for-http-test"));
        HttpResponse<String> missing = send("/auth/v1/password", body, values);
        assertProblem(json, missing, 401, "OPERATIONS_SESSION_INVALID");
        assertTrue(platform.requests().isEmpty());
        platform.allow(false);
        HttpResponse<String> denied = send("/auth/v1/password", body, identity("operations-auth-password"));
        assertProblem(json, denied, 403, "OPERATIONS_PERMISSION_DENIED");
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(PlatformOperationsSimulator.RecordedRequest::path).toList());
        assertTrue(platform.sessionActive());
    }

    private Map<String, String> headers() {
        Map<String, String> values = new LinkedHashMap<>();
        values.put("Origin", ORIGIN);
        values.put("Content-Type", "application/json");
        return values;
    }
    private Map<String, String> identity(String route) {
        Map<String, String> values = headers();
        values.put("X-Gateway-Realm", "OPERATIONS");
        values.put("X-Gateway-Channel", "OPERATIONS");
        values.put("X-Gateway-Actor-Type", "PLATFORM_USER");
        values.put("X-Gateway-Actor-Subject", ACCOUNT_ID);
        values.put("X-Gateway-Session-Id", SESSION_ID);
        values.put("X-Gateway-Route-Code", route);
        values.put("X-Gateway-Context-Type", "TENANT");
        values.put("X-Gateway-Tenant-Type", "INTERNAL");
        values.put("X-Gateway-Tenant-Id", "tenant_10001");
        values.put("X-Gateway-Tenant-Member-Id", "member_10001");
        return values;
    }
    private HttpResponse<String> send(String path, String body, Map<String, String> values) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(8));
        values.forEach(request::header);
        HttpResponse<String> response = CLIENT.send(request.POST(HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "HTTP test 模式同样只使用 JSON Token");
        return response;
    }
    private String refreshBody(String token) { return json.writeValueAsString(Map.of("refreshToken", token)); }

    private void assertTokenGrant(HttpResponse<String> response) {
        var body = json.readTree(response.body());
        assertEquals(java.util.Set.of("accessToken", "tokenType", "expiresIn", "refreshToken", "refreshExpiresIn",
            "passwordChangeRequired", "bootstrapAdministratorRequired"), body.propertyNames());
        assertEquals(platform.refreshToken(), body.path("refreshToken").asString());
        assertEquals(28800, body.path("refreshExpiresIn").asLong());
        assertEquals("Bearer", body.path("tokenType").asString());
        assertEquals(900, body.path("expiresIn").asLong());
    }
}
