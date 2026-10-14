package com.paytech.operations.console.bff.auto;

import static com.paytech.operations.console.bff.test.OperationsProblemChecker.assertHeadProblem;
import static com.paytech.operations.console.bff.test.OperationsProblemChecker.assertProblem;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNTS_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_CREATE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_DISABLE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ASSIGN_ROLES_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_DELETE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ENABLE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ROLE_CREATE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ROLE_UPDATE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ROLE_DELETE_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ROLE_COUNTS_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUDIT_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUDIT_READ_PERMISSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTH_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.EMAIL;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.PASSWORD;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_DETAIL_CANARY;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_EXTENSION_CANARY;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.UPSTREAM_REQUEST_ID;
import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Stream;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;
import com.paytech.foundation.web.request.RequestIdFilter;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.test.base.OperationsBffHttpTestBase;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.platform.support.api.authentication.request.OperationsLoginRequestBody;
import com.paytech.platform.support.api.authentication.OperationsAuthenticationApi;
import com.paytech.platform.support.api.operations.response.RoleResponseBody;
import com.paytech.platform.support.api.operations.response.AccountResponseBody;
import com.paytech.platform.support.api.operations.response.AuditEvent;
import com.paytech.platform.support.api.operations.response.AuditEventResponseBody;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.web.client.RestClientResponseException;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.ThrowableProxyUtil;
import ch.qos.logback.core.read.ListAppender;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 真实浏览器协议请求经 BFF 与 @HttpExchange 到有状态 Platform Simulator。 */
class OperationsBrowserBoundaryAutoTest extends OperationsBffHttpTestBase {
    private static final String ORIGIN = "https://operations-console.local.test";
    private static final String REQUEST_ID = "operations-browser-boundary-request";
    private static final String SECRET_QUERY = "sensitive-query-must-never-be-logged";
    private static final HttpClient CLIENT = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2))
        .followRedirects(HttpClient.Redirect.NEVER).build();

    private final JsonMapper json = JsonMapper.builder().findAndAddModules().build();
    private final Logger rootLogger = (Logger) LoggerFactory.getLogger(Logger.ROOT_LOGGER_NAME);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @Autowired
    private PlatformOperationsSimulator platform;
    @Autowired
    private OperationsAuthenticationApi platformAuthenticationApi;
    @LocalServerPort
    private int port;

    @BeforeEach
    void setUp() {
        platform.reset();
        appender.list = new CopyOnWriteArrayList<>();
        appender.start();
        rootLogger.addAppender(appender);
    }

    @AfterEach
    void verifyAndCleanUp() {
        rootLogger.detachAppender(appender);
        appender.stop();
        assertTrue(platform.unexpectedRequests().isEmpty(), () -> "Simulator 收到未准备请求: " + platform.unexpectedRequests());
        assertNoSecretInLogs(PASSWORD, EMAIL, SECRET_QUERY, "sensitive-access-token-");
    }

    @Test
    void test_loginReturnsBothTokensAsJsonWithoutCookies() throws Exception {
        HttpResponse<String> response = send("POST", "/auth/v1/login?unused=" + SECRET_QUERY,
            loginBody(PASSWORD), browserHeaders());

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(Set.of("accessToken", "tokenType", "expiresIn", "refreshToken", "refreshExpiresIn",
            "passwordChangeRequired", "bootstrapAdministratorRequired"), body.propertyNames());
        assertEquals("sensitive-access-token-1", body.path("accessToken").asString());
        assertEquals("Bearer", body.path("tokenType").asString());
        assertEquals(900, body.path("expiresIn").asLong());
        assertEquals(platform.refreshToken(), body.path("refreshToken").asString());
        assertEquals(28800, body.path("refreshExpiresIn").asLong());
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals(REQUEST_ID, response.headers().firstValue(RequestIdFilter.HEADER_NAME).orElseThrow());
        assertEquals(List.of(AUTH_PATH + "/login"), calledPaths());
        var call = platform.requests().getFirst();
        assertEquals(REQUEST_ID, call.header("X-Request-Id"));
        assertNull(call.header("Cookie"));
        assertNull(call.header("Origin"));
        assertNull(call.header("X-CSRF-Token"));
        assertEquals(PASSWORD, json.readTree(call.body()).path("password").asString());
        assertMetadataEvent("/auth/**", 200);
        assertNoSecretInLogs(platform.refreshToken());
    }

    @Test
    void test_refreshConsumesJsonBodyAndRotatesBothTokensWithoutCsrfOrCookies() throws Exception {
        String oldToken = platform.refreshToken();

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(oldToken), browserHeaders());

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(1, platform.generation());
        assertNotEquals(oldToken, platform.refreshToken());
        assertEquals(oldToken, json.readTree(platform.requests().getFirst().body()).path("refreshToken").asString());
        assertEquals(platform.refreshToken(), json.readTree(response.body()).path("refreshToken").asString());
        assertEquals("sensitive-access-token-1", json.readTree(response.body()).path("accessToken").asString());
        assertFalse(response.body().contains(oldToken));
        assertEquals(List.of(AUTH_PATH + "/refresh"), calledPaths());
        assertNull(platform.requests().getFirst().header("Cookie"));
        assertNull(platform.requests().getFirst().header("X-CSRF-Token"));
        assertMetadataEvent("/auth/**", 200);
        assertNoSecretInLogs(oldToken, platform.refreshToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-origin", "wrong-origin"})
    void test_invalidOriginRejectsBeforePlatformCall(String invalid) throws Exception {
        Map<String, String> headers = browserHeaders();
        if (invalid.equals("missing-origin")) headers.remove("Origin");
        else headers.put("Origin", "https://attacker.example.test");

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(platform.refreshToken()), headers);

        assertError(response, 403, "OPERATIONS_ORIGIN_REJECTED");
        assertTrue(platform.requests().isEmpty());
        assertMetadataEvent("/auth/**", 403);
        assertNoSecretInLogs(platform.refreshToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"https-cookie", "http-cookie", "duplicate-cookie"})
    void test_legacyCookiesCannotSupplyMissingJsonRefreshToken(String source) throws Exception {
        Map<String, String> headers = browserHeaders();
        String token = platform.refreshToken();
        String cookie = (source.equals("http-cookie") ? "operations-refresh-test" : "__Secure-operations-refresh") + "=" + token;
        headers.put("Cookie", source.equals("duplicate-cookie") ? cookie + "; " + cookie : cookie);

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", "{}", headers);

        assertError(response, 422, "VALIDATION_ERROR");
        assertTrue(platform.requests().isEmpty());
        assertTrue(platform.sessionActive());
        assertNoSecretInLogs(token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"query", "header"})
    void test_refreshTokenCannotFallBackToQueryOrHeader(String source) throws Exception {
        String token = platform.refreshToken();
        String path = "/auth/v1/refresh";
        Map<String, String> headers = browserHeaders();
        if (source.equals("query")) path += "?refreshToken=" + token;
        else headers.put("X-Refresh-Token", token);

        HttpResponse<String> response = send("POST", path, "{}", headers);

        assertError(response, 422, "VALIDATION_ERROR");
        assertTrue(platform.requests().isEmpty());
        assertFalse(response.body().contains(token));
        assertNoSecretInLogs(token);
    }

    @Test
    void test_jsonRefreshIgnoresConflictingLegacyCookie() throws Exception {
        Map<String, String> headers = browserHeaders();
        String oldToken = platform.refreshToken();
        String cookieToken = "stale-cookie-secret-must-not-be-used";
        headers.put("Cookie", "__Secure-operations-refresh=" + cookieToken);

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(oldToken), headers);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(oldToken, json.readTree(platform.requests().getFirst().body()).path("refreshToken").asString());
        assertNull(platform.requests().getFirst().header("Cookie"));
        assertNoSecretInLogs(oldToken, platform.refreshToken(), cookieToken);
    }

    @Test
    void test_currentPermissionDenialStopsManagementAndIsRechecked() throws Exception {
        HttpResponse<String> allowed = send("GET", "/iam/v1/accounts", null, trustedHeaders("operations-account-list"));
        assertEquals(200, allowed.statusCode(), allowed.body());
        assertEquals(1, json.readTree(allowed.body()).path("total").asInt());
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH), calledPaths());
        platform.allow(false);

        HttpResponse<String> denied = send("GET", "/iam/v1/accounts", null, trustedHeaders("operations-account-list"));

        assertError(denied, 403, "OPERATIONS_PERMISSION_DENIED");
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH, AUTHORIZE_PATH), calledPaths());
        assertEquals("operations-account-list", json.readTree(platform.requests().getLast().body()).path("routeCode").asString());
    }

    @ParameterizedTest
    @CsvSource({"/iam/v1/accounts,operations-account-list,/operations/v1/accounts,1",
        "/iam/v1/roles,operations-role-list,/operations/v1/roles,1",
        "/audit/v1/events,operations-audit-list,/operations/v1/audit-events,0"})
    void test_pagedQueriesUseSharedBodyAndDefaultTen(String path, String routeCode, String upstreamPath, int total)
        throws Exception {
        HttpResponse<String> response = send("GET", path, null, trustedHeaders(routeCode));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(Set.of("items", "page", "size", "total"), body.propertyNames());
        assertEquals(1, body.path("page").asInt());
        assertEquals(10, body.path("size").asInt());
        assertEquals(total, body.path("total").asInt());
        assertEquals(total, body.path("items").size());
        List<String> expectedCalls = path.equals("/iam/v1/roles")
            ? List.of(AUTHORIZE_PATH, upstreamPath, ROLE_COUNTS_PATH) : List.of(AUTHORIZE_PATH, upstreamPath);
        assertEquals(expectedCalls, calledPaths());
        assertEquals("page=1&size=10", platform.requests().get(1).query());
    }

    @ParameterizedTest
    @CsvSource({"/iam/v1/accounts,operations-account-list,/operations/v1/accounts,10,1",
        "/iam/v1/roles,operations-role-list,/operations/v1/roles,20,1",
        "/audit/v1/events,operations-audit-list,/operations/v1/audit-events,50,0",
        "/iam/v1/accounts,operations-account-list,/operations/v1/accounts,100,1",
        "/iam/v1/roles,operations-role-list,/operations/v1/roles,200,1"})
    void test_paginationForwardsSupportedSizesAndPreservesEmptyLaterPage(String path, String routeCode,
        String upstreamPath, int size, int total) throws Exception {
        HttpResponse<String> response = send("GET", path + "?page=2&size=" + size, null,
            trustedHeaders(routeCode));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(Set.of("items", "page", "size", "total"), body.propertyNames());
        assertEquals(2, body.path("page").asInt());
        assertEquals(size, body.path("size").asInt());
        assertEquals(total, body.path("total").asInt());
        assertTrue(body.path("items").isArray());
        assertEquals(0, body.path("items").size());
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
        assertEquals("page=2&size=" + size, platform.requests().getLast().query());
    }

    @ParameterizedTest
    @CsvSource({"/iam/v1/accounts,operations-account-list,/operations/v1/accounts",
        "/iam/v1/roles,operations-role-list,/operations/v1/roles",
        "/audit/v1/events,operations-audit-list,/operations/v1/audit-events"})
    void test_pagedResponseRejectsMissingItemsAsUpstreamProtocolFailure(String path, String routeCode,
        String upstreamPath) throws Exception {
        platform.rawResponse(upstreamPath, "{\"items\":null,\"page\":1,\"size\":10,\"total\":0}");

        HttpResponse<String> response = send("GET", path, null, trustedHeaders(routeCode));

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-Gateway-Realm", "X-Gateway-Channel", "X-Gateway-Actor-Type", "X-Gateway-Actor-Subject",
        "X-Gateway-Session-Id", "X-Gateway-Route-Code", "X-Gateway-Context-Type", "X-Gateway-Tenant-Id",
        "X-Gateway-Tenant-Type", "X-Gateway-Tenant-Member-Id"})
    void test_missingTrustedIdentityHeaderRejectsBeforeAuthorization(String header) throws Exception {
        Map<String, String> headers = trustedHeaders("operations-account-list");
        headers.remove(header);

        HttpResponse<String> response = send("GET", "/iam/v1/accounts", null, headers);

        assertError(response, 401, "OPERATIONS_SESSION_INVALID");
        assertTrue(platform.requests().isEmpty());
    }

    @Test
    void test_roleListAggregatesEmployeeCountsOnceForOnlyTheCurrentPage() throws Exception {
        for (int index = 1; index <= 11; index++) {
            platform.addRole(new RoleResponseBody("custom_" + index, "CUSTOM_" + index, "自定义角色" + index,
                false, index, List.of("p_read")));
        }

        HttpResponse<String> response = send("GET", "/iam/v1/roles?page=1&size=10", null,
            trustedHeaders("operations-role-list"));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(1, body.path("page").asInt());
        assertEquals(10, body.path("size").asInt());
        assertEquals(12, body.path("total").asInt());
        List<String> requestedIds = new ArrayList<>();
        for (JsonNode item : body.path("items")) {
            assertEquals(Set.of("roleId", "roleCode", "roleName", "systemRole", "version", "employeeCount"), item.propertyNames());
            requestedIds.add(item.path("roleId").asString());
            assertEquals(item.path("systemRole").asBoolean() ? 1 : 0, item.path("employeeCount").asLong());
        }
        assertEquals(10, requestedIds.size());
        assertEquals(9, body.path("items").get(9).path("version").asInt());
        assertFalse(requestedIds.contains("custom_10"));
        assertFalse(requestedIds.contains("custom_11"));
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/roles", ROLE_COUNTS_PATH), calledPaths());
        RecordedRequest call = platform.requests().getLast();
        assertEquals("POST", call.method());
        assertEquals(json.valueToTree(Map.of("roleIds", requestedIds)), json.readTree(call.body()));
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
    }

    @Test
    void test_roleCountsAreJoinedByIdRatherThanResponseOrder() throws Exception {
        platform.addRole(new RoleResponseBody("custom_1", "CUSTOM", "自定义角色", false, 4, List.of()));
        platform.rawResponse(ROLE_COUNTS_PATH, """
            {"items":[{"roleId":"custom_1","employeeCount":0},{"roleId":"role_10001","employeeCount":8}]}
            """);

        HttpResponse<String> response = send("GET", "/iam/v1/roles", null, trustedHeaders("operations-role-list"));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode items = json.readTree(response.body()).path("items");
        assertEquals("role_10001", items.get(0).path("roleId").asString());
        assertEquals(8, items.get(0).path("employeeCount").asLong());
        assertEquals("custom_1", items.get(1).path("roleId").asString());
        assertEquals(0, items.get(1).path("employeeCount").asLong());
        assertEquals(4, items.get(1).path("version").asInt());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"items\":null}", "{\"items\":[]}", "{\"items\":[null]}",
        "{\"items\":[{\"roleId\":\"role_10001\",\"employeeCount\":-1}]}",
        "{\"items\":[{\"roleId\":\"role_10001\"}]}",
        "{\"items\":[{\"roleId\":\"role_10001\",\"employeeCount\":null}]}",
        "{\"items\":[{\"roleId\":\"role_10001\",\"employeeCount\":0},{\"roleId\":\"role_10001\",\"employeeCount\":1}]}",
        "{\"items\":[{\"roleId\":\"role_10001\",\"employeeCount\":1},{\"roleId\":\"unrequested\",\"employeeCount\":0}]}"})
    void test_incompleteOrInvalidCountsFailTheEntireRolePage(String responseBody) throws Exception {
        platform.rawResponse(ROLE_COUNTS_PATH, responseBody);

        HttpResponse<String> response = send("GET", "/iam/v1/roles", null, trustedHeaders("operations-role-list"));

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertFalse(response.body().contains("employeeCount"));
        assertFalse(response.body().contains("管理员"));
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/roles", ROLE_COUNTS_PATH), calledPaths());
    }

    @ParameterizedTest
    @ValueSource(strings = {"duplicate-id", "missing-version"})
    void test_invalidRolePageStopsBeforeCountQuery(String invalid) throws Exception {
        Map<String, Object> role = new LinkedHashMap<>(Map.of("roleId", "role_10001", "roleCode", "ADMIN",
            "roleName", "管理员", "systemRole", true, "version", 1));
        if (invalid.equals("missing-version")) role.remove("version");
        List<?> items = invalid.equals("duplicate-id") ? List.of(role, role) : List.of(role);
        platform.rawResponse("/operations/v1/roles", json.writeValueAsString(Map.of("items", items,
            "page", 1, "size", 10, "total", items.size())));

        HttpResponse<String> response = send("GET", "/iam/v1/roles", null, trustedHeaders("operations-role-list"));

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/roles"), calledPaths());
    }

    @Test
    void test_roleCountFailureDoesNotTurnIntoZeroEmployees() throws Exception {
        platform.fail(ROLE_COUNTS_PATH, 503, "SERVICE_UNAVAILABLE");

        HttpResponse<String> response = send("GET", "/iam/v1/roles", null, trustedHeaders("operations-role-list"));

        assertError(response, 503, "DEPENDENCY_UNAVAILABLE");
        assertFalse(response.body().contains("employeeCount"));
        assertEquals(3, platform.requests().size());
    }

    @Test
    void test_customerRealmCannotEnterOperations() throws Exception {
        Map<String, String> headers = trustedHeaders("operations-account-list");
        headers.put("X-Gateway-Realm", "CUSTOMER");

        HttpResponse<String> response = send("GET", "/iam/v1/accounts", null, headers);

        assertError(response, 401, "OPERATIONS_SESSION_INVALID");
        assertTrue(platform.requests().isEmpty());
    }

    @Test
    void test_managementPreservesIdentityWithoutProxyingSensitiveHeaders() throws Exception {
        Map<String, String> headers = trustedHeaders("operations-account-create");
        headers.put("Idempotency-Key", "account-create-idempotency-10001");
        headers.put("Authorization", "Bearer raw-browser-access-secret");
        headers.put("Cookie", "ignored-browser-cookie=raw-cookie-secret");
        headers.put("X-CSRF-Token", "obsolete-browser-marker");
        String body = json.writeValueAsString(Map.of("email", EMAIL, "initialPassword", PASSWORD,
            "roleIds", List.of("role_10001"), "reason", "测试人员创建"));

        HttpResponse<String> response = send("POST", "/iam/v1/accounts", body, headers);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals("account_10002", json.readTree(response.body()).path("accountId").asString());
        assertEquals(Set.of("roleId", "roleCode", "roleName", "systemRole"),
            json.readTree(response.body()).path("roles").get(0).propertyNames());
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH), calledPaths());
        var call = platform.requests().getLast();
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertNull(call.header("Idempotency-Key"));
        assertEquals(REQUEST_ID, call.header("X-Request-Id"));
        assertNull(call.header("Authorization"));
        assertNull(call.header("Cookie"));
        assertNull(call.header("X-CSRF-Token"));
        assertEquals(PASSWORD, json.readTree(call.body()).path("initialPassword").asString());
        assertMetadataEvent("/iam/**", 200);
        assertNoSecretInLogs("raw-browser-access-secret", "raw-cookie-secret", platform.refreshToken());
    }

    @Test
    void test_logoutConsumesJsonTokenAndRevokesSession() throws Exception {
        Map<String, String> headers = trustedHeaders("operations-auth-logout");

        HttpResponse<String> response = send("POST", "/auth/v1/logout", refreshBody(platform.refreshToken()), headers);

        assertEquals(204, response.statusCode(), response.body());
        assertEquals("", response.body());
        assertFalse(platform.sessionActive());
        assertEquals(List.of(AUTHORIZE_PATH, AUTH_PATH + "/logout"), calledPaths());
    }

    @Test
    void test_accountDeletionForwardsBothVersionsAndReasonAsOneCommand() throws Exception {
        prepareDeletableAccount();
        Map<String, String> headers = trustedHeaders("operations-account-delete");
        headers.put("Idempotency-Key", "account-delete-command-10001");
        headers.put("Authorization", "Bearer raw-browser-access-secret");
        headers.put("Cookie", "ignored-browser-cookie=raw-cookie-secret");
        String body = json.writeValueAsString(Map.of("expectedVersion", 3, "expectedMemberVersion", 4,
            "reason", "人员离职，撤销当前访问"));

        HttpResponse<String> response = send("DELETE", "/iam/v1/accounts/account_10002", body, headers);

        assertEquals(204, response.statusCode(), response.body());
        assertEquals("", response.body());
        assertFalse(platform.hasAccount("account_10002"));
        assertTrue(platform.hasAccount(ACCOUNT_ID));
        assertTrue(platform.sessionActive(), "删除其他人员不得影响当前操作人的 Session");
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH + "/account_10002"), calledPaths());
        RecordedRequest call = platform.requests().getLast();
        assertEquals("DELETE", call.method());
        assertEquals(json.readTree(body), json.readTree(call.body()));
        assertNull(call.header("Idempotency-Key"));
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertEquals(REQUEST_ID, call.header("X-Request-Id"));
        assertNull(call.header("Authorization"));
        assertNull(call.header("Cookie"));
        assertMetadataEvent("/iam/**", 204);
        assertNoSecretInLogs("人员离职，撤销当前访问", "raw-browser-access-secret", "raw-cookie-secret");
    }

    @ParameterizedTest
    @CsvSource({"account_10001,1,1,400,BUSINESS_CHECK_FAILED,业务校验未通过",
        "account_10002,2,4,409,CONCURRENT_REQUEST,请求发生并发冲突，请稍后重试",
        "account_10002,3,2,409,CONCURRENT_REQUEST,请求发生并发冲突，请稍后重试"})
    void test_accountDeletionPreservesPlatformBusinessAndVersionFailures(String accountId, int version,
        int memberVersion, int status, String code, String message) throws Exception {
        prepareDeletableAccount();
        Map<String, String> headers = trustedHeaders("operations-account-delete");
        headers.put("Idempotency-Key", "account-delete-rejected-10001");
        String body = json.writeValueAsString(Map.of("expectedVersion", version, "expectedMemberVersion", memberVersion,
            "reason", "删除校验测试"));

        HttpResponse<String> response = send("DELETE", "/iam/v1/accounts/" + accountId, body, headers);

        assertError(response, status, code);
        assertEquals(message, json.readTree(response.body()).path("detail").asString());
        assertTrue(platform.hasAccount(accountId));
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH + "/" + accountId), calledPaths());
        assertMetadataEvent("/iam/**", status);
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-account-version", "zero-account-version", "missing-member-version",
        "negative-member-version", "blank-reason", "long-reason"})
    void test_invalidAccountDeletionStopsBeforePlatformCommand(String invalid) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("expectedVersion", 1, "expectedMemberVersion", 1, "reason", "删除校验"));
        switch (invalid) {
            case "missing-account-version" -> body.remove("expectedVersion");
            case "zero-account-version" -> body.put("expectedVersion", 0);
            case "missing-member-version" -> body.remove("expectedMemberVersion");
            case "negative-member-version" -> body.put("expectedMemberVersion", -1);
            case "blank-reason" -> body.put("reason", " ");
            case "long-reason" -> body.put("reason", "长".repeat(501));
        }
        Map<String, String> headers = trustedHeaders("operations-account-delete");
        headers.put("Idempotency-Key", "account-delete-invalid-10001");

        HttpResponse<String> response = send("DELETE", "/iam/v1/accounts/account_10001", json.writeValueAsString(body), headers);

        int status = invalid.startsWith("missing-") ? 400 : 422;
        assertError(response, status, status == 400 ? "INVALID_REQUEST" : "VALIDATION_ERROR");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
        assertTrue(platform.hasAccount(ACCOUNT_ID));
    }

    @ParameterizedTest
    @CsvSource({"missing-origin,403,OPERATIONS_ORIGIN_REJECTED", "foreign-origin,403,OPERATIONS_ORIGIN_REJECTED",
        "plain-text,415,OPERATIONS_UNSUPPORTED_MEDIA_TYPE", "malformed-json,400,INVALID_REQUEST"})
    void test_accountDeletionRequiresOriginAndJson(String invalid, int status, String code) throws Exception {
        Map<String, String> headers = trustedHeaders("operations-account-delete");
        switch (invalid) {
            case "missing-origin" -> headers.remove("Origin");
            case "foreign-origin" -> headers.put("Origin", "https://untrusted.example.test");
            case "plain-text" -> headers.put("Content-Type", "text/plain");
        }
        String body = invalid.equals("malformed-json") ? "{" : protectedBody("DELETE", "/iam/v1/accounts/account_10001");

        HttpResponse<String> response = send("DELETE", "/iam/v1/accounts/account_10001", body, headers);

        assertError(response, status, code);
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
        assertEquals(1, platform.completedAuthorizationCount());
        assertTrue(platform.hasAccount(ACCOUNT_ID));
    }

    private void prepareDeletableAccount() {
        platform.addAccount(new AccountResponseBody("account_10002", "p***@example.test", "ACTIVE", false, null,
            3, "member_10002", 4, List.of()));
    }

    @Test
    void test_accountEnablementForwardsVersionAndReasonAsOneCommand() throws Exception {
        prepareDisabledAccount();
        Map<String, String> headers = trustedHeaders("operations-account-enable");
        headers.put("Idempotency-Key", "account-enable-command-10001");
        headers.put("Authorization", "Bearer raw-browser-access-secret");
        headers.put("Cookie", "ignored-browser-cookie=raw-cookie-secret");
        String body = json.writeValueAsString(Map.of("expectedVersion", 3, "reason", "人员停用检查完成，恢复访问"));

        HttpResponse<String> response = send("POST", "/iam/v1/accounts/account_10002/enable", body, headers);

        assertEquals(204, response.statusCode(), response.body());
        assertEquals("", response.body());
        assertEquals("ACTIVE", platform.account("account_10002").getStatus());
        assertEquals(4, platform.account("account_10002").getVersion());
        assertEquals(4, platform.account("account_10002").getMemberVersion());
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH + "/account_10002/enable"), calledPaths());
        RecordedRequest call = platform.requests().getLast();
        assertEquals("POST", call.method());
        assertEquals(json.readTree(body), json.readTree(call.body()));
        assertNull(call.header("Idempotency-Key"));
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertEquals(REQUEST_ID, call.header("X-Request-Id"));
        assertNull(call.header("Authorization"));
        assertNull(call.header("Cookie"));
        assertMetadataEvent("/iam/**", 204);
        assertNoSecretInLogs("人员停用检查完成，恢复访问", "raw-browser-access-secret", "raw-cookie-secret");
    }

    @ParameterizedTest
    @CsvSource({"account_10001,1,400,BUSINESS_CHECK_FAILED,业务校验未通过",
        "account_10002,2,409,CONCURRENT_REQUEST,请求发生并发冲突，请稍后重试"})
    void test_accountEnablementPreservesPlatformBusinessAndVersionFailures(String accountId, int version,
        int status, String code, String message) throws Exception {
        prepareDisabledAccount();
        Map<String, String> headers = trustedHeaders("operations-account-enable");
        headers.put("Idempotency-Key", "account-enable-rejected-10001");
        String body = json.writeValueAsString(Map.of("expectedVersion", version, "reason", "启用校验测试"));

        HttpResponse<String> response = send("POST", "/iam/v1/accounts/" + accountId + "/enable", body, headers);

        assertError(response, status, code);
        assertEquals(message, json.readTree(response.body()).path("detail").asString());
        assertEquals("DISABLED", platform.account("account_10002").getStatus());
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH + "/" + accountId + "/enable"), calledPaths());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing-version", "zero-version", "blank-reason", "long-reason",
        "missing-origin", "plain-text"})
    void test_invalidAccountEnablementStopsBeforePlatformCommand(String invalid) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>(Map.of("expectedVersion", 3, "reason", "启用校验"));
        Map<String, String> headers = trustedHeaders("operations-account-enable");
        switch (invalid) {
            case "missing-version" -> body.remove("expectedVersion");
            case "zero-version" -> body.put("expectedVersion", 0);
            case "blank-reason" -> body.put("reason", " ");
            case "long-reason" -> body.put("reason", "长".repeat(501));
            case "missing-origin" -> headers.remove("Origin");
            case "plain-text" -> headers.put("Content-Type", "text/plain");
        }
        prepareDisabledAccount();

        HttpResponse<String> response = send("POST", "/iam/v1/accounts/account_10002/enable", json.writeValueAsString(body), headers);

        int status = invalid.equals("missing-origin") ? 403 : invalid.equals("plain-text") ? 415
            : invalid.equals("missing-version") ? 400 : 422;
        String code = status == 403 ? "OPERATIONS_ORIGIN_REJECTED"
            : status == 415 ? "OPERATIONS_UNSUPPORTED_MEDIA_TYPE"
            : status == 422 ? "VALIDATION_ERROR" : "INVALID_REQUEST";
        assertError(response, status, code);
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
        assertEquals(1, platform.completedAuthorizationCount());
        assertEquals("DISABLED", platform.account("account_10002").getStatus());
    }

    private void prepareDisabledAccount() {
        platform.addAccount(new AccountResponseBody("account_10002", "p***@example.test", "DISABLED", false, null,
            3, "member_10002", 4, List.of()));
    }

    @Test
    void test_logoutAuthorizationDenialDoesNotClaimSessionRevocation() throws Exception {
        platform.allow(false);
        String token = platform.refreshToken();
        Map<String, String> headers = trustedHeaders("operations-auth-logout");

        HttpResponse<String> response = send("POST", "/auth/v1/logout", refreshBody(platform.refreshToken()), headers);

        assertError(response, 403, "OPERATIONS_PERMISSION_DENIED");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths(), "授权拒绝后不得调用 Session 撤销接口");
        assertTrue(platform.sessionActive(), "授权拒绝不得撤销服务端 Session");
        assertEquals(token, platform.refreshToken());
        assertMetadataEvent("/auth/**", 403);
        assertNoSecretInLogs(token);
    }

    @Test
    void test_passwordChangeRevokesSessionWithoutCookies() throws Exception {
        String body = json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", "new-sensitive-password-for-simulator"));

        HttpResponse<String> response = send("POST", "/auth/v1/password", body, trustedHeaders("operations-auth-password"));

        assertEquals(204, response.statusCode(), response.body());
        assertFalse(platform.sessionActive());
        assertEquals(List.of(AUTHORIZE_PATH, AUTH_PATH + "/password"), calledPaths());
        assertNoSecretInLogs("new-sensitive-password-for-simulator");
    }

    @ParameterizedTest
    @CsvSource({"OPERATIONS_AUTHENTICATION_FAILED,401,账号或凭据无效", "OPERATIONS_SESSION_INVALID,401,登录已失效，请重新登录",
        "OPERATIONS_PERMISSION_DENIED,403,没有执行此操作的权限"})
    void test_legacyPlatformErrorCodeUsesRegisteredLocalDetail(String code, int expectedStatus,
        String expectedMessage) throws Exception {
        platform.legacyError(AUTH_PATH + "/login", 400, code);

        HttpResponse<String> response = send("POST", "/auth/v1/login", loginBody(PASSWORD), browserHeaders());

        assertError(response, expectedStatus, code);
        assertEquals(expectedMessage, json.readTree(response.body()).path("detail").asString());
        assertUpstreamMaterialHidden(response);
        assertEquals(1, platform.requests().size());
    }

    @ParameterizedTest
    @CsvSource({"OPERATIONS_AUTHENTICATION_FAILED,401,账号或凭据无效", "OPERATIONS_SESSION_INVALID,401,登录已失效，请重新登录",
        "OPERATIONS_PERMISSION_DENIED,403,没有执行此操作的权限"})
    void test_problemPlatformErrorCodeUsesRegisteredLocalDetailAndIgnoresExtension(String code, int expectedStatus,
        String expectedMessage) throws Exception {
        platform.problem(AUTH_PATH + "/login", 400, code);

        HttpResponse<String> response = send("POST", "/auth/v1/login", loginBody(PASSWORD), browserHeaders());

        assertError(response, expectedStatus, code);
        assertEquals(expectedMessage, json.readTree(response.body()).path("detail").asString());
        assertUpstreamMaterialHidden(response);
        assertEquals(1, platform.requests().size(), "已注册 Problem 只允许一次 Platform 调用");
    }

    @ParameterizedTest
    @CsvSource({"BUSINESS_CHECK_FAILED,400,不能停用当前登录账号,业务校验未通过",
        "INVALID_REQUEST,400,分页大小必须在 1 到 100 之间,请求参数不正确",
        "CONCURRENT_REQUEST,409,账号资料已被更新，请刷新后重试,请求发生并发冲突，请稍后重试"})
    void test_legacyCommonErrorUsesLocalDefaultInsteadOfUpstreamMessage(String code, int status,
        String upstreamMessage, String expectedMessage) throws Exception {
        platform.rawResponse(ACCOUNTS_PATH, status, "application/json", json.writeValueAsString(Map.of(
            "code", code, "message", upstreamMessage, "requestId", UPSTREAM_REQUEST_ID,
            "timestamp", "2026-09-04T10:00:00Z")));

        HttpResponse<String> response = send("GET", "/iam/v1/accounts", null, trustedHeaders("operations-account-list"));

        assertError(response, status, code);
        assertEquals(expectedMessage, json.readTree(response.body()).path("detail").asString());
        assertFalse(response.body().contains(upstreamMessage));
        assertUpstreamMaterialHidden(response);
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH), calledPaths());
        assertMetadataEvent("/iam/**", status);
    }

    @Test
    void test_refreshRejectionDoesNotLeakToken() throws Exception {
        platform.fail(AUTH_PATH + "/refresh", 400, "OPERATIONS_SESSION_INVALID");
        Map<String, String> headers = browserHeaders();

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(platform.refreshToken()), headers);

        assertError(response, 401, "OPERATIONS_SESSION_INVALID");
        assertNoSecretInLogs(platform.refreshToken());
    }

    @ParameterizedTest
    @ValueSource(ints = {500, 503})
    void test_platformServerFailureCannotInvalidateRefreshSession(int upstreamStatus) throws Exception {
        platform.fail(AUTH_PATH + "/refresh", upstreamStatus, "OPERATIONS_SESSION_INVALID");
        String token = platform.refreshToken();
        Map<String, String> headers = browserHeaders();

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(platform.refreshToken()), headers);

        assertError(response, 503, "DEPENDENCY_UNAVAILABLE");
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "JSON Token 契约不得设置 Cookie");
        assertTrue(platform.sessionActive());
        assertEquals(0, platform.generation());
        assertEquals(token, platform.refreshToken());
        assertEquals(List.of(AUTH_PATH + "/refresh"), calledPaths());
        assertFalse(response.body().contains(PASSWORD));
        assertFalse(response.body().contains(EMAIL));
        assertFalse(response.body().contains(token));
        assertMetadataEvent("/auth/**", 503);
        assertNoSecretInLogs(token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"BUSINESS_CHECK_FAILED", "INVALID_REQUEST", "CONCURRENT_REQUEST"})
    void test_serverFailureCannotExposeCommonBusinessMessage(String code) throws Exception {
        platform.fail(AUTH_PATH + "/refresh", 500, code);

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(platform.refreshToken()), browserHeaders());

        assertError(response, 503, "DEPENDENCY_UNAVAILABLE");
        assertFalse(response.body().contains(PASSWORD));
        assertFalse(response.body().contains(EMAIL));
        assertTrue(platform.sessionActive());
    }

    @ParameterizedTest
    @CsvSource({"400,OPERATIONS_FUTURE_REJECTION", "401,OPERATIONS_FUTURE_REJECTION",
        "403,OPERATIONS_SESSION_INVALID_SUFFIX"})
    void test_unknownOperationsErrorCodeIsRejected(int upstreamStatus, String code) throws Exception {
        platform.fail(AUTH_PATH + "/refresh", upstreamStatus, code);
        String token = platform.refreshToken();
        Map<String, String> headers = browserHeaders();

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(platform.refreshToken()), headers);

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        assertTrue(platform.sessionActive());
        assertEquals(List.of(AUTH_PATH + "/refresh"), calledPaths());
        assertFalse(response.body().contains(code));
        assertFalse(response.body().contains(PASSWORD));
        assertFalse(response.body().contains(EMAIL));
        assertMetadataEvent("/auth/**", 502);
        assertNoSecretInLogs(token);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed", "unknown-code", "request-id-mismatch"})
    void test_invalidProblemResponseFailsClosedWithoutLeakingUpstreamMaterial(String invalid) throws Exception {
        String path = AUTH_PATH + "/refresh";
        switch (invalid) {
            case "malformed" -> platform.rawResponse(path, 400, "application/problem+json",
                "{\"type\":\"about:blank\",\"detail\":\"" + UPSTREAM_DETAIL_CANARY + PASSWORD,
                Map.of("X-Request-Id", UPSTREAM_REQUEST_ID));
            case "unknown-code" -> platform.problem(path, 400, "OPERATIONS_FUTURE_REJECTION");
            case "request-id-mismatch" -> platform.problem(path, 400, "OPERATIONS_SESSION_INVALID",
                "platform-header-request", "platform-body-request");
            default -> throw new AssertionError(invalid);
        }
        String token = platform.refreshToken();

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(token), browserHeaders());

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(1, platform.requests().size(), "非法 Problem 不得自动重试");
        assertTrue(platform.sessionActive());
        assertFalse(response.body().contains("OPERATIONS_FUTURE_REJECTION"));
        assertFalse(response.body().contains("platform-header-request"));
        assertFalse(response.body().contains("platform-body-request"));
        assertUpstreamMaterialHidden(response);
        assertNoSecretInLogs(token, UPSTREAM_DETAIL_CANARY, UPSTREAM_EXTENSION_CANARY);
    }

    @Test
    void test_compliantUpstream503MapsToOwnedUnavailableWithoutLeakOrRetry() throws Exception {
        platform.problem(AUTH_PATH + "/refresh", 503, "INTERNAL_ERROR");
        String token = platform.refreshToken();

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(token), browserHeaders());

        assertError(response, 503, "DEPENDENCY_UNAVAILABLE");
        assertEquals("依赖服务暂时不可用，请稍后重试", json.readTree(response.body()).path("detail").asString());
        assertEquals(1, platform.requests().size(), "上游 5xx 不得自动重试");
        assertTrue(platform.sessionActive());
        assertUpstreamMaterialHidden(response);
        assertNoSecretInLogs(token, UPSTREAM_DETAIL_CANARY, UPSTREAM_EXTENSION_CANARY);
    }

    @Test
    void test_compliantUpstream504MapsToOwnedTimeoutWithoutLeakOrRetry() throws Exception {
        platform.problem(AUTH_PATH + "/refresh", 504, "DEPENDENCY_TIMEOUT");
        String token = platform.refreshToken();

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(token), browserHeaders());

        assertError(response, 504, "DEPENDENCY_TIMEOUT");
        assertEquals("依赖服务响应超时，请稍后重试", json.readTree(response.body()).path("detail").asString());
        assertEquals(1, platform.requests().size(), "上游 504 不得自动重试");
        assertTrue(platform.sessionActive());
        assertUpstreamMaterialHidden(response);
        assertNoSecretInLogs(token, UPSTREAM_DETAIL_CANARY, UPSTREAM_EXTENSION_CANARY);
    }

    @Test
    void test_headDependencyFailureMapsStatusAndWritesNoResponseBytes() throws Exception {
        platform.problem(ACCOUNTS_PATH, 503, "INTERNAL_ERROR");
        Map<String, String> headers = trustedHeaders("operations-account-list");
        headers.remove("Origin");
        long completedBefore = completionLogCount();
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/iam/v1/accounts"))
            .timeout(Duration.ofSeconds(8));
        headers.forEach(request::header);

        HttpResponse<byte[]> response = CLIENT.send(request.method("HEAD", HttpRequest.BodyPublishers.noBody()).build(),
            HttpResponse.BodyHandlers.ofByteArray());

        await().atMost(Duration.ofSeconds(2)).until(() -> completionLogCount() == completedBefore + 1);
        assertHeadProblem(response, 503, REQUEST_ID);
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH), calledPaths());
        assertMetadataEvent("/iam/**", 503);
    }

    @Test
    void test_localPlatformReadTimeoutMapsToOwned504WithoutRetry() throws Exception {
        platform.delay(AUTH_PATH + "/login");
        HttpResponse<String> response;
        try {
            response = send("POST", "/auth/v1/login", loginBody(PASSWORD), browserHeaders());
        } finally {
            platform.releaseDelay();
        }

        assertError(response, 504, "DEPENDENCY_TIMEOUT");
        assertEquals(1, platform.requests().size(), "读取超时不得自动重试");
        assertEquals(AUTH_PATH + "/login", platform.requests().getFirst().path());
        assertMetadataEvent("/auth/**", 504);
    }

    @Test
    void test_dependencyMapperKeepsValidatedResponseCauseDirectly() {
        platform.problem(AUTH_PATH + "/login", 504, "DEPENDENCY_TIMEOUT");

        ErrorException timeout = assertThrows(ErrorException.class,
            () -> platformAuthenticationApi.login(new OperationsLoginRequestBody(EMAIL, PASSWORD)));

        assertEquals(OperationsBffErrorCode.DEPENDENCY_TIMEOUT, timeout.getErrorCode());
        RestClientResponseException timeoutCause = assertInstanceOf(RestClientResponseException.class,
            timeout.getCause());
        assertTrue(timeoutCause.getResponseBodyAsString().contains(UPSTREAM_EXTENSION_CANARY));
        assertEquals(1, platform.requests().size());

        platform.reset();
        platform.problem(AUTH_PATH + "/login", 503, "INTERNAL_ERROR");
        ServiceUnavailableException unavailable = assertThrows(ServiceUnavailableException.class,
            () -> platformAuthenticationApi.login(new OperationsLoginRequestBody(EMAIL, PASSWORD)));

        assertEquals(OperationsBffErrorCode.DEPENDENCY_UNAVAILABLE, unavailable.getErrorCode());
        RestClientResponseException unavailableCause = assertInstanceOf(RestClientResponseException.class,
            unavailable.getCause());
        assertTrue(unavailableCause.getResponseBodyAsString().contains(UPSTREAM_EXTENSION_CANARY));
        assertEquals(1, platform.requests().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"empty", "json-null"})
    void test_emptyAuthorizationResponseStopsBeforeManagementCall(String content) throws Exception {
        platform.rawResponse(AUTHORIZE_PATH, content.equals("empty") ? "" : "null");

        HttpResponse<String> response = send("GET", "/iam/v1/accounts", null,
            trustedHeaders("operations-account-list"));

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths(), "授权协议异常后不得调用人员管理 API");
        assertTrue(platform.sessionActive());
        assertMetadataEvent("/iam/**", 502);
    }

    @ParameterizedTest
    @ValueSource(strings = {"malformed-json", "html"})
    void test_malformedPlatformErrorBodyIsRejectedWithoutSensitiveResponseOrLogs(String content) throws Exception {
        String marker = "sensitive-upstream-error-fragment";
        String body = content.equals("html") ? "<html>" + marker + PASSWORD + EMAIL + "</html>"
            : "{\"message\":\"" + marker + PASSWORD + EMAIL;
        platform.rawResponse(AUTH_PATH + "/refresh", 400,
            content.equals("html") ? "text/html" : "application/json", body);
        String token = platform.refreshToken();
        Map<String, String> headers = browserHeaders();

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", refreshBody(platform.refreshToken()), headers);

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        assertEquals(List.of(AUTH_PATH + "/refresh"), calledPaths());
        assertFalse(response.body().contains(marker));
        assertFalse(response.body().contains(PASSWORD));
        assertFalse(response.body().contains(EMAIL));
        assertMetadataEvent("/auth/**", 502);
        assertNoSecretInLogs(marker, token);
    }

    @Test
    void test_platformNetworkFailureReturnsSafe503() throws Exception {
        platform.disconnect();

        HttpResponse<String> response = send("POST", "/auth/v1/login", loginBody(PASSWORD), browserHeaders());

        assertError(response, 503, "DEPENDENCY_UNAVAILABLE");
        assertEquals(1, platform.requests().size(), "连接中断不得自动重试");
        assertEquals(AUTH_PATH + "/login", platform.requests().getFirst().path());
    }

    @ParameterizedTest
    @ValueSource(strings = {"password", "logout"})
    void test_platformRedirectCannotSucceedAsVoidCommand(String command) throws Exception {
        platform.rawResponse(AUTH_PATH + "/" + command, 302, "application/json", "{}");
        Map<String, String> headers = trustedHeaders("operations-auth-" + command);
        String body = command.equals("password") ? json.writeValueAsString(Map.of("currentPassword", PASSWORD,
            "newPassword", "new-sensitive-password-for-simulator")) : refreshBody(platform.refreshToken());

        HttpResponse<String> response = send("POST", "/auth/v1/" + command, body, headers);

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertTrue(platform.sessionActive(), "上游未执行任何 Session 状态变化");
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        assertEquals(List.of(AUTHORIZE_PATH, AUTH_PATH + "/" + command), calledPaths());
    }

    @ParameterizedTest
    @CsvSource({"context,empty", "context,malformed", "bootstrap/administrator,empty", "bootstrap/administrator,malformed"})
    void test_requiredDtoRejectsEmptyOrMalformedSuccess(String command, String content) throws Exception {
        platform.rawResponse(AUTH_PATH + "/" + command, content.equals("empty") ? "" : "sensitive-success-body-fragment");
        boolean bootstrap = command.startsWith("bootstrap");
        String body = bootstrap ? json.writeValueAsString(Map.of("email", EMAIL, "initialPassword", PASSWORD)) : null;

        HttpResponse<String> response = send(bootstrap ? "POST" : "GET", "/auth/v1/" + command, body,
            trustedHeaders("operations-auth-command"));

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "JSON Token 契约不得设置 Cookie");
        assertTrue(platform.sessionActive());
        assertEquals(List.of(AUTHORIZE_PATH, AUTH_PATH + "/" + command), calledPaths());
        assertNoSecretInLogs("sensitive-success-body-fragment");
    }

    @Test
    void test_navigationAndContextUseCurrentAuthorization() throws Exception {
        HttpResponse<String> context = send("GET", "/auth/v1/context", null, trustedHeaders("operations-auth-context"));
        HttpResponse<String> navigation = send("GET", "/authorization/v1/navigation", null,
            trustedHeaders("operations-navigation"));

        assertEquals(200, context.statusCode(), context.body());
        assertEquals(ACCOUNT_ID, json.readTree(context.body()).path("accountId").asString());
        assertEquals(200, navigation.statusCode(), navigation.body());
        assertEquals("OPERATIONS_SYSTEM", json.readTree(navigation.body()).path("resources").get(0).path("resourceCode").asString());
        assertEquals(List.of(AUTHORIZE_PATH, AUTH_PATH + "/context", AUTHORIZE_PATH,
            "/authorization/v1/console-navigation"), calledPaths());
    }

    @Test
    void test_unmappedSensitivePathAndInvalidJsonNeverReachPlatformOrLeakLogs() throws Exception {
        HttpResponse<String> unmapped = send("POST", "/auth/v1/unknown/" + SECRET_QUERY + "?password=" + SECRET_QUERY,
            loginBody(PASSWORD), browserHeaders());
        HttpResponse<String> invalid = send("POST", "/auth/v1/login", "{\"password\":\"" + PASSWORD + "\"INVALID", browserHeaders());

        assertError(unmapped, 404, "NOT_FOUND");
        assertError(invalid, 400, "INVALID_REQUEST");
        assertTrue(platform.requests().isEmpty());
        assertNoSecretInLogs(SECRET_QUERY, PASSWORD);
    }

    @ParameterizedTest
    @ValueSource(strings = {"text/plain", "application/x-www-form-urlencoded"})
    void test_unsupportedMediaTypeRejectsBeforePlatformWithoutLoggingBody(String contentType) throws Exception {
        Map<String, String> headers = browserHeaders();
        headers.put("Content-Type", contentType);

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", loginBody(PASSWORD), headers);

        assertError(response, 415, "OPERATIONS_UNSUPPORTED_MEDIA_TYPE");
        assertTrue(platform.requests().isEmpty());
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty());
        assertFalse(response.body().contains(PASSWORD));
        assertFalse(response.body().contains(EMAIL));
        assertMetadataEvent("/auth/**", 415);
    }

    @ParameterizedTest
    @CsvSource({"/iam/v1/accounts,operations-account-list,page=0,/iam/**",
        "/audit/v1/events,operations-audit-list,size=101,/audit/**",
        "/iam/v1/roles,operations-role-list,size=1,/iam/**",
        "/iam/v1/accounts,operations-account-list,size=11,/iam/**",
        "/audit/v1/events,operations-audit-list,size=201,/audit/**",
        "/iam/v1/roles,operations-role-list,page=100001,/iam/**",
        "/iam/v1/roles,operations-role-list,page=abc,/iam/**"})
    void test_invalidPaginationNeverCallsManagement(String path, String routeCode, String query, String safePath) throws Exception {
        HttpResponse<String> response = send("GET", path + "?" + query + "&unused=" + SECRET_QUERY, null,
            trustedHeaders(routeCode));

        assertError(response, 400, "INVALID_REQUEST");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
        assertFalse(response.body().contains(SECRET_QUERY));
        assertMetadataEvent(safePath, 400);
    }

    @Test
    void test_invalidManagementBodyNeverCallsManagementOrLogsCredentials() throws Exception {
        Map<String, String> headers = trustedHeaders("operations-account-create");
        headers.put("Idempotency-Key", "account-create-idempotency-10001");
        String body = json.writeValueAsString(Map.of("email", EMAIL, "initialPassword", PASSWORD,
            "roleIds", List.of(), "reason", SECRET_QUERY));

        HttpResponse<String> response = send("POST", "/iam/v1/accounts", body, headers);

        assertError(response, 422, "VALIDATION_ERROR");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
        assertFalse(response.body().contains(PASSWORD));
        assertFalse(response.body().contains(EMAIL));
        assertFalse(response.body().contains(SECRET_QUERY));
        assertMetadataEvent("/iam/**", 422);
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void test_everyProtectedEndpointRequiresIdentity(String method, String path, String routeCode) throws Exception {
        Map<String, String> headers = browserHeaders();
        headers.put("Idempotency-Key", "account-command-idempotency-10001");

        HttpResponse<String> response = send(method, path, protectedBody(method, path), headers);

        assertError(response, 401, "OPERATIONS_SESSION_INVALID");
        assertTrue(platform.requests().isEmpty(), "缺少身份时不得调用授权或业务 API");
        assertTrue(platform.sessionActive());
        assertNoSecretInLogs(platform.refreshToken());
    }

    @ParameterizedTest
    @MethodSource("protectedEndpoints")
    void test_everyProtectedEndpointStopsAfterSingleDeniedAuthorization(String method, String path, String routeCode) throws Exception {
        platform.allow(false);
        Map<String, String> headers = trustedHeaders(routeCode);
        headers.put("Idempotency-Key", "account-command-idempotency-10001");

        HttpResponse<String> response = send(method, path, protectedBody(method, path), headers);

        assertError(response, 403, "OPERATIONS_PERMISSION_DENIED");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths(), "统一授权只能执行一次，拒绝后不得执行目标操作");
        RecordedRequest authorization = platform.requests().getFirst();
        assertEquals(routeCode, json.readTree(authorization.body()).path("routeCode").asString());
        assertEquals(ACCOUNT_ID, authorization.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, authorization.header("X-Gateway-Session-Id"));
        assertTrue(platform.sessionActive());
        assertNoSecretInLogs(platform.refreshToken());
    }

    @ParameterizedTest
    @ValueSource(strings = {"X-Gateway-Route-Code", "X-Gateway-Actor-Subject", "X-Gateway-Realm"})
    void test_duplicateTrustedHeaderRejectsBeforeAuthorization(String header) throws Exception {
        Map<String, String> headers = trustedHeaders("operations-account-list");
        headers.put(header.toLowerCase(java.util.Locale.ROOT), headers.get(header));

        HttpResponse<String> response = send("GET", "/iam/v1/accounts", null, headers);

        assertError(response, 401, "OPERATIONS_SESSION_INVALID");
        assertTrue(platform.requests().isEmpty());
    }

    @Test
    void test_queryCannotReplaceTrustedAuthorizationContext() throws Exception {
        HttpResponse<String> response = send("GET", "/iam/v1/accounts?routeCode=operations-auth-login&accountId=attacker",
            null, trustedHeaders("operations-account-list"));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH), calledPaths());
        RecordedRequest authorization = platform.requests().getFirst();
        assertEquals("operations-account-list", json.readTree(authorization.body()).path("routeCode").asString());
        assertEquals(ACCOUNT_ID, authorization.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, authorization.header("X-Gateway-Session-Id"));
    }

    @Test
    void test_publicRouteCodeCannotExemptProtectedHandler() throws Exception {
        platform.allow(false);

        HttpResponse<String> response = send("GET", "/iam/v1/accounts", null, trustedHeaders("operations-auth-login"));

        assertError(response, 403, "OPERATIONS_PERMISSION_DENIED");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
    }

    @Test
    void test_sameOriginGetWithoutOriginStillAuthorizes() throws Exception {
        Map<String, String> headers = trustedHeaders("operations-account-list");
        headers.remove("Origin");

        HttpResponse<String> response = send("GET", "/iam/v1/accounts", null, headers);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH, ACCOUNTS_PATH), calledPaths());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "blank", "oversize"})
    void test_invalidJsonRefreshCredentialFailsBeforePlatform(String invalid) throws Exception {
        String body = invalid.equals("null") ? "{\"refreshToken\":null}"
            : refreshBody(invalid.equals("blank") ? " " : "sensitive-refresh-marker-".repeat(32));

        HttpResponse<String> response = send("POST", "/auth/v1/refresh", body, browserHeaders());

        assertError(response, 422, "VALIDATION_ERROR");
        assertTrue(platform.requests().isEmpty());
        assertNoSecretInLogs("sensitive-refresh-marker-");
    }

    @Test
    void test_roleOptionsAggregateAuthorizedResourcesAndUnboundPermissions() throws Exception {
        HttpResponse<String> response = send("GET", "/iam/v1/role-permission-options", null,
            trustedHeaders("operations-role-permission-options"));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(Set.of("nodes"), body.propertyNames());
        JsonNode root = body.path("nodes").get(0);
        assertEquals(Set.of("nodeId", "displayName", "permissionId", "children"), root.propertyNames());
        assertEquals("resource:root", root.path("nodeId").asString());
        assertEquals("p_menu", root.path("permissionId").asString());
        JsonNode roleActions = root.path("children").get(0).path("children");
        assertEquals(3, roleActions.size());
        assertEquals(ROLE_CREATE_PERMISSION_ID, roleActions.get(0).path("permissionId").asString());
        assertEquals(ROLE_UPDATE_PERMISSION_ID, roleActions.get(1).path("permissionId").asString());
        assertEquals(ROLE_DELETE_PERMISSION_ID, roleActions.get(2).path("permissionId").asString());
        assertNotEquals(roleActions.get(0).path("nodeId"), roleActions.get(1).path("nodeId"));
        JsonNode accountActions = root.path("children").get(1).path("children");
        assertEquals(5, accountActions.size());
        assertEquals(ACCOUNT_CREATE_PERMISSION_ID, accountActions.get(0).path("permissionId").asString());
        assertEquals(ACCOUNT_DISABLE_PERMISSION_ID, accountActions.get(1).path("permissionId").asString());
        assertEquals(ACCOUNT_ASSIGN_ROLES_PERMISSION_ID, accountActions.get(2).path("permissionId").asString());
        assertEquals(ACCOUNT_DELETE_PERMISSION_ID, accountActions.get(3).path("permissionId").asString());
        assertEquals(ACCOUNT_ENABLE_PERMISSION_ID, accountActions.get(4).path("permissionId").asString());
        assertEquals("permission:p_unbound", body.path("nodes").get(1).path("children").get(0).path("nodeId").asString());
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/role-permissions", "/operations/v1/role-resources"), calledPaths());
        assertMetadataEvent("/iam/**", 200);
    }

    @Test
    void test_roleDetailsJoinRoleVersionSelectionsAndPermissionTree() throws Exception {
        HttpResponse<String> response = send("GET", "/iam/v1/roles/role_10001", null,
            trustedHeaders("operations-role-detail"));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(Set.of("roleId", "roleCode", "roleName", "systemRole", "version", "permissionIds", "nodes"), body.propertyNames());
        assertEquals("role_10001", body.path("roleId").asString());
        assertEquals(1, body.path("version").asInt());
        assertEquals(json.valueToTree(List.of("p_menu", "p_read")), body.path("permissionIds"));
        assertEquals(2, body.path("nodes").size());
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/roles/role_10001", "/operations/v1/role-permissions",
            "/operations/v1/role-resources"), calledPaths());
    }

    @Test
    void test_roleWritesForwardOneCommandAndReturnFlatNewVersion() throws Exception {
        Map<String, String> createHeaders = trustedHeaders("operations-role-create");
        createHeaders.put("Idempotency-Key", "role-create-command-10001");
        String create = json.writeValueAsString(Map.of("roleCode", "CUSTOM_READER", "roleName", "自定义审阅",
            "permissionIds", List.of("p_read", "p_menu"), "reason", "创建权限测试"));
        HttpResponse<String> created = send("POST", "/iam/v1/roles", create, createHeaders);
        assertEquals(200, created.statusCode(), created.body());
        JsonNode role = json.readTree(created.body());
        assertEquals(Set.of("roleId", "roleCode", "roleName", "systemRole", "version", "permissionIds"), role.propertyNames());
        assertEquals(1, role.path("version").asInt());

        Map<String, String> updateHeaders = trustedHeaders("operations-role-update");
        updateHeaders.put("Idempotency-Key", "role-update-command-10001");
        String update = json.writeValueAsString(Map.of("roleName", "已撤销权限", "expectedVersion", 1,
            "permissionIds", List.of(), "reason", "清空权限测试"));
        HttpResponse<String> updated = send("PUT", "/iam/v1/roles/role_20001", update, updateHeaders);
        assertEquals(200, updated.statusCode(), updated.body());
        assertEquals(2, json.readTree(updated.body()).path("version").asInt());
        assertEquals(0, json.readTree(updated.body()).path("permissionIds").size());
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/roles", AUTHORIZE_PATH, "/operations/v1/roles/role_20001"), calledPaths());
        var createCall = platform.requests().get(1);
        var updateCall = platform.requests().get(3);
        assertNull(createCall.header("Idempotency-Key"));
        assertNull(updateCall.header("Idempotency-Key"));
        assertEquals(json.readTree(create), json.readTree(createCall.body()));
        assertEquals(json.readTree(update), json.readTree(updateCall.body()));
        assertEquals(ACCOUNT_ID, updateCall.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, updateCall.header("X-Gateway-Session-Id"));
        assertEquals(REQUEST_ID, updateCall.header("X-Request-Id"));
        assertNoSecretInLogs("自定义审阅", "清空权限测试");
    }

    @Test
    void test_roleDeletionForwardsVersionAndReasonAsOneCommand() throws Exception {
        platform.addRole(new RoleResponseBody("custom_1", "CUSTOM", "待删除角色", false, 3, List.of("p_read")));
        Map<String, String> headers = trustedHeaders("operations-role-delete");
        headers.put("Idempotency-Key", "role-delete-command-10001");
        headers.put("Authorization", "Bearer raw-browser-access-secret");
        headers.put("Cookie", "ignored-browser-cookie=raw-cookie-secret");
        String body = json.writeValueAsString(Map.of("expectedVersion", 3, "reason", "停止使用此角色"));

        HttpResponse<String> response = send("DELETE", "/iam/v1/roles/custom_1", body, headers);

        assertEquals(204, response.statusCode(), response.body());
        assertEquals("", response.body());
        assertFalse(platform.hasRole("custom_1"));
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/roles/custom_1"), calledPaths());
        RecordedRequest call = platform.requests().getLast();
        assertEquals("DELETE", call.method());
        assertEquals(json.readTree(body), json.readTree(call.body()));
        assertNull(call.header("Idempotency-Key"));
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertEquals(REQUEST_ID, call.header("X-Request-Id"));
        assertNull(call.header("Authorization"));
        assertNull(call.header("Cookie"));
        assertMetadataEvent("/iam/**", 204);
        assertNoSecretInLogs("停止使用此角色", "raw-browser-access-secret", "raw-cookie-secret");
    }

    @Test
    void test_roleDeletionPreservesPlatformVersionConflict() throws Exception {
        platform.addRole(new RoleResponseBody("custom_1", "CUSTOM", "待删除角色", false, 2, List.of()));
        Map<String, String> headers = trustedHeaders("operations-role-delete");
        headers.put("Idempotency-Key", "role-delete-stale-10001");

        HttpResponse<String> response = send("DELETE", "/iam/v1/roles/custom_1",
            json.writeValueAsString(Map.of("expectedVersion", 1, "reason", "旧版本删除")), headers);

        assertError(response, 409, "CONCURRENT_REQUEST");
        assertEquals("请求发生并发冲突，请稍后重试", json.readTree(response.body()).path("detail").asString());
        assertTrue(platform.hasRole("custom_1"));
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/roles/custom_1"), calledPaths());
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"expectedVersion\":0,\"reason\":\"测试删除\"}",
        "{\"expectedVersion\":1,\"reason\":\" \"}"})
    void test_invalidRoleDeletionStopsBeforePlatformCommand(String body) throws Exception {
        Map<String, String> headers = trustedHeaders("operations-role-delete");
        headers.put("Idempotency-Key", "role-delete-invalid-10001");

        HttpResponse<String> response = send("DELETE", "/iam/v1/roles/role_10001", body, headers);

        assertError(response, body.equals("{}") ? 400 : 422,
            body.equals("{}") ? "INVALID_REQUEST" : "VALIDATION_ERROR");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
        assertTrue(platform.hasRole("role_10001"));
    }

    @Test
    void test_roleDeletionRequiresExactOriginAfterAuthorizationWithoutBusinessCommand() throws Exception {
        Map<String, String> headers = trustedHeaders("operations-role-delete");
        headers.remove("Origin");
        headers.put("Idempotency-Key", "role-delete-origin-10001");

        HttpResponse<String> response = send("DELETE", "/iam/v1/roles/role_10001",
            protectedBody("DELETE", "/iam/v1/roles/role_10001"), headers);

        assertError(response, 403, "OPERATIONS_ORIGIN_REJECTED");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
        assertEquals(1, platform.completedAuthorizationCount());
        assertTrue(platform.hasRole("role_10001"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"lowercase-code", "blank-name", "missing-permissions", "blank-permission"})
    void test_invalidRoleCreationStopsBeforePlatformCommand(String invalid) throws Exception {
        Map<String, Object> payload = new LinkedHashMap<>(Map.of("roleCode", "TEST_ROLE", "roleName", "测试角色",
            "permissionIds", List.of("p_read"), "reason", "参数测试"));
        switch (invalid) {
            case "lowercase-code" -> payload.put("roleCode", "invalid");
            case "blank-name" -> payload.put("roleName", "   ");
            case "missing-permissions" -> payload.remove("permissionIds");
            case "blank-permission" -> payload.put("permissionIds", List.of(""));
        }
        Map<String, String> headers = trustedHeaders("operations-role-create");
        headers.put("Idempotency-Key", "role-invalid-command-10001");
        HttpResponse<String> response = send("POST", "/iam/v1/roles", json.writeValueAsString(payload), headers);
        assertError(response, 422, "VALIDATION_ERROR");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
    }

    @Test
    void test_roleAggregationDoesNotReturnPartialResultWhenResourcesFail() throws Exception {
        platform.fail("/operations/v1/role-resources", 503, "SERVICE_UNAVAILABLE");
        HttpResponse<String> response = send("GET", "/iam/v1/roles/role_10001", null, trustedHeaders("operations-role-detail"));
        assertError(response, 503, "DEPENDENCY_UNAVAILABLE");
        assertFalse(response.body().contains("nodes"));
        assertFalse(response.body().contains("管理员"));
        assertFalse(response.body().contains(PASSWORD));
        assertEquals(4, platform.requests().size());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{\"permissions\":null}", "{\"permissions\":[null]}"})
    void test_invalidPermissionCatalogFailsBeforeResourceQuery(String body) throws Exception {
        platform.rawResponse("/operations/v1/role-permissions", body);
        HttpResponse<String> response = send("GET", "/iam/v1/role-permission-options", null,
            trustedHeaders("operations-role-permission-options"));
        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/role-permissions"), calledPaths());
    }

    @Test
    void test_hiddenRolePermissionCannotBeSilentlyDroppedByTheEditor() throws Exception {
        platform.rawResponse("/operations/v1/roles/role_10001", json.writeValueAsString(Map.of(
            "roleId", "role_10001", "roleCode", "HIDDEN", "roleName", "禁止显示的角色", "systemRole", false,
            "version", 1, "permissionIds", List.of("permission-outside-current-scope"))));
        HttpResponse<String> response = send("GET", "/iam/v1/roles/role_10001", null, trustedHeaders("operations-role-detail"));
        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertFalse(response.body().contains("permission-outside-current-scope"));
        assertFalse(response.body().contains("禁止显示的角色"));
    }

    @ParameterizedTest
    @MethodSource("actionPermissionEndpoints")
    void test_independentActionPermissionControlsTrustedRouteAndNavigation(String method, String path, String routeCode,
                                                                          String permissionId, String buttonCode) throws Exception {
        platform.grantPermissions("p_menu", "p_read", "p_account_read", permissionId);
        HttpResponse<String> navigation = send("GET", "/authorization/v1/navigation", null, trustedHeaders("operations-navigation"));
        assertEquals(List.of(buttonCode), navigationButtons(navigation));

        int before = platform.requests().size();
        HttpResponse<String> allowed = send(method, path, method.equals("GET") ? null : "{}", trustedHeaders(routeCode));
        // 写请求在授权后故意停在参数校验，避免本测试修改角色或人员事实。
        if (method.equals("GET")) {
            assertEquals(200, allowed.statusCode(), allowed.body());
        } else if (path.equals("/iam/v1/accounts") || path.equals("/iam/v1/roles")) {
            assertError(allowed, 422, "VALIDATION_ERROR");
        } else {
            assertError(allowed, 400, "INVALID_REQUEST");
        }
        assertEquals(AUTHORIZE_PATH, platform.requests().get(before).path());
        assertEquals(routeCode, json.readTree(platform.requests().get(before).body()).path("routeCode").asString());
        if (!method.equals("GET")) assertEquals(before + 1, platform.requests().size());

        String differentPermission = permissionId.equals(ACCOUNT_ENABLE_PERMISSION_ID) ? ACCOUNT_DISABLE_PERMISSION_ID
            : permissionId.equals(ROLE_CREATE_PERMISSION_ID) ? ROLE_UPDATE_PERMISSION_ID : ROLE_CREATE_PERMISSION_ID;
        platform.grantPermissions("p_menu", "p_read", "p_account_read", differentPermission);
        before = platform.requests().size();
        HttpResponse<String> denied = send(method, path, method.equals("GET") ? null : "{}", trustedHeaders(routeCode));
        assertError(denied, 403, "OPERATIONS_PERMISSION_DENIED");
        assertEquals(before + 1, platform.requests().size(), "不同操作的权限不能通过当前 Route 授权");
        assertEquals(AUTHORIZE_PATH, platform.requests().getLast().path());
        HttpResponse<String> after = send("GET", "/authorization/v1/navigation", null, trustedHeaders("operations-navigation"));
        assertFalse(navigationButtons(after).contains(buttonCode), "撤销当前动作权限后按钮必须同步消失");
    }

    private List<String> navigationButtons(HttpResponse<String> response) {
        assertEquals(200, response.statusCode(), response.body());
        List<String> result = new ArrayList<>();
        for (JsonNode resource : json.readTree(response.body()).path("resources")) {
            if (resource.path("resourceType").asString().equals("BUTTON")) result.add(resource.path("resourceCode").asString());
        }
        return result;
    }

    private static Stream<Arguments> actionPermissionEndpoints() {
        return Stream.of(
            Arguments.of("POST", "/iam/v1/accounts", "operations-account-create", ACCOUNT_CREATE_PERMISSION_ID, "OPERATIONS_ACCOUNT_CREATE"),
            Arguments.of("POST", "/iam/v1/accounts/account_10001/disable", "operations-account-disable", ACCOUNT_DISABLE_PERMISSION_ID, "OPERATIONS_ACCOUNT_DISABLE"),
            Arguments.of("PUT", "/iam/v1/accounts/account_10001/roles", "operations-account-roles", ACCOUNT_ASSIGN_ROLES_PERMISSION_ID, "OPERATIONS_ACCOUNT_ASSIGN_ROLES"),
            Arguments.of("DELETE", "/iam/v1/accounts/account_10001", "operations-account-delete", ACCOUNT_DELETE_PERMISSION_ID, "OPERATIONS_ACCOUNT_DELETE"),
            Arguments.of("POST", "/iam/v1/accounts/account_10001/enable", "operations-account-enable", ACCOUNT_ENABLE_PERMISSION_ID, "OPERATIONS_ACCOUNT_ENABLE"),
            Arguments.of("GET", "/iam/v1/role-permission-options", "operations-role-permission-options", ROLE_CREATE_PERMISSION_ID, "OPERATIONS_ROLE_CREATE"),
            Arguments.of("POST", "/iam/v1/roles", "operations-role-create", ROLE_CREATE_PERMISSION_ID, "OPERATIONS_ROLE_CREATE"),
            Arguments.of("GET", "/iam/v1/roles/role_10001", "operations-role-detail", ROLE_UPDATE_PERMISSION_ID, "OPERATIONS_ROLE_EDIT"),
            Arguments.of("PUT", "/iam/v1/roles/role_10001", "operations-role-update", ROLE_UPDATE_PERMISSION_ID, "OPERATIONS_ROLE_EDIT"),
            Arguments.of("DELETE", "/iam/v1/roles/role_10001", "operations-role-delete", ROLE_DELETE_PERMISSION_ID, "OPERATIONS_ROLE_DELETE"));
    }

    private static Stream<Arguments> protectedEndpoints() {
        return Stream.of(
            Arguments.of("POST", "/auth/v1/logout", "operations-auth-logout"),
            Arguments.of("GET", "/auth/v1/context", "operations-auth-context"),
            Arguments.of("POST", "/auth/v1/password", "operations-auth-password"),
            Arguments.of("POST", "/auth/v1/bootstrap/administrator", "operations-bootstrap-administrator"),
            Arguments.of("GET", "/authorization/v1/navigation", "operations-navigation"),
            Arguments.of("GET", "/iam/v1/accounts", "operations-account-list"),
            Arguments.of("POST", "/iam/v1/accounts", "operations-account-create"),
            Arguments.of("POST", "/iam/v1/accounts/account_10001/disable", "operations-account-disable"),
            Arguments.of("PUT", "/iam/v1/accounts/account_10001/roles", "operations-account-roles"),
            Arguments.of("DELETE", "/iam/v1/accounts/account_10001", "operations-account-delete"),
            Arguments.of("POST", "/iam/v1/accounts/account_10001/enable", "operations-account-enable"),
            Arguments.of("GET", "/iam/v1/roles", "operations-role-list"),
            Arguments.of("GET", "/iam/v1/role-permission-options", "operations-role-permission-options"),
            Arguments.of("GET", "/iam/v1/roles/role_10001", "operations-role-detail"),
            Arguments.of("POST", "/iam/v1/roles", "operations-role-create"),
            Arguments.of("PUT", "/iam/v1/roles/role_10001", "operations-role-update"),
            Arguments.of("DELETE", "/iam/v1/roles/role_10001", "operations-role-delete"),
            Arguments.of("GET", "/audit/v1/events", "operations-audit-list"),
            Arguments.of("GET", "/audit/v1/events/audit_10001", "operations-audit-detail"),
            Arguments.of("GET", "/gateway-admin/v1/target-services", "operations-gateway-target-list"),
            Arguments.of("GET", "/gateway-admin/v1/target-services/operations-console-bff", "operations-gateway-target-detail"),
            Arguments.of("PATCH", "/gateway-admin/v1/target-services/operations-console-bff/status", "operations-gateway-target-status"),
            Arguments.of("GET", "/gateway-admin/v1/routes", "operations-gateway-route-list"),
            Arguments.of("GET", "/gateway-admin/v1/routes/operations-auth-login", "operations-gateway-route-detail"),
            Arguments.of("PATCH", "/gateway-admin/v1/routes/operations-auth-login/status", "operations-gateway-route-status"));
    }

    private String protectedBody(String method, String path) {
        if (method.equals("GET")) return null;
        if (method.equals("DELETE") && path.startsWith("/iam/v1/accounts/")) {
            return json.writeValueAsString(Map.of("expectedVersion", 1, "expectedMemberVersion", 1, "reason", "测试删除"));
        }
        if (method.equals("DELETE")) return json.writeValueAsString(Map.of("expectedVersion", 1, "reason", "测试删除"));
        if (path.endsWith("/logout")) return refreshBody(platform.refreshToken());
        if (path.endsWith("/password")) return json.writeValueAsString(Map.of("currentPassword", PASSWORD, "newPassword", PASSWORD + "-changed"));
        if (path.endsWith("/administrator")) return json.writeValueAsString(Map.of("email", EMAIL, "initialPassword", PASSWORD));
        if (path.endsWith("/disable")) return json.writeValueAsString(Map.of("expectedVersion", 1, "reason", "测试禁用"));
        if (path.endsWith("/enable")) return json.writeValueAsString(Map.of("expectedVersion", 1, "reason", "测试启用"));
        if (path.equals("/iam/v1/roles")) return json.writeValueAsString(Map.of("roleCode", "TEST_ROLE", "roleName", "测试角色",
            "permissionIds", List.of("p_read"), "reason", "测试创建"));
        if (path.startsWith("/iam/v1/roles/")) return json.writeValueAsString(Map.of("expectedVersion", 1, "roleName", "测试角色",
            "permissionIds", List.of("p_read"), "reason", "测试修改"));
        if (method.equals("PUT")) return json.writeValueAsString(Map.of("expectedMemberVersion", 1, "roleIds", List.of("role_10001"), "reason", "测试分配"));
        return json.writeValueAsString(Map.of("email", EMAIL, "initialPassword", PASSWORD,
            "roleIds", List.of("role_10001"), "reason", "测试创建"));
    }

    private Map<String, String> browserHeaders() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Origin", ORIGIN);
        headers.put(RequestIdFilter.HEADER_NAME, REQUEST_ID);
        return headers;
    }

    private Map<String, String> trustedHeaders(String routeCode) {
        Map<String, String> headers = browserHeaders();
        headers.put("X-Gateway-Realm", "OPERATIONS");
        headers.put("X-Gateway-Channel", "OPERATIONS");
        headers.put("X-Gateway-Actor-Type", "PLATFORM_USER");
        headers.put("X-Gateway-Actor-Subject", ACCOUNT_ID);
        headers.put("X-Gateway-Session-Id", SESSION_ID);
        headers.put("X-Gateway-Route-Code", routeCode);
        headers.put("X-Gateway-Context-Type", "TENANT");
        headers.put("X-Gateway-Tenant-Type", "INTERNAL");
        headers.put("X-Gateway-Tenant-Id", "tenant_10001");
        headers.put("X-Gateway-Tenant-Member-Id", "member_10001");
        return headers;
    }

    private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
        long completedBefore = completionLogCount();
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(8));
        headers.forEach(builder::header);
        if (body != null && headers.keySet().stream().noneMatch(name -> name.equalsIgnoreCase("Content-Type"))) {
            builder.header("Content-Type", "application/json");
        }
        builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
        HttpResponse<String> response = CLIENT.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        await().atMost(Duration.ofSeconds(2)).until(() -> completionLogCount() == completedBefore + 1);
        assertTrue(response.headers().allValues("Set-Cookie").isEmpty(), "JSON Token 契约不得读写 Cookie");
        return response;
    }

    private long completionLogCount() {
        return appender.list.stream()
            .filter(event -> event.getLoggerName().equals(HttpRequestResponseLoggingFilter.class.getName())).count();
    }

    private String refreshBody(String token) { return json.writeValueAsString(Map.of("refreshToken", token)); }
    private String loginBody(String password) { return json.writeValueAsString(Map.of("loginIdentifier", EMAIL, "password", password)); }
    private List<String> calledPaths() { return platform.requests().stream().map(RecordedRequest::path).toList(); }

    @Test
    void test_auditQueryForwardsAllFiltersAndKeepsExclusiveEndAndPaging() throws Exception {
        platform.addAuditEvent(auditEvent("audit_before", "2026-09-04T09:59:59Z"));
        platform.addAuditEvent(auditEvent("audit_start", "2026-09-04T10:00:00Z"));
        platform.addAuditEvent(auditEvent("audit_end", "2026-09-04T11:00:00Z"));
        Map<String, String> headers = trustedHeaders("operations-audit-list");
        headers.remove("Origin");
        HttpResponse<String> response = send("GET", "/audit/v1/events?page=1&size=20"
            + "&occurredFrom=2026-09-04T10:00:00Z&occurredTo=2026-09-04T11:00:00Z"
            + "&actorId=%20UNKNOWN%20&actionCode=OPERATIONS_ROLE_UPDATE&result=SUCCEEDED&requestId=historical-request",
            null, headers);

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(1, body.path("total").asInt());
        assertEquals(20, body.path("size").asInt());
        assertEquals("audit_start", body.path("items").get(0).path("auditEventId").asString());
        assertEquals(List.of(AUTHORIZE_PATH, AUDIT_PATH), calledPaths());
        RecordedRequest call = platform.requests().getLast();
        Map<String, String> query = new LinkedHashMap<>();
        for (String part : call.query().split("&")) {
            String[] pair = part.split("=", 2);
            query.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        assertEquals(Map.of("page", "1", "size", "20", "occurredFrom", "2026-09-04T10:00:00Z",
            "occurredTo", "2026-09-04T11:00:00Z", "actorId", "UNKNOWN", "actionCode", "OPERATIONS_ROLE_UPDATE",
            "result", "SUCCEEDED", "requestId", "historical-request"), query);
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertMetadataEvent("/audit/**", 200);
        assertNoSecretInLogs("historical-request");
    }

    @ParameterizedTest
    @ValueSource(strings = {"occurredFrom=2026-09-04T10:00:00Z", "occurredTo=2026-09-04T11:00:00Z",
        "actorId=%20&actionCode=&result=%20&requestId="})
    void test_auditQuerySupportsOneSidedTimeAndEmptyOptionalFilters(String query) throws Exception {
        platform.addAuditEvent(auditEvent("audit_10001", "2026-09-04T10:00:00Z"));
        var response = send("GET", "/audit/v1/events?" + query, null, trustedHeaders("operations-audit-list"));
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(1, json.readTree(response.body()).path("total").asInt());
    }

    @ParameterizedTest
    @CsvSource({"actorId,129", "actionCode,65", "requestId,129"})
    void test_auditQueryRejectsOversizedExactFilters(String field, int length) throws Exception {
        var response = send("GET", "/audit/v1/events?" + field + "=" + "x".repeat(length), null,
            trustedHeaders("operations-audit-list"));
        assertError(response, 400, "INVALID_REQUEST");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
    }

    @Test
    void test_auditDetailUsesReadPermissionAndAllowsNoHistoricalAttributes() throws Exception {
        platform.addAuditEvent(auditEvent("audit_10001", "2026-09-04T10:00:00Z"));
        platform.grantPermissions(AUDIT_READ_PERMISSION_ID);
        var response = send("GET", "/audit/v1/events/audit_10001", null, trustedHeaders("operations-audit-detail"));
        assertEquals(200, response.statusCode(), response.body());
        assertEquals(0, json.readTree(response.body()).path("changes").size());
        assertEquals(0, json.readTree(response.body()).path("attributes").size());
        int before = platform.requests().size();
        platform.grantPermissions(ROLE_UPDATE_PERMISSION_ID);
        var denied = send("GET", "/audit/v1/events/audit_10001", null, trustedHeaders("operations-audit-detail"));
        assertError(denied, 403, "OPERATIONS_PERMISSION_DENIED");
        assertEquals(before + 1, platform.requests().size());
        assertEquals(AUTHORIZE_PATH, platform.requests().getLast().path());
    }

    @Test
    void test_missingAuditDetailKeepsSafePlatformBusinessError() throws Exception {
        var response = send("GET", "/audit/v1/events/audit_missing", null, trustedHeaders("operations-audit-detail"));
        assertError(response, 400, "BUSINESS_CHECK_FAILED");
        assertEquals("业务校验未通过", json.readTree(response.body()).path("detail").asString());
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "[null]", "[{\"number\":0,\"key\":\"roleId\",\"value\":\"p\"}]",
        "[{\"number\":1,\"key\":\"\",\"value\":\"p\"}]",
        "[{\"number\":1,\"key\":\"before.\",\"value\":\"p\"}]",
        "[{\"number\":1,\"key\":\"roleId\",\"value\":\"p\"},{\"number\":1,\"key\":\"roleId\",\"value\":\"q\"}]"})
    void test_malformedAuditAttributesFailClosed(String attributes) throws Exception {
        platform.rawResponse(AUDIT_PATH + "/audit_10001", "{\"event\":"
            + json.writeValueAsString(auditEvent("audit_10001", "2026-09-04T10:00:00Z").getEvent())
            + ",\"attributes\":" + attributes + "}");
        var response = send("GET", "/audit/v1/events/audit_10001", null, trustedHeaders("operations-audit-detail"));
        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "{}", "{\"auditEventId\":\"different-event\"}"})
    void test_missingOrMismatchedAuditIdentityFailsClosed(String event) throws Exception {
        platform.rawResponse(AUDIT_PATH + "/audit_10001", "{\"event\":" + event + ",\"attributes\":[]}");
        var response = send("GET", "/audit/v1/events/audit_10001", null, trustedHeaders("operations-audit-detail"));
        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
    }

    private AuditEventResponseBody auditEvent(String id, String time) {
        return new AuditEventResponseBody(new AuditEvent(id, "UNKNOWN", "OPERATIONS_ROLE_UPDATE", "ROLE", "role_10001",
            "SUCCEEDED", "historical-request", Instant.parse(time), "历史操作原因"), List.of());
    }

    @Test
    void test_auditDetailsPreserveOrderedHistoricalValuesAndUnknownAttributes() throws Exception {
        platform.rawResponse("/operations/v1/audit-events/audit_10001", """
            {"event":{"auditEventId":"audit_10001","actorId":"UNKNOWN","actionCode":"OPERATIONS_ROLE_UPDATE",
              "resourceType":"ROLE","resourceId":"role_10001","result":"SUCCEEDED","requestId":"historical-request",
              "occurredTime":"2026-09-04T10:00:00Z","reason":"历史操作原因"},"attributes":[
              {"number":8,"key":"futureKey","value":null},
              {"number":3,"key":"before.permissionId","value":"p2"},
              {"number":1,"key":"reason","value":"历史操作原因"},
              {"number":2,"key":"before.permissionId","value":"p1"},
              {"number":4,"key":"before.permissionId","value":"p1"},
              {"number":5,"key":"after.permissionId","value":null},
              {"number":6,"key":"before.roleId","value":"old-role"},
              {"number":7,"key":"futureKey","value":"历史未知属性"}]}
            """);
        HttpResponse<String> response = send("GET", "/audit/v1/events/audit_10001", null,
            trustedHeaders("operations-audit-detail"));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(Set.of("event", "changes", "attributes"), body.propertyNames());
        assertEquals("UNKNOWN", body.path("event").path("actorId").asString());
        assertEquals(json.readTree("[\"p1\",\"p2\",\"p1\"]"), body.path("changes").get(0).path("before"));
        assertEquals(json.readTree("[null]"), body.path("changes").get(0).path("after"));
        assertEquals("权限 ID", body.path("changes").get(0).path("label").asString());
        assertEquals(json.readTree("[]"), body.path("changes").get(1).path("after"));
        assertEquals(1, body.path("attributes").size());
        assertEquals("futureKey", body.path("attributes").get(0).path("label").asString());
        assertEquals(json.readTree("[\"历史未知属性\",null]"), body.path("attributes").get(0).path("values"));
        assertEquals(List.of(AUTHORIZE_PATH, "/operations/v1/audit-events/audit_10001"), calledPaths());
        assertMetadataEvent("/audit/**", 200);
        assertNoSecretInLogs("历史操作原因", "历史未知属性", "historical-request");
    }

    @ParameterizedTest
    @ValueSource(strings = {"occurredFrom=invalid", "occurredFrom=2026-09-04T10:00:00Z&occurredTo=2026-09-04T10:00:00Z",
        "occurredFrom=2026-09-04T11:00:00Z&occurredTo=2026-09-04T10:00:00Z", "result=UNKNOWN"})
    void test_invalidAuditFiltersStopBeforeQuery(String query) throws Exception {
        HttpResponse<String> response = send("GET", "/audit/v1/events?" + query, null,
            trustedHeaders("operations-audit-list"));
        assertError(response, 400, "INVALID_REQUEST");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
    }

    @Test
    void test_gatewayTargetListForwardsExactFiltersAndReturnsOnlyLocalPageModel() throws Exception {
        String upstreamPath = "/gateway-admin/v1/target-services";
        platform.rawResponse(upstreamPath, """
            {"items":[{"targetServiceCode":"operations-console-bff","discoveryServiceName":"operations-console-bff",
              "channel":"OPERATIONS","status":"ACTIVE","version":3,"managementProtection":{"editableFields":[],"statusChangeAllowed":false,"reason":"内置管理服务"}}],"page":2,"size":20,"total":21}
            """);

        HttpResponse<String> response = send("GET", "/gateway-admin/v1/target-services?page=2&size=20"
            + "&targetServiceCode=operations-console-bff&channel=OPERATIONS&status=ACTIVE", null,
            trustedHeaders("operations-gateway-target-list"));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(Set.of("items", "page", "size", "total"), body.propertyNames());
        assertEquals(Set.of("targetServiceCode", "discoveryServiceName", "channel", "status", "version", "managementProtection"),
            body.path("items").get(0).propertyNames());
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
        RecordedRequest call = platform.requests().getLast();
        assertEquals(Map.of("page", "2", "size", "20", "targetServiceCode", "operations-console-bff",
            "channel", "OPERATIONS", "status", "ACTIVE"), decodedQuery(call.query()));
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
    }

    @Test
    void test_gatewayPageRejectsTotalSmallerThanReturnedItems() throws Exception {
        String upstreamPath = "/gateway-admin/v1/target-services";
        platform.rawResponse(upstreamPath, """
            {"items":[{"targetServiceCode":"operations-console-bff","discoveryServiceName":"operations-console-bff",
              "channel":"OPERATIONS","status":"ACTIVE","version":3,"managementProtection":{"editableFields":[],"statusChangeAllowed":false,"reason":"内置管理服务"}}],"page":1,"size":10,"total":0}
            """);

        HttpResponse<String> response = send("GET", upstreamPath, null,
            trustedHeaders("operations-gateway-target-list"));

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
    }

    @Test
    void test_gatewayPageRejectsTotalBeforeReturnedSecondPageItem() throws Exception {
        String upstreamPath = "/gateway-admin/v1/target-services";
        platform.rawResponse(upstreamPath, """
            {"items":[{"targetServiceCode":"operations-console-bff","discoveryServiceName":"operations-console-bff",
              "channel":"OPERATIONS","status":"ACTIVE","version":3,"managementProtection":{"editableFields":[],"statusChangeAllowed":false,"reason":"内置管理服务"}}],"page":2,"size":20,"total":1}
            """);

        HttpResponse<String> response = send("GET", upstreamPath + "?page=2&size=20", null,
            trustedHeaders("operations-gateway-target-list"));

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
    }

    @Test
    void test_gatewayPageRejectsEmptyPageBeforeKnownTotal() throws Exception {
        String upstreamPath = "/gateway-admin/v1/target-services";
        platform.rawResponse(upstreamPath, "{\"items\":[],\"page\":2,\"size\":20,\"total\":21}");

        HttpResponse<String> response = send("GET", upstreamPath + "?page=2&size=20", null,
            trustedHeaders("operations-gateway-target-list"));

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
    }

    @Test
    void test_gatewayPageAllowsEmptyPageAtOrAfterKnownTotal() throws Exception {
        String upstreamPath = "/gateway-admin/v1/target-services";
        platform.rawResponse(upstreamPath, "{\"items\":[],\"page\":2,\"size\":20,\"total\":1}");

        HttpResponse<String> response = send("GET", upstreamPath + "?page=2&size=20", null,
            trustedHeaders("operations-gateway-target-list"));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(0, json.readTree(response.body()).path("items").size());
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
    }

    @Test
    void test_gatewayRouteListForwardsRouteCodeAndApprovedExactFilters() throws Exception {
        String upstreamPath = "/gateway-admin/v1/routes";
        platform.rawResponse(upstreamPath, "{\"items\":[],\"page\":1,\"size\":10,\"total\":0}");

        HttpResponse<String> response = send("GET", upstreamPath
            + "?routeCode=operations-gateway-route-list&channel=OPERATIONS&status=ACTIVE"
            + "&targetServiceCode=operations-console-bff", null,
            trustedHeaders("operations-gateway-route-list"));

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(Map.of("page", "1", "size", "10", "routeCode", "operations-gateway-route-list",
            "channel", "OPERATIONS", "status", "ACTIVE", "targetServiceCode", "operations-console-bff"),
            decodedQuery(platform.requests().getLast().query()));
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
    }

    @Test
    void test_gatewayRouteDetailIsOnePlatformAggregationAndKeepsTargetStatusSeparate() throws Exception {
        String upstreamPath = "/gateway-admin/v1/routes/operations-gateway-route-list";
        platform.rawResponse(upstreamPath, gatewayRouteDetail("""
            [{"headerName":"accept-language","headerUse":"CORS_REQUEST_ALLOWED"}]
            """));

        HttpResponse<String> response = send("GET", upstreamPath, null,
            trustedHeaders("operations-gateway-route-detail"));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals("ACTIVE", body.path("status").asString());
        assertEquals("DISABLED", body.path("targetStatus").asString());
        assertEquals("GATEWAY_CONFIG_READ", body.path("permissionCode").asString());
        assertEquals(1, body.path("headers").size());
        assertEquals(6, body.path("guardRules").size());
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths(),
            "详情聚合必须由一次 Platform API 返回，不允许 BFF 发起额外拼装查询");
    }

    @Test
    void test_gatewayRouteDetailAcceptsPlatformMaximumPathLength() throws Exception {
        String upstreamPath = "/gateway-admin/v1/routes/operations-gateway-route-list";
        String maximumPath = "/" + "a".repeat(999);
        platform.rawResponse(upstreamPath, gatewayRouteDetail("""
            [{"headerName":"accept-language","headerUse":"CORS_REQUEST_ALLOWED"}]
            """, maximumPath, maximumPath));

        HttpResponse<String> response = send("GET", upstreamPath, null,
            trustedHeaders("operations-gateway-route-detail"));

        assertEquals(200, response.statusCode(), response.body());
        JsonNode body = json.readTree(response.body());
        assertEquals(maximumPath, body.path("externalPathTemplate").asString());
        assertEquals(maximumPath, body.path("downstreamPathTemplate").asString());
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
    }

    @ParameterizedTest
    @CsvSource({"65536,200", "65537,502"})
    void test_nonRiskGatewayRouteDetailEnforcesInclusiveGenericBodyCeiling(int bodyLimit, int expectedStatus)
        throws Exception {
        String upstreamPath = "/gateway-admin/v1/routes/operations-gateway-route-list";
        platform.rawResponse(upstreamPath, gatewayRouteDetail("[]",
            "/api/operations/gateway-admin/v1/routes", "/gateway-admin/v1/routes", bodyLimit));

        HttpResponse<String> response = send("GET", upstreamPath, null,
            trustedHeaders("operations-gateway-route-detail"));

        assertEquals(expectedStatus, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
        assertEquals(1, platform.completedAuthorizationCount());
        assertEquals("GET", platform.requests().getLast().method());
        if (expectedStatus == 200) {
            JsonNode body = json.readTree(response.body());
            assertEquals(65_536, body.path("bodyLimitBytes").asInt());
            assertEquals("operations-gateway-route-list", body.path("routeCode").asString());
            assertEquals("/gateway-admin/v1/routes", body.path("downstreamPathTemplate").asString());
            assertEquals(6, body.path("guardRules").size());
        } else {
            assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
            assertEquals("依赖服务响应异常，请稍后重试", json.readTree(response.body()).path("detail").asString());
        }
    }

    @Test
    void test_gatewayRouteStatusForwardsOnePatchWithIdentityVersionReasonWithoutIdempotencyKey() throws Exception {
        String upstreamPath = "/gateway-admin/v1/routes/operations-gateway-route-list/status";
        platform.rawResponse(upstreamPath,
            "{\"routeCode\":\"operations-gateway-route-list\",\"status\":\"DISABLED\",\"version\":4}");
        Map<String, String> headers = trustedHeaders("operations-gateway-route-status");
        headers.put("Idempotency-Key", "gateway-route-status-command-10001");
        String requestBody = "{\"status\":\"DISABLED\",\"expectedVersion\":3,\"reason\":\"紧急维护窗口\"}";

        HttpResponse<String> response = send("PATCH", upstreamPath, requestBody, headers);

        assertEquals(200, response.statusCode(), response.body());
        assertEquals(json.readTree("{\"routeCode\":\"operations-gateway-route-list\",\"status\":\"DISABLED\",\"version\":4}"),
            json.readTree(response.body()));
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
        RecordedRequest call = platform.requests().getLast();
        assertEquals("PATCH", call.method());
        assertNull(call.header("Idempotency-Key"));
        assertEquals(json.readTree(requestBody), json.readTree(call.body()));
        assertEquals(ACCOUNT_ID, call.header("X-Gateway-Actor-Subject"));
        assertEquals(SESSION_ID, call.header("X-Gateway-Session-Id"));
        assertNull(call.header("Authorization"));
        assertNull(call.header("Cookie"));
    }

    @Test
    void test_gatewayStatusRejectsAnUpstreamVersionJump() throws Exception {
        String upstreamPath = "/gateway-admin/v1/routes/operations-gateway-route-list/status";
        platform.rawResponse(upstreamPath,
            "{\"routeCode\":\"operations-gateway-route-list\",\"status\":\"DISABLED\",\"version\":5}");
        Map<String, String> headers = trustedHeaders("operations-gateway-route-status");
        headers.put("Idempotency-Key", "gateway-route-status-command-10002");

        HttpResponse<String> response = send("PATCH", upstreamPath,
            "{\"status\":\"DISABLED\",\"expectedVersion\":3,\"reason\":\"紧急维护窗口\"}", headers);

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
    }

    @Test
    void test_gatewayDetailRejectsDuplicateNormalizedHeadersAsUpstreamProtocolFailure() throws Exception {
        String upstreamPath = "/gateway-admin/v1/routes/operations-gateway-route-list";
        platform.rawResponse(upstreamPath, gatewayRouteDetail("""
            [{"headerName":"Accept-Language","headerUse":"CORS_REQUEST_ALLOWED"},
             {"headerName":"accept-language","headerUse":"CORS_REQUEST_ALLOWED"}]
            """));

        HttpResponse<String> response = send("GET", upstreamPath, null,
            trustedHeaders("operations-gateway-route-detail"));

        assertError(response, 502, "UPSTREAM_INVALID_RESPONSE");
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
        assertFalse(response.body().contains("Accept-Language"));
    }

    @Test
    void test_missingGatewayDetailPreservesTypedNotFoundForStaleDrawerRecovery() throws Exception {
        String upstreamPath = "/gateway-admin/v1/routes/removed-route";
        platform.fail(upstreamPath, 404, "GATEWAY_CONFIGURATION_NOT_FOUND");

        HttpResponse<String> response = send("GET", upstreamPath, null,
            trustedHeaders("operations-gateway-route-detail"));

        assertError(response, 404, "GATEWAY_CONFIGURATION_NOT_FOUND");
        assertEquals("Gateway 配置不存在", json.readTree(response.body()).path("detail").asString());
        assertEquals(List.of(AUTHORIZE_PATH, upstreamPath), calledPaths());
        assertFalse(response.body().contains("敏感上游细节"));
    }

    @Test
    void test_invalidGatewayStatusBodyStopsAfterRouteAuthorization() throws Exception {
        Map<String, String> headers = trustedHeaders("operations-gateway-target-status");
        headers.put("Idempotency-Key", "gateway-target-status-command-10001");

        HttpResponse<String> response = send("PATCH",
            "/gateway-admin/v1/target-services/operations-console-bff/status",
            "{\"status\":\"ACTIVE\",\"expectedVersion\":0,\"reason\":\" \"}", headers);

        assertError(response, 422, "VALIDATION_ERROR");
        assertEquals(List.of(AUTHORIZE_PATH), calledPaths());
    }

    private Map<String, String> decodedQuery(String rawQuery) {
        Map<String, String> result = new LinkedHashMap<>();
        for (String parameter : rawQuery.split("&")) {
            String[] pair = parameter.split("=", 2);
            result.put(pair[0], URLDecoder.decode(pair[1], StandardCharsets.UTF_8));
        }
        return result;
    }

    private String gatewayRouteDetail(String headers) {
        return gatewayRouteDetail(headers, "/api/operations/gateway-admin/v1/routes", "/gateway-admin/v1/routes");
    }

    private String gatewayRouteDetail(String headers, String externalPath, String downstreamPath) {
        return gatewayRouteDetail(headers, externalPath, downstreamPath, 16_384);
    }

    private String gatewayRouteDetail(String headers, String externalPath, String downstreamPath, int bodyLimit) {
        return """
            {"routeCode":"operations-gateway-route-list","channel":"OPERATIONS","httpMethod":"GET",
             "externalPathTemplate":"%s",
             "downstreamPathTemplate":"%s","targetServiceCode":"operations-console-bff",
             "targetStatus":"DISABLED","securityProfileCode":"OPERATIONS_JWT","authorizationMode":"HUMAN_PERMISSION",
             "permissionId":"900000000000000534","permissionCode":"GATEWAY_CONFIG_READ","requiredScopeCode":null,
             "requestCookieMode":"DROP","responseCookieMode":"NONE","bodyLimitBytes":%d,
             "bodyReceiveTimeoutMillis":5000,"responseTimeoutMillis":10000,"status":"ACTIVE","version":3,
             "headers":%s,"queryParameterNames":["channel","status","targetServiceCode"],"guardRules":[
               {"ruleType":"RATE_LIMIT","dimension":"SHARED_ROUTE","capacity":100,"refillTokens":100,"refillPeriodMillis":1000,"concurrencyLimit":null},
               {"ruleType":"CONCURRENCY","dimension":"SHARED_ROUTE","capacity":null,"refillTokens":null,"refillPeriodMillis":null,"concurrencyLimit":20},
               {"ruleType":"RATE_LIMIT","dimension":"CLIENT_IP","capacity":50,"refillTokens":50,"refillPeriodMillis":1000,"concurrencyLimit":null},
               {"ruleType":"CONCURRENCY","dimension":"CLIENT_IP","capacity":null,"refillTokens":null,"refillPeriodMillis":null,"concurrencyLimit":10},
               {"ruleType":"RATE_LIMIT","dimension":"AUTHENTICATED_PRINCIPAL","capacity":50,"refillTokens":50,"refillPeriodMillis":1000,"concurrencyLimit":null},
               {"ruleType":"CONCURRENCY","dimension":"AUTHENTICATED_PRINCIPAL","capacity":null,"refillTokens":null,"refillPeriodMillis":null,"concurrencyLimit":10}],
             "managementProtection":{"editableFields":["headers","queryParameterNames","guardRules","bodyLimitBytes","bodyReceiveTimeoutMillis","responseTimeoutMillis"],"statusChangeAllowed":false,"reason":"内置管理路由"},
             "createdBy":"900000000000000001","updatedBy":"900000000000000001",
             "createTime":"2026-09-05T08:00:00Z","updateTime":"2026-09-05T08:00:01Z"}
            """.formatted(externalPath, downstreamPath, bodyLimit, headers);
    }

    private void assertError(HttpResponse<String> response, int status, String code) {
        assertProblem(json, response, status, code, REQUEST_ID);
    }

    private void assertUpstreamMaterialHidden(HttpResponse<String> response) {
        assertFalse(response.body().contains(UPSTREAM_REQUEST_ID));
        assertFalse(response.body().contains(UPSTREAM_DETAIL_CANARY));
        assertFalse(response.body().contains(UPSTREAM_EXTENSION_CANARY));
        assertFalse(response.body().contains(PASSWORD));
        assertFalse(response.body().contains(EMAIL));
    }

    private void assertMetadataEvent(String safePath, int status) {
        List<ILoggingEvent> events = appender.list.stream()
            .filter(event -> event.getLoggerName().equals(HttpRequestResponseLoggingFilter.class.getName())).toList();
        assertEquals(1, events.size());
        Map<String, Object> fields = new LinkedHashMap<>();
        events.getFirst().getKeyValuePairs().forEach(pair -> fields.put(pair.key, pair.value));
        assertEquals(Set.of("requestId", "method", "uri", "status", "elapsedMs"), fields.keySet());
        assertEquals(REQUEST_ID, fields.get("requestId"));
        assertEquals(safePath, fields.get("uri"));
        assertEquals(status, fields.get("status"));
    }

    private void assertNoSecretInLogs(String... values) {
        for (ILoggingEvent event : appender.list) {
            String text = event.getFormattedMessage() + " " + event.getKeyValuePairs()
                + (event.getThrowableProxy() == null ? "" : ThrowableProxyUtil.asString(event.getThrowableProxy()));
            for (String value : values) assertFalse(text.contains(value), "日志出现敏感测试内容");
        }
    }
}
