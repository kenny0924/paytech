package com.paytech.gateway.auto.acceptance;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.simulator.bff.BffRequestJournal;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore.RequestBarrier;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;

/** Operations Gateway 首期真实协议端到端验收测试。 */
class OperationsGatewayAcceptanceAutoTest extends GatewayAutoTestBase {
    private static final String ORIGIN = "https://operations-console.test";
    private static final String LOGIN_PATH = OperationsTestPaths.LOGIN;
    private static final String REFRESH_PATH = OperationsTestPaths.REFRESH;
    private static final String LOGOUT_PATH = OperationsTestPaths.LOGOUT;
    private static final String REFRESH_COOKIE = "__Secure-operations-refresh=opaque-refresh";
    private static final String REFRESH_BODY = "{\"refreshToken\":\"opaque-refresh\"}";
    private static final String TOKEN_RESPONSE = "{\"accessToken\":\"access-token\",\"tokenType\":\"Bearer\","
        + "\"expiresIn\":900,\"refreshToken\":\"rotated-refresh\",\"refreshExpiresIn\":28800,"
        + "\"forcePasswordChange\":false,\"bootstrap\":false}";

    @LocalServerPort
    private int port;

    @TestBean(name = "operationsJwtClock", methodName = "fixedClock", enforceOverride = true)
    private Clock operationsJwtClock;

    private static Stream<Arguments> localFailureScenarios() {
        return Stream.of(Arguments.of("missing-token", 401, "GATEWAY_UNAUTHORIZED"),
            Arguments.of("forbidden-origin", 403, "GATEWAY_FORBIDDEN"),
            Arguments.of("unknown-route", 404, "GATEWAY_ROUTE_NOT_FOUND"),
            Arguments.of("oversized-body", 413, "GATEWAY_PAYLOAD_TOO_LARGE"));
    }

    private static Clock fixedClock() {
        return Clock.fixed(JwtFixtureBuilder.NOW, ZoneOffset.UTC);
    }

