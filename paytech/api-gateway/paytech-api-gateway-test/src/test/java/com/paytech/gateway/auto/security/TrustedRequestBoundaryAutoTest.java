package com.paytech.gateway.auto.security;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration.GatewayBoundaryProbe;
import com.paytech.gateway.test.simulator.bff.BffRequestJournal;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;

/** Operations 上游 Cookie 与可信 Header 清理自动化测试。 */
@Import(GatewayBoundaryTestConfiguration.class)
class TrustedRequestBoundaryAutoTest extends GatewayAutoTestBase {
    private static final String ORIGIN = "https://operations-console.test";
    private static final String REFRESH_COOKIE = "__Secure-operations-refresh";
    private static final String CLIENT_IP = "X-Real-IP";
    private static final String DEFAULT_CLIENT_IP = "203.0.113.17";
    private static final List<String> RESERVED_INGRESS_HEADERS = List.of("x-ReQuEsT-iD", "x-GaTeWaY-rOuTe-CoDe",
        "x-GaTeWaY-aCtOr-SubJeCt", "X-gAtEwAy-SeSsIoN-iD", "x-GaTeWaY-aCtOr-TyPe", "X-gAtEwAy-ChAnNeL",
        "X-gAtEwAy-ReAlM", "X-gAtEwAy-CoNtExT-TyPe", "X-gAtEwAy-TeNaNt-Id", "X-gAtEwAy-TeNaNt-TyPe",
        "X-gAtEwAy-TeNaNt-MeMbEr-Id", "fOrWaRdEd", "x-FoRwArDeD-fOr", "X-fOrWaRdEd-HoSt", "x-FoRwArDeD-pOrT",
        "X-fOrWaRdEd-PrEfIx", "x-FoRwArDeD-pRoTo", "X-fOrWaRdEd-SeRvEr", "x-FoRwArDeD-sSl",
        "X-Gateway-Actor-Subject-Extra", "x-gAtEwAy-ClIeNt-Id", "X-GATEWAY-CREDENTIAL-ID", "x-GaTeWaY-unknown");
    private static final List<String> NEAR_MATCH_HEADERS =
        List.of("X-Forwarded-Evil", "X-Real-IP-Extra");

    @LocalServerPort
    private int port;

    @Autowired
    private GatewayBoundaryProbe boundaryProbe;

    private static Stream<List<String>> ignoredRefreshCookies() {
        return Stream.of(List.of(), List.of(REFRESH_COOKIE + "=one; " + REFRESH_COOKIE + "=two"),
            List.of(REFRESH_COOKIE + "=one", REFRESH_COOKIE + "=two"), List.of(REFRESH_COOKIE + "=opaque value"),
            List.of(REFRESH_COOKIE + "=\"\""));
    }

    private static Stream<String> validClientIps() {
        return Stream.of("203.0.113.77", "2001:db8::77");
    }

    private static Stream<Arguments> invalidClientIps() {
        return Stream.of(Arguments.of("missing", List.of()),
            Arguments.of("duplicate", List.of("203.0.113.77", "203.0.113.78")),
            Arguments.of("comma chain", List.of("203.0.113.77, 198.51.100.10")),
            Arguments.of("hostname", List.of("client.example")),
            Arguments.of("IPv4 with port", List.of("203.0.113.77:443")),
            Arguments.of("IPv6 with port", List.of("[2001:db8::77]:443")),
            Arguments.of("encoded bytes", List.of("203.0.113.%37%37")),
            Arguments.of("malformed", List.of("203.0.113.999")));
    }

    @BeforeEach
    void resetBoundaryProbe() {
        boundaryProbe.reset();
    }

    @Test
    void test_loginStripsEveryCallerCookieAndSensitiveForwardingHeaders() {
        expect("/auth/v1/login", BffScenarioStore.Response.json(200, "{}"));

        gatewayClient().post().uri(OperationsTestPaths.LOGIN).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.COOKIE, "unrelated=opaque; " + REFRESH_COOKIE + "=opaque-refresh")
            .header(HttpHeaders.AUTHORIZATION, "Bearer attacker-token").header("x-gateway-actor-subject", "attacker")
            .header("X-Gateway-Route-Code", "attacker-route").header("X-Gateway-Actor-Subject-Extra", "near-match")
            .header("Forwarded", "for=attacker").header("X-Forwarded-For", "198.51.100.1")
            .header("X-Forwarded-Evil", "near-match").header(HttpHeaders.HOST, "attacker.example")
            .header("X-Request-Id", "attacker-request-id").header("traceparent", "00-attacker")
            .header("tracestate", "attacker=value").header("baggage", "attacker=true")
            .header("X-CSRF-Token", "csrf-value").bodyValue("{}").exchange().expectStatus().isOk();