    @Test
    void test_loginHappyPathStripsPrefixAndBuildsOnlyTrustedAnonymousHeaders() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(201, TOKEN_RESPONSE));

        EntityExchangeResult<byte[]> result = gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer caller-value")
            .header(HttpHeaders.COOKIE, "unrelated=value; " + REFRESH_COOKIE)
            .header("X-Gateway-Actor-Subject", "caller-subject").contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"username\":\"operator\"}").exchange().expectStatus().isCreated().expectHeader()
            .doesNotExist(HttpHeaders.SET_COOKIE).expectBody().json(TOKEN_RESPONSE).returnResult();

        BffRequestJournal.Entry forwarded = onlyBffEntry();
        Assertions.assertAll(() -> Assertions.assertEquals("/auth/v1/login", forwarded.path()),
            () -> Assertions.assertEquals(HttpMethod.POST, forwarded.method()),
            () -> Assertions.assertTrue(headerValues(forwarded, HttpHeaders.AUTHORIZATION).isEmpty()),
            () -> Assertions.assertTrue(headerValues(forwarded, HttpHeaders.COOKIE).isEmpty()),
            () -> Assertions.assertTrue(headerValues(forwarded, "X-Gateway-Actor-Subject").isEmpty()),
            () -> Assertions.assertEquals(List.of(responseRequestId(result)), headerValues(forwarded, "X-Request-Id")));
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_terminalResponseRebuildsTrustedRequestIdAndCorsWhilePreservingBusinessHeaders() {
        String forgedRequestId = "11111111-1111-1111-1111-111111111111";
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            new BffScenarioStore.Response(200, "{\"authenticated\":true}", "application/json",
                Map.ofEntries(Map.entry("X-Request-Id", List.of(forgedRequestId)),
                    Map.entry(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, List.of("https://attacker.test")),
                    Map.entry(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, List.of("false")),
                    Map.entry(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, List.of("GET")),
                    Map.entry(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS, List.of("X-Attacker")),
                    Map.entry(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, List.of("X-Attacker")),
                    Map.entry(HttpHeaders.ACCESS_CONTROL_MAX_AGE, List.of("86400")),
                    Map.entry(HttpHeaders.CACHE_CONTROL, List.of("public, max-age=86400")),
                    Map.entry(HttpHeaders.PRAGMA, List.of("public")),
                    Map.entry(HttpHeaders.VARY, List.of("Accept-Encoding, Origin", "Origin")),
                    Map.entry(HttpHeaders.RETRY_AFTER, List.of("37")),
                    Map.entry("Access-Control-Allow-Origin-Extra", List.of("near-cors")),
                    Map.entry("Cache-Control-Extra", List.of("near-cache")),
                    Map.entry("X-Request-Id-Extra", List.of("near-request-id"))),
                Duration.ZERO, false, null, null));

        EntityExchangeResult<byte[]> result = loginRequest().exchange().expectStatus().isOk().expectBody()
            .json("{\"authenticated\":true}").returnResult();

        HttpHeaders headers = result.getResponseHeaders();
        List<String> requestIds = headers.getOrEmpty("X-Request-Id");
        Assertions.assertAll(() -> Assertions.assertEquals(1, requestIds.size()),
            () -> Assertions.assertDoesNotThrow(() -> UUID.fromString(requestIds.getFirst())),
            () -> Assertions.assertNotEquals(forgedRequestId, requestIds.getFirst()),
            () -> Assertions.assertEquals(List.of(ORIGIN), headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)),
            () -> Assertions.assertEquals(List.of("true"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)),
            () -> Assertions.assertEquals(List.of("POST, OPTIONS"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)),
            () -> Assertions.assertEquals(List.of("content-type"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).stream()
                    .map(value -> value.toLowerCase(java.util.Locale.ROOT)).toList()),
            () -> Assertions.assertEquals(List.of("X-Request-Id, Retry-After"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)),
            () -> Assertions.assertTrue(headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_MAX_AGE).isEmpty()),
            () -> Assertions.assertEquals(List.of("no-store"), headers.getOrEmpty(HttpHeaders.CACHE_CONTROL)),
            () -> Assertions.assertEquals(List.of("no-cache"), headers.getOrEmpty(HttpHeaders.PRAGMA)),
            () -> Assertions.assertEquals(List.of(HttpHeaders.ORIGIN), headers.getOrEmpty(HttpHeaders.VARY)),
            () -> Assertions.assertTrue(headers.getOrEmpty(HttpHeaders.SET_COOKIE).isEmpty()),
            () -> Assertions.assertEquals(List.of("37"), headers.getOrEmpty(HttpHeaders.RETRY_AFTER)),
            () -> Assertions.assertEquals(List.of("near-cors"),
                headers.getOrEmpty("Access-Control-Allow-Origin-Extra")),
            () -> Assertions.assertEquals(List.of("near-cache"), headers.getOrEmpty("Cache-Control-Extra")),
            () -> Assertions.assertEquals(List.of("near-request-id"), headers.getOrEmpty("X-Request-Id-Extra")));
        Assertions.assertEquals(1, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_refreshHappyPathForwardsJsonAndReturnsBothTokensWithoutCookie() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/refresh",
            BffScenarioStore.Response.json(200, TOKEN_RESPONSE));

        EntityExchangeResult<byte[]> result = gatewayClient().post().uri(REFRESH_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.COOKIE, "unrelated=value; " + REFRESH_COOKIE)
            .contentType(MediaType.APPLICATION_JSON).bodyValue(REFRESH_BODY).exchange().expectStatus().isOk()
            .expectHeader().doesNotExist(HttpHeaders.SET_COOKIE).expectBody().json(TOKEN_RESPONSE).returnResult();

        BffRequestJournal.Entry forwarded = onlyBffEntry();
        Assertions.assertAll(() -> Assertions.assertEquals("/auth/v1/refresh", forwarded.path()),
            () -> Assertions.assertEquals(forwarded.bodyDigestOf(REFRESH_BODY), forwarded.bodyDigest()),
            () -> Assertions.assertTrue(headerValues(forwarded, HttpHeaders.COOKIE).isEmpty()),
            () -> Assertions.assertEquals(List.of(ORIGIN), headerValues(forwarded, HttpHeaders.ORIGIN)),
            () -> Assertions.assertEquals(List.of(MediaType.APPLICATION_JSON_VALUE),
                headerValues(forwarded, HttpHeaders.CONTENT_TYPE)),
            () -> Assertions.assertTrue(headerValues(forwarded, "X-CSRF-Token").isEmpty()),
            () -> Assertions.assertTrue(headerValues(forwarded, "X-Gateway-Actor-Subject").isEmpty()),
            () -> Assertions.assertEquals(List.of(responseRequestId(result)), headerValues(forwarded, "X-Request-Id")));
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_logoutHappyPathUsesInternalHttpJwksAndForwardsOnlyTrustedIdentity() {
        JwtFixtureBuilder jwt = JwtFixtureBuilder.template();
        jwksSimulator().scenarios().expectJwks(jwt.jwksJson());
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout", BffScenarioStore.Response.json(204, ""));

        EntityExchangeResult<byte[]> result =
            logoutRequest(jwt.build()).exchange().expectStatus().isNoContent().expectBody().returnResult();

        BffRequestJournal.Entry forwarded = onlyBffEntry();
        Assertions.assertAll(() -> Assertions.assertEquals("/auth/v1/logout", forwarded.path()),
            () -> Assertions.assertEquals(forwarded.bodyDigestOf(REFRESH_BODY), forwarded.bodyDigest()),
            () -> Assertions.assertEquals(List.of(responseRequestId(result)), headerValues(forwarded, "X-Request-Id")),
            () -> Assertions.assertEquals(List.of(JwtFixtureBuilder.SUBJECT),
                headerValues(forwarded, "X-Gateway-Actor-Subject")),
            () -> Assertions.assertEquals(List.of(JwtFixtureBuilder.SESSION_ID),
                headerValues(forwarded, "X-Gateway-Session-Id")),
            () -> Assertions.assertEquals(List.of("PLATFORM_USER"), headerValues(forwarded, "X-Gateway-Actor-Type")),
            () -> Assertions.assertEquals(List.of("OPERATIONS"), headerValues(forwarded, "X-Gateway-Channel")),
            () -> Assertions.assertTrue(headerValues(forwarded, HttpHeaders.COOKIE).isEmpty()),
            () -> Assertions.assertEquals(List.of(ORIGIN), headerValues(forwarded, HttpHeaders.ORIGIN)),
            () -> Assertions.assertEquals(List.of(MediaType.APPLICATION_JSON_VALUE),
                headerValues(forwarded, HttpHeaders.CONTENT_TYPE)),
            () -> Assertions.assertTrue(headerValues(forwarded, "X-CSRF-Token").isEmpty()),
            () -> Assertions.assertTrue(headerValues(forwarded, HttpHeaders.AUTHORIZATION).isEmpty()));
        jwksSimulator().journal().verifySingleGet();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("localFailureScenarios")
    void test_representativeLocalFailuresNeverReachBffOrJwks(String scenario, int status, String code) {
        assertGatewayError(localFailureRequest(scenario), status, code);
        Assertions.assertEquals(0, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_rateLimitReturns429AfterFiveForwardedRequestsWithoutRetry() {
        for (int index = 0; index < 5; index++) {
            simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
                BffScenarioStore.Response.json(200, "{}"));
            loginRequest().exchange().expectStatus().isOk().expectBody().returnResult();
        }

        assertProblem(loginRequest().exchange().expectHeader().value(HttpHeaders.RETRY_AFTER, value -> {
            long retryAfter = Long.parseLong(value);
            Assertions.assertTrue(retryAfter >= 1 && retryAfter <= 12);
        }), 429, "GATEWAY_RATE_LIMITED");

        Assertions.assertEquals(5, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_concurrencyLimitReturns429WhileTwoRealBffRequestsAreHeld() {
        RequestBarrier firstBarrier = heldLoginScenario();
        CompletableFuture<EntityExchangeResult<byte[]>> first = startLogin();
        firstBarrier.awaitRequest(Duration.ofSeconds(3));
        RequestBarrier secondBarrier = heldLoginScenario();
        CompletableFuture<EntityExchangeResult<byte[]>> second = startLogin();
        secondBarrier.awaitRequest(Duration.ofSeconds(3));

        try {
            assertProblem(loginRequest().exchange().expectHeader()
                .value(HttpHeaders.RETRY_AFTER, value -> Assertions.assertTrue(Long.parseLong(value) >= 5)), 429,
                "GATEWAY_CONCURRENCY_LIMITED");
            Assertions.assertEquals(2, simulator().journal().count());
        } finally {
            firstBarrier.release();
            secondBarrier.release();
        }

        Assertions.assertEquals(HttpStatus.OK, await(first).getStatus());
        Assertions.assertEquals(HttpStatus.OK, await(second).getStatus());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_bffPrematureCloseReturns502WithoutRetry() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.immediateDisconnect());

        assertProblem(loginRequest().exchange(), 502, "UPSTREAM_PROTOCOL_ERROR");

        Assertions.assertEquals(1, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_bffTimeoutReturns504WithoutRetry() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.delayed(200, "{}", Duration.ofSeconds(2)));

        assertProblem(loginRequest().exchange(), 504, "GATEWAY_UPSTREAM_TIMEOUT");

        Assertions.assertEquals(1, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    private void assertGatewayError(WebTestClient.RequestHeadersSpec<?> request, int status, String code) {
        assertProblem(request.exchange(), status, code);
    }

    private WebTestClient.RequestHeadersSpec<?> localFailureRequest(String scenario) {
        return switch (scenario) {
            case "missing-token" -> gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
                .header(HttpHeaders.COOKIE, REFRESH_COOKIE).bodyValue("{}");
            case "forbidden-origin" ->
                gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, "https://evil.test").bodyValue("{}");
            case "unknown-route" -> gatewayClient().post().uri("/api/operations/auth/v1/unknown")
                .header(HttpHeaders.ORIGIN, ORIGIN).bodyValue("{}");
            case "oversized-body" -> gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
                .contentType(MediaType.TEXT_PLAIN).bodyValue("x".repeat(16_385));
            default -> throw new IllegalArgumentException("未知本地失败场景: " + scenario);
        };
    }

    private RequestBarrier heldLoginScenario() {
        RequestBarrier barrier = new RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(200, "{}").waitingFor(barrier));
        return barrier;
    }

    private CompletableFuture<EntityExchangeResult<byte[]>> startLogin() {
        return CompletableFuture.supplyAsync(() -> loginRequest().exchange().expectBody().returnResult());
    }

    private EntityExchangeResult<byte[]> await(CompletableFuture<EntityExchangeResult<byte[]>> response) {
        try {
            return response.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("等待 Gateway 端到端响应失败", exception);
        }
    }

    private WebTestClient.RequestHeadersSpec<?> loginRequest() {
        return gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
    }

    private WebTestClient.RequestHeadersSpec<?> logoutRequest(String token) {
        return gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).contentType(MediaType.APPLICATION_JSON)
            .bodyValue(REFRESH_BODY);
    }

    private WebTestClient gatewayClient() {
        return GatewayPublicTestClient.operations(port)
            .defaultHeader("X-Real-IP", "203.0.113.17").responseTimeout(Duration.ofSeconds(5)).build();
    }

    private String responseRequestId(EntityExchangeResult<byte[]> result) {
        String requestId = result.getResponseHeaders().getFirst("X-Request-Id");
        Assertions.assertNotNull(requestId);
        Assertions.assertDoesNotThrow(() -> UUID.fromString(requestId));
        return requestId;
    }

    private BffRequestJournal.Entry onlyBffEntry() {
        Assertions.assertEquals(1, simulator().journal().count());
        return simulator().journal().entries().getFirst();
    }

    private List<String> headerValues(BffRequestJournal.Entry entry, String name) {
        return entry.headers().entrySet().stream().filter(header -> header.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue).findFirst().orElse(List.of());
    }

    private OperationsBffSimulator simulator() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }

    private com.paytech.gateway.test.simulator.jwks.JwksSimulator jwksSimulator() {
        return GatewayAutoTestRuntime.jwksSimulator();
    }
}