        BffRequestJournal.Entry entry = onlyEntry();
        Assertions.assertFalse(entry.headers().containsKey(HttpHeaders.COOKIE));
        Assertions.assertFalse(entry.headers().containsKey(HttpHeaders.AUTHORIZATION));
        Assertions.assertFalse(
            entry.headers().keySet().stream().anyMatch(name -> name.equalsIgnoreCase("X-Gateway-Actor-Subject")));
        Assertions.assertEquals(List.of("operations-auth-login"), headerValues(entry, "X-Gateway-Route-Code"));
        Assertions.assertFalse(
            entry.headers().keySet().stream().anyMatch(name -> name.equalsIgnoreCase("X-Gateway-Actor-Subject-Extra")));
        Assertions.assertFalse(entry.headers().containsKey("Forwarded"));
        Assertions.assertFalse(entry.headers().containsKey("X-Forwarded-For"));
        Assertions.assertFalse(entry.headers().containsKey(CLIENT_IP));
        Assertions
            .assertFalse(entry.headers().keySet().stream().anyMatch(name -> name.equalsIgnoreCase("X-Forwarded-Evil")));
        Assertions.assertFalse(entry.headers().containsKey("traceparent"));
        Assertions.assertFalse(entry.headers().containsKey("tracestate"));
        Assertions.assertFalse(entry.headers().containsKey("baggage"));
        Assertions.assertFalse(headerValues(entry, HttpHeaders.HOST).contains("attacker.example"));
        Assertions.assertFalse(headerValues(entry, "X-Request-Id").contains("attacker-request-id"));
        Assertions.assertEquals(List.of(ORIGIN), headerValues(entry, HttpHeaders.ORIGIN));
        Assertions.assertTrue(headerValues(entry, "X-CSRF-Token").isEmpty());
        Assertions.assertDoesNotThrow(() -> UUID.fromString(headerValues(entry, "X-Request-Id").getFirst()));
        boundaryProbe.awaitTerminal(java.time.Duration.ofSeconds(1));
        GatewayBoundaryTestConfiguration.Entry boundaryEntry = boundaryProbe.entries().getFirst();
        Assertions.assertFalse(hasHeader(boundaryEntry, "X-Gateway-Actor-Subject"));
        Assertions.assertFalse(hasHeader(boundaryEntry, "X-Gateway-Actor-Subject-Extra"));
        Assertions.assertTrue(hasHeader(boundaryEntry, "X-Forwarded-Evil"));
    }

    @Test
    void test_everyReservedIngressHeaderIsRemovedBeforeBoundaryWhileNearMatchesRemainObservable() {
        expect("/auth/v1/login", BffScenarioStore.Response.json(200, "{}"));

        gatewayClient().post().uri(OperationsTestPaths.LOGIN).header(HttpHeaders.ORIGIN, ORIGIN)
            .headers(headers -> RESERVED_INGRESS_HEADERS.forEach(name -> headers.add(name, "attacker-value")))
            .headers(headers -> NEAR_MATCH_HEADERS.forEach(name -> headers.add(name, "near-match"))).bodyValue("{}")
            .exchange().expectStatus().isOk();

        boundaryProbe.awaitTerminal(java.time.Duration.ofSeconds(1));
        GatewayBoundaryTestConfiguration.Entry boundaryEntry = boundaryProbe.entries().getFirst();
        Assertions.assertAll(RESERVED_INGRESS_HEADERS.stream()
            .map(name -> () -> Assertions.assertFalse(hasHeader(boundaryEntry, name), name)));
        Assertions.assertFalse(hasHeader(boundaryEntry, CLIENT_IP));
        Assertions.assertFalse(hasHeader(boundaryEntry, "X-Entry-Channel"));
        Assertions.assertAll(
            NEAR_MATCH_HEADERS.stream().map(name -> () -> Assertions.assertTrue(hasHeader(boundaryEntry, name), name)));

        BffRequestJournal.Entry bffEntry = onlyEntry();
        Assertions.assertTrue(headerValues(bffEntry, "X-Entry-Channel").isEmpty());
        Assertions.assertTrue(bffEntry.headers().keySet().stream()
            .filter(name -> name.toLowerCase(java.util.Locale.ROOT).startsWith("x-gateway-"))
            .allMatch(name -> name.equalsIgnoreCase("X-Gateway-Route-Code")));
        Assertions.assertAll(NEAR_MATCH_HEADERS.stream()
            .map(name -> () -> Assertions.assertTrue(headerValues(bffEntry, name).isEmpty(), name)));
    }

    @Test
    void test_refreshForwardsJsonAndDropsCookiesAndObsoleteCsrfHeader() {
        String body = "{\"refreshToken\":\"opaque-refresh\"}";
        String response = "{\"accessToken\":\"access\",\"tokenType\":\"Bearer\",\"expiresIn\":900,"
            + "\"refreshToken\":\"rotated-refresh\",\"refreshExpiresIn\":28800}";
        expect("/auth/v1/refresh", BffScenarioStore.Response.json(200, response));

        gatewayClient().post().uri(OperationsTestPaths.REFRESH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.CONTENT_TYPE, "application/json").header("X-CSRF-Token", "obsolete-value")
            .header(HttpHeaders.COOKIE, "unrelated=opaque; " + REFRESH_COOKIE + "=obsolete").bodyValue(body).exchange()
            .expectStatus().isOk().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE).expectBody().json(response);

        BffRequestJournal.Entry entry = onlyEntry();
        Assertions.assertAll(() -> Assertions.assertEquals(entry.bodyDigestOf(body), entry.bodyDigest()),
            () -> Assertions.assertTrue(headerValues(entry, HttpHeaders.COOKIE).isEmpty()),
            () -> Assertions.assertEquals(List.of(ORIGIN), headerValues(entry, HttpHeaders.ORIGIN)),
            () -> Assertions.assertEquals(List.of("application/json"), headerValues(entry, HttpHeaders.CONTENT_TYPE)),
            () -> Assertions.assertTrue(headerValues(entry, "X-CSRF-Token").isEmpty()),
            () -> Assertions.assertTrue(headerValues(entry, HttpHeaders.AUTHORIZATION).isEmpty()));
    }

    @Test
    void test_invalidUpstreamRefreshCookieIsRejectedBeforeItsStatusHeadersOrBodyCommit() {
        String invalidCookie = REFRESH_COOKIE + "=rotated; Path=/api/operations/auth/v1; Max-Age=3600; "
            + "Domain=attacker.test; Secure; HttpOnly; SameSite=Strict";
        expect("/auth/v1/refresh", BffScenarioStore.Response.json(201, "UPSTREAM_BODY_CANARY")
            .withHeader(HttpHeaders.SET_COOKIE, invalidCookie));

        var result = gatewayClient().post().uri(OperationsTestPaths.REFRESH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.COOKIE, REFRESH_COOKIE + "=opaque-refresh").bodyValue("{}").exchange().expectStatus()
            .isEqualTo(502).expectHeader().doesNotExist(HttpHeaders.SET_COOKIE).expectBody().returnResult();
        assertProblem(result, 502, "UPSTREAM_PROTOCOL_ERROR");
        byte[] body = result.getResponseBody();

        Assertions.assertAll(() -> Assertions.assertNotNull(body),
            () -> Assertions.assertFalse(
                new String(body, java.nio.charset.StandardCharsets.UTF_8).contains("UPSTREAM_BODY_CANARY")),
            () -> Assertions.assertEquals(1, simulator().journal().count()));
    }

    @Test
    void test_logoutWithoutAccessTokenDoesNotForwardRefreshCookieToBff() {
        String refresh = REFRESH_COOKIE + "=opaque-refresh";

        assertProblem(gatewayClient().post().uri(OperationsTestPaths.LOGOUT).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.COOKIE, refresh + "; unrelated=opaque").bodyValue("{}").exchange().expectStatus()
            .isUnauthorized(), 401, "GATEWAY_UNAUTHORIZED");

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @ParameterizedTest(name = "trusted numeric client IP {0}")
    @MethodSource("validClientIps")
    void test_numericClientIpReachesRouteAndAllProxyHeadersAreRemoved(String clientIp) {
        expect("/auth/v1/login", BffScenarioStore.Response.json(200, "{}"));

        gatewayClient(clientIp).post().uri(OperationsTestPaths.LOGIN).header(HttpHeaders.ORIGIN, ORIGIN)
            .header("Forwarded", "for=198.51.100.10").header("X-Forwarded-For", "198.51.100.10").bodyValue("{}")
            .exchange().expectStatus().isOk();

        BffRequestJournal.Entry entry = onlyEntry();
        Assertions.assertAll(() -> Assertions.assertFalse(entry.headers().containsKey("Forwarded")),
            () -> Assertions.assertFalse(entry.headers().containsKey("X-Forwarded-For")),
            () -> Assertions.assertFalse(entry.headers().containsKey(CLIENT_IP)));
    }

    @ParameterizedTest(name = "invalid trusted client IP {0}")
    @MethodSource("invalidClientIps")
    void test_missingAmbiguousOrInvalidClientIpReturnsSafe403WithoutBffCall(String scenario, List<String> values) {
        WebTestClient.RequestBodySpec request =
            gatewayClientWithoutClientIp().post().uri(OperationsTestPaths.LOGIN).header(HttpHeaders.ORIGIN, ORIGIN);
        values.forEach(value -> request.header(CLIENT_IP, value));

        var result = assertProblem(request.bodyValue("{}").exchange(), 403, "GATEWAY_FORBIDDEN");
        String body = new String(result.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8);
        Assertions.assertTrue(body.contains("\"detail\":\"请求不符合渠道安全策略\""));

        Assertions.assertEquals(0, simulator().journal().count(), scenario);
    }

    @Test
    void test_logoutWithoutAccessTokenIsUnauthorizedWithoutBffCall() {
        assertProblem(gatewayClient().post().uri(OperationsTestPaths.LOGOUT).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.COOKIE, REFRESH_COOKIE + "=opaque-refresh").bodyValue("{}").exchange().expectStatus()
            .isUnauthorized(), 401, "GATEWAY_UNAUTHORIZED");

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @ParameterizedTest
    @MethodSource("ignoredRefreshCookies")
    void test_refreshDoesNotReadMissingDuplicateOrMalformedCookie(List<String> cookies) {
        expect("/auth/v1/refresh", BffScenarioStore.Response.json(200, "{}"));
        var request = gatewayClient().post().uri(OperationsTestPaths.REFRESH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.CONTENT_TYPE, "application/json");
        cookies.forEach(value -> request.header(HttpHeaders.COOKIE, value));
        String body = "{\"refreshToken\":\"json-refresh\"}";

        request.bodyValue(body).exchange().expectStatus().isOk().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE);

        BffRequestJournal.Entry entry = onlyEntry();
        Assertions.assertTrue(headerValues(entry, HttpHeaders.COOKIE).isEmpty());
        Assertions.assertEquals(entry.bodyDigestOf(body), entry.bodyDigest());
    }

    private void expect(String path, BffScenarioStore.Response response) {
        simulator().scenarios().expect(HttpMethod.POST, path, response);
    }

    @ParameterizedTest
    @MethodSource("invalidEntryChannels")
    void invalidEntryChannelFailsBeforeSnapshotGuardAndDownstream(List<String> channels) {
        int snapshotRequests = GatewayAutoTestRuntime.platformSnapshotSimulator().requestCount();
        assertProblem(GatewayPublicTestClient.builder(port, channels).defaultHeader(CLIENT_IP, DEFAULT_CLIENT_IP).build()
            .post().uri(OperationsTestPaths.LOGIN).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "a".repeat(8_193)).bodyValue("{}").exchange().expectStatus()
            .isForbidden(), 403, "GATEWAY_FORBIDDEN");
        Assertions.assertEquals(snapshotRequests, GatewayAutoTestRuntime.platformSnapshotSimulator().requestCount());
        Assertions.assertTrue(redisTestCleaner.guardKeys().isEmpty());
        Assertions.assertEquals(0, simulator().journal().count());
        Assertions.assertTrue(boundaryProbe.entries().isEmpty());
    }

    private static Stream<List<String>> invalidEntryChannels() {
        return Stream.of(List.of(), List.of("OPERATIONS", "OPERATIONS"), List.of("OPERATIONS", "CLIENT_API"),
            List.of("UNKNOWN"), List.of("operations"), List.of("OPERATIONS,CLIENT_API"));
    }

    private BffRequestJournal.Entry onlyEntry() {
        List<BffRequestJournal.Entry> entries = simulator().journal().entries();
        Assertions.assertEquals(1, entries.size());
        return entries.getFirst();
    }

    private List<String> headerValues(BffRequestJournal.Entry entry, String expectedName) {
        return entry.headers().entrySet().stream().filter(header -> header.getKey().equalsIgnoreCase(expectedName))
            .map(Map.Entry::getValue).findFirst().orElse(List.of());
    }

    private boolean hasHeader(GatewayBoundaryTestConfiguration.Entry entry, String expectedName) {
        return entry.headers().keySet().stream().anyMatch(name -> name.equalsIgnoreCase(expectedName));
    }

    private OperationsBffSimulator simulator() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }

    private WebTestClient gatewayClient() {
        return gatewayClient(DEFAULT_CLIENT_IP);
    }

    private WebTestClient gatewayClient(String clientIp) {
        return GatewayPublicTestClient.operations(port).defaultHeader(CLIENT_IP, clientIp)
            .build();
    }

    private WebTestClient gatewayClientWithoutClientIp() {
        return GatewayPublicTestClient.operations(port).build();
    }
}
