package com.paytech.gateway.auto.routing;

import static com.paytech.gateway.test.GatewayProblemChecker.assertHeadProblem;
import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.test.web.reactive.server.EntityExchangeResult;

import com.paytech.gateway.guard.policy.ConcurrencyRule;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.policy.RateLimitRule;
import com.paytech.gateway.routing.*;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.simulator.bff.BffRequestJournal;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;

import reactor.netty.http.client.HttpClient;
import reactor.core.publisher.Mono;

/** Operations 静态 BFF 路由自动化契约测试。 */
@ExtendWith(OutputCaptureExtension.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsRoutingAutoTest extends GatewayAutoTestBase {
    private static final String REQUEST_BODY = "{\"request\":\"value\"}";

    @LocalServerPort
    private int port;

    @Autowired
    private OperationsRouteCatalog routeCatalog;

    private static Stream<Arguments> routes() {
        return Stream.of(Arguments.of(OperationsTestPaths.LOGIN, "/auth/v1/login"),
            Arguments.of(OperationsTestPaths.REFRESH, "/auth/v1/refresh"));
    }

    private static Stream<Arguments> negativeRequests() {
        String exactPath = OperationsTestPaths.LOGIN;
        return Stream.of(Arguments.of(HttpMethod.GET, exactPath), Arguments.of(HttpMethod.PUT, exactPath),
            Arguments.of(HttpMethod.PATCH, exactPath), Arguments.of(HttpMethod.DELETE, exactPath),
            Arguments.of(HttpMethod.HEAD, exactPath), Arguments.of(HttpMethod.POST, exactPath + "/"),
            Arguments.of(HttpMethod.POST, "/api/operations/auth/v1/LOGIN"),
            Arguments.of(HttpMethod.POST, exactPath + "/other"),
            Arguments.of(HttpMethod.POST, "/api/operations/v1/anything"));
    }

    private static Stream<String> queryBearingRoutes() {
        return Stream.of("/api/operations/auth/v1/login?next=%2Foperations", "/api/operations/auth/v1/refresh?retry=1",
            "/api/operations/auth/v1/logout?trace=1");
    }

    @ParameterizedTest
    @MethodSource("routes")
    void test_anonymousPostRouteStripsOperationsPrefix(String externalPath, String bffPath) {
        OperationsBffSimulator simulator = simulator();
        simulator.scenarios().expect(HttpMethod.POST, bffPath,
            BffScenarioStore.Response.json(201, "{\"accepted\":true}"));

        gatewayClient().post().uri(externalPath).header("Origin", "https://operations-console.test")
            .headers(headers -> addRefreshCookie(headers, externalPath)).bodyValue(REQUEST_BODY).exchange()
            .expectStatus().isCreated().expectHeader().contentType("application/json").expectBody()
            .json("{\"accepted\":true}");

        BffRequestJournal.Entry entry = onlyRecordedRequest(simulator);
        Assertions.assertEquals(HttpMethod.POST, entry.method());
        Assertions.assertEquals(bffPath, entry.path());
        Assertions.assertEquals(entry.bodyDigestOf(REQUEST_BODY), entry.bodyDigest());
    }

    @ParameterizedTest
    @MethodSource("negativeRequests")
    void test_unregisteredRequestsReturnProblem404WithoutBffCall(HttpMethod method, String path) {
        WebTestClient.ResponseSpec response = gatewayClient().method(method).uri(path).exchange();
        if (method != HttpMethod.HEAD) {
            assertProblem(response, 404, "GATEWAY_ROUTE_NOT_FOUND");
        } else {
            assertHeadProblem(response, 404);
        }

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_encodedSeparatorIsRejectedWithoutBffCall() {
        RawGatewayResponse response = GatewayPublicTestClient.operationsHttpClient(port)
            .headers(headers -> headers.set("X-Real-IP", "203.0.113.17")).post()
            .uri("/api/operations/auth%2Fv1%2Flogin")
            .responseSingle((httpResponse, content) -> content.asString().defaultIfEmpty("")
                .map(body -> new RawGatewayResponse(httpResponse.status().code(),
                    httpResponse.responseHeaders().get("Content-Type"),
                    httpResponse.responseHeaders().get("X-Request-Id"),
                    httpResponse.responseHeaders().get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN), body)))
            .block();

        Assertions.assertNotNull(response);
        Assertions.assertEquals(404, response.status());
        Assertions.assertEquals("application/problem+json", response.contentType());
        Assertions.assertDoesNotThrow(() -> UUID.fromString(response.requestId()));
        Assertions.assertTrue(response.body().contains("\"code\":\"GATEWAY_ROUTE_NOT_FOUND\""));
        Assertions.assertTrue(response.body().contains("\"requestId\":\"" + response.requestId() + "\""));
        Assertions.assertNull(response.allowOrigin());
        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_bffBusinessErrorPassesThroughUnchanged() {
        OperationsBffSimulator simulator = simulator();
        simulator.scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            legacyResponse(422, "INVALID_CREDENTIALS", "credentials rejected"));

        gatewayClient().post().uri(OperationsTestPaths.LOGIN).header("Origin", "https://operations-console.test")
            .bodyValue(REQUEST_BODY).exchange().expectStatus().isEqualTo(422).expectBody()
            .json("{\"code\":\"INVALID_CREDENTIALS\"}");

        Assertions.assertEquals(1, simulator.journal().count());
    }

    @Test
    void test_bffServerErrorIsNotRetried() {
        OperationsBffSimulator simulator = simulator();
        String response = """
            {"type":"about:blank","title":"Internal Server Error","detail":"failed","code":"BFF_FAILED",
             "requestId":"{{requestId}}","timestamp":"2026-09-06T10:11:12.123456Z","error":"failed"}
            """;
        simulator.scenarios().expect(HttpMethod.POST, "/auth/v1/refresh",
            BffScenarioStore.Response.raw(500, response.getBytes(StandardCharsets.UTF_8), "application/problem+json")
                .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId());

        gatewayClient().post().uri(OperationsTestPaths.REFRESH).header("Origin", "https://operations-console.test")
            .header("Cookie", "__Secure-operations-refresh=opaque-refresh").bodyValue(REQUEST_BODY).exchange()
            .expectStatus().is5xxServerError().expectBody().json("{\"error\":\"failed\"}");

        Assertions.assertEquals(1, simulator.journal().count());
    }

    @Test
    void test_strictProblemPassesWithExactOwnerBytesAndUnknownNestedExtension() {
        String raw = """
             { "type":"about:blank", "title":"Conflict", "detail":"state conflict", "code":"STATE_CONFLICT",
               "requestId":"{{requestId}}", "timestamp":"2026-09-06T10:11:12.654321Z",
               "ownerExtension":{"nested":[1,true,{"canary":"kept-exactly"}]} }
            """;
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.raw(409, raw.getBytes(StandardCharsets.UTF_8),
                "Application/Problem+Json;charset=utf-8")
                .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId());

        EntityExchangeResult<byte[]> result = gatewayClient().post().uri(OperationsTestPaths.LOGIN)
            .header("Origin", "https://operations-console.test").bodyValue(REQUEST_BODY).exchange()
            .expectStatus().isEqualTo(409).expectBody().returnResult();

        String requestId = result.getResponseHeaders().getFirst("X-Request-Id");
        Assertions.assertNotNull(requestId);
        Assertions.assertArrayEquals(raw.replace(BffScenarioStore.REQUEST_ID_TOKEN, requestId)
            .getBytes(StandardCharsets.UTF_8), result.getResponseBody());
        Assertions.assertEquals(1, simulator().journal().count());
    }

    @Test
    void test_invalidOwnerProblemBecomesSanitizedLocal502WithoutRetry() {
        String ownerCanary = "OWNER_DETAIL_EXTENSION_CANARY";
        String invalid = "{\"type\":\"about:blank\",\"title\":\"Wrong Title\",\"detail\":\"" + ownerCanary
            + "\",\"code\":\"BAD\",\"requestId\":\"{{requestId}}\","
            + "\"timestamp\":\"2026-09-06T10:11:12.123456Z\",\"extension\":\"" + ownerCanary + "\"}";
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.raw(422, invalid.getBytes(StandardCharsets.UTF_8), "application/problem+json")
                .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN)
                .withHeader("X-Owner-Canary", ownerCanary).reflectingRequestId());

        EntityExchangeResult<byte[]> result = gatewayClient().post().uri(OperationsTestPaths.LOGIN)
            .header("Origin", "https://operations-console.test").bodyValue(REQUEST_BODY).exchange()
            .expectStatus().isEqualTo(502).expectHeader().doesNotExist("X-Owner-Canary").expectBody().returnResult();
        assertProblem(result, 502, "UPSTREAM_PROTOCOL_ERROR");

        String body = new String(result.getResponseBody(), StandardCharsets.UTF_8);
        String requestId = result.getResponseHeaders().getFirst("X-Request-Id");
        Assertions.assertAll(() -> Assertions.assertNotNull(requestId),
            () -> Assertions.assertTrue(body.contains("\"requestId\":\"" + requestId + "\"")),
            () -> Assertions.assertFalse(body.contains(ownerCanary)),
            () -> Assertions.assertEquals(1, simulator().journal().count()));
    }

    @Test
    void test_redirectBypassesOwnerErrorValidation() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.raw(302, "not-json".getBytes(StandardCharsets.UTF_8), "text/plain")
                .withHeader(HttpHeaders.LOCATION, "/next"));

        gatewayClient().post().uri(OperationsTestPaths.LOGIN).header("Origin", "https://operations-console.test")
            .bodyValue(REQUEST_BODY).exchange().expectStatus().isEqualTo(302).expectHeader()
            .valueEquals(HttpHeaders.LOCATION, "/next").expectBody(String.class).isEqualTo("not-json");

        Assertions.assertEquals(1, simulator().journal().count());
    }

    @Test
    void test_rawHeadKeepsValidOwnerMetadataAndInvalidHeadBecomesBodylessLocal502() {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(snapshotWithHeadRoute());
        Assertions.assertTrue(refreshSnapshot());
        String externalPath = "/api/operations/auth/v1/problem-head";
        String downstreamPath = "/auth/v1/problem-head";

        simulator().scenarios().expect(HttpMethod.HEAD, downstreamPath,
            BffScenarioStore.Response.raw(404, new byte[0], "application/problem+json")
                .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).withDeclaredContentLength(37)
                .reflectingRequestId());
        HeadRawResponse valid = head(externalPath);
        Assertions.assertAll(() -> Assertions.assertEquals(404, valid.status()),
            () -> Assertions.assertEquals("application/problem+json", valid.contentType()),
            () -> Assertions.assertDoesNotThrow(() -> UUID.fromString(valid.requestId())),
            () -> Assertions.assertEquals(37, valid.contentLength()),
            () -> Assertions.assertEquals(0, valid.bodyLength()));

        simulator().scenarios().expect(HttpMethod.HEAD, downstreamPath,
            BffScenarioStore.Response.raw(404, new byte[0], "application/json")
                .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId());
        HeadRawResponse invalid = head(externalPath);
        Assertions.assertAll(() -> Assertions.assertEquals(502, invalid.status()),
            () -> Assertions.assertEquals("application/problem+json", invalid.contentType()),
            () -> Assertions.assertDoesNotThrow(() -> UUID.fromString(invalid.requestId())),
            () -> Assertions.assertEquals(0, invalid.bodyLength()),
            () -> Assertions.assertEquals(2, simulator().journal().count()));
    }

    @Test
    void test_declaredTruncationBecomesLocal502WithoutRetryOrOwnerBytes() {
        String canary = "DECLARED_TRUNCATION_CANARY";
        byte[] truncated = ("{\"type\":\"about:blank\",\"detail\":\"" + canary + "\"}")
            .getBytes(StandardCharsets.UTF_8);
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.raw(500, truncated, "application/problem+json")
                .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN)
                .withDeclaredContentLength(truncated.length + 17L).reflectingRequestId());

        EntityExchangeResult<byte[]> result = gatewayClient().post().uri(OperationsTestPaths.LOGIN)
            .header("Origin", "https://operations-console.test").bodyValue(REQUEST_BODY).exchange()
            .expectStatus().isEqualTo(502).expectBody().returnResult();

        assertProblem(result, 502, "UPSTREAM_PROTOCOL_ERROR");
        Assertions.assertFalse(new String(result.getResponseBody(), StandardCharsets.UTF_8).contains(canary));
        Assertions.assertEquals(1, simulator().journal().count());
    }

    @Test
    void test_midBodyDisconnectBecomesLocal502WithoutRetryOrPartialBytes() throws Exception {
        String canary = "MID_BODY_OWNER_CANARY";
        BffScenarioStore.RequestBarrier disconnect = new BffScenarioStore.RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.chunkedThenDisconnect(500, canary, disconnect)
                .withContentTypes("application/problem+json")
                .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId());

        var response = GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set("X-Real-IP", "203.0.113.17");
            headers.set(HttpHeaders.ORIGIN, "https://operations-console.test");
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        }).post().uri(OperationsTestPaths.LOGIN).send(reactor.netty.ByteBufFlux.fromString(Mono.just(REQUEST_BODY)))
            .responseSingle((head, body) -> body.asString().defaultIfEmpty("")
                .map(value -> new RawGatewayResponse(head.status().code(),
                    head.responseHeaders().get(HttpHeaders.CONTENT_TYPE),
                    head.responseHeaders().get("X-Request-Id"), null, value)))
            .toFuture();
        disconnect.awaitRequest(Duration.ofSeconds(3));
        disconnect.release();
        RawGatewayResponse result = response.get(3, TimeUnit.SECONDS);

        Assertions.assertAll(() -> Assertions.assertEquals(502, result.status()),
            () -> Assertions.assertFalse(result.body().contains(canary)),
            () -> Assertions.assertEquals(1, simulator().journal().count()));
    }

    @ParameterizedTest
    @MethodSource("queryBearingRoutes")
    void test_queryBearingOperationsRequestsAreRejectedWithoutBffCall(String path) {
        assertProblem(gatewayClient().post().uri(path).header("Origin", "https://operations-console.test")
            .header("Cookie", "__Secure-operations-refresh=opaque-refresh").bodyValue(REQUEST_BODY).exchange(), 400,
            "GATEWAY_INVALID_REQUEST");

        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_bffPrematureCloseReturnsStable502WithoutRetryOrErrorNoise(CapturedOutput output) {
        OperationsBffSimulator simulator = simulator();
        simulator.scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.immediateDisconnect());

        assertProblem(gatewayClient().post().uri(OperationsTestPaths.LOGIN)
            .header("Origin", "https://operations-console.test").bodyValue(REQUEST_BODY).exchange(), 502,
            "UPSTREAM_PROTOCOL_ERROR");

        Assertions.assertEquals(1, simulator.journal().count());
        Assertions.assertFalse(output.getOut().contains("PrematureCloseException"));
    }

    @Test
    void test_catalogContainsExactlyThreeStableRoutes() {
        List<OperationsRouteDefinition> routes = routeCatalog.all();

        Assertions.assertEquals(3, routes.size());
        Assertions.assertIterableEquals(
            List.of("operations-auth-login", "operations-auth-refresh", "operations-auth-logout"),
            routes.stream().map(route -> route.code().getCode()).toList());
        Assertions.assertTrue(routes.stream().allMatch(route -> route.method() == HttpMethod.POST));
        Assertions
            .assertTrue(routes.stream().allMatch(route -> route.queryPolicy() == OperationsQueryPolicy.REJECT_ALL));
        Assertions.assertThrows(UnsupportedOperationException.class, () -> routes.add(routes.getFirst()));
    }

    @Test
    void test_catalogOwnsExactMethodPathAccessAndGuardContracts() {
        OperationsRouteDefinition login = route(OperationsRouteCode.AUTH_LOGIN);
        OperationsRouteDefinition refresh = route(OperationsRouteCode.AUTH_REFRESH);
        OperationsRouteDefinition logout = route(OperationsRouteCode.AUTH_LOGOUT);

        Assertions.assertAll(() -> assertLookup(login), () -> assertLookup(refresh), () -> assertLookup(logout),
            () -> Assertions.assertEquals(OperationsAccessRequirement.ANONYMOUS, login.accessRequirement()),
            () -> Assertions.assertEquals(OperationsAccessRequirement.ANONYMOUS, refresh.accessRequirement()),
            () -> Assertions.assertEquals(OperationsAccessRequirement.AUTHENTICATED, logout.accessRequirement()),
            () -> Assertions.assertEquals(RefreshCookieRequirement.NOT_REQUIRED, login.refreshCookieRequirement()),
            () -> Assertions.assertEquals(RefreshCookieRequirement.NOT_REQUIRED, refresh.refreshCookieRequirement()),
            () -> Assertions.assertEquals(RefreshCookieRequirement.NOT_REQUIRED, logout.refreshCookieRequirement()),
            () -> Assertions.assertNull(login.authenticatedGuard()),
            () -> Assertions.assertNull(refresh.authenticatedGuard()));
        assertProfile(login.ingressGuard(),
            List.of(new RateLimitRule(GuardDimension.SHARED_ROUTE, 300, 300, Duration.ofMinutes(1)),
                new RateLimitRule(GuardDimension.CLIENT_IP, 5, 5, Duration.ofMinutes(1))),
            List.of(new ConcurrencyRule(GuardDimension.SHARED_ROUTE, 20),
                new ConcurrencyRule(GuardDimension.CLIENT_IP, 2)));
        assertProfile(refresh.ingressGuard(),
            List.of(new RateLimitRule(GuardDimension.SHARED_ROUTE, 600, 600, Duration.ofMinutes(1)),
                new RateLimitRule(GuardDimension.CLIENT_IP, 10, 30, Duration.ofMinutes(1))),
            List.of(new ConcurrencyRule(GuardDimension.SHARED_ROUTE, 50),
                new ConcurrencyRule(GuardDimension.CLIENT_IP, 3)));
        assertProfile(logout.ingressGuard(),
            List.of(new RateLimitRule(GuardDimension.SHARED_ROUTE, 300, 300, Duration.ofMinutes(1)),
                new RateLimitRule(GuardDimension.CLIENT_IP, 10, 30, Duration.ofMinutes(1))),
            List.of(new ConcurrencyRule(GuardDimension.SHARED_ROUTE, 50),
                new ConcurrencyRule(GuardDimension.CLIENT_IP, 3)));
        assertProfile(logout.authenticatedGuard(),
            List.of(new RateLimitRule(GuardDimension.AUTHENTICATED_SUBJECT, 10, 10, Duration.ofMinutes(1))),
            List.of(new ConcurrencyRule(GuardDimension.AUTHENTICATED_SUBJECT, 2)));
    }

    private WebTestClient gatewayClient() {
        return GatewayPublicTestClient.operations(port)
            .defaultHeader("X-Real-IP", "203.0.113.17").build();
    }

    private void addRefreshCookie(org.springframework.http.HttpHeaders headers, String path) {
        if (path.endsWith("/refresh") || path.endsWith("/logout")) {
            headers.set("Cookie", "__Secure-operations-refresh=opaque-refresh");
        }
    }

    private OperationsBffSimulator simulator() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }

    private OperationsRouteDefinition route(OperationsRouteCode code) {
        return routeCatalog.all().stream().filter(route -> route.code() == code).findFirst().orElseThrow();
    }

    private void assertLookup(OperationsRouteDefinition route) {
        Assertions.assertEquals(route, routeCatalog.find(route.method(), route.externalPath()).orElseThrow());
        Assertions.assertTrue(routeCatalog.find(HttpMethod.GET, route.externalPath()).isEmpty());
        Assertions.assertTrue(routeCatalog.find(route.method(), route.externalPath() + "?unexpected=1").isEmpty());
    }

    private void assertProfile(GuardProfile profile, List<RateLimitRule> rateLimits,
        List<ConcurrencyRule> concurrencyLimits) {
        Assertions.assertAll(() -> Assertions.assertEquals(rateLimits, profile.rateLimits()),
            () -> Assertions.assertEquals(concurrencyLimits, profile.concurrencyLimits()),
            () -> Assertions.assertEquals(Duration.ofSeconds(30), profile.leaseDuration()),
            () -> Assertions.assertEquals(Duration.ofSeconds(60), profile.stateTtl()));
    }

    private BffRequestJournal.Entry onlyRecordedRequest(OperationsBffSimulator simulator) {
        List<BffRequestJournal.Entry> entries = simulator.journal().entries();
        Assertions.assertEquals(1, entries.size());
        return entries.getFirst();
    }

    private BffScenarioStore.Response legacyResponse(int status, String code, String message) {
        String body = "{\"code\":\"" + code + "\",\"message\":\"" + message + "\",\"requestId\":\""
            + BffScenarioStore.REQUEST_ID_TOKEN + "\",\"timestamp\":\"2026-09-06T10:11:12.123456Z\"}";
        return BffScenarioStore.Response.json(status, body)
            .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId();
    }

    private com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody snapshotWithHeadRoute() {
        var snapshot = com.paytech.gateway.test.builder.GatewaySnapshotFixture.operationsSnapshot();
        var source = snapshot.getRoutes().getFirst();
        List<com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot> routes =
            new ArrayList<>(snapshot.getRoutes());
        routes.add(new com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot(
            "operations-problem-head", "OPERATIONS", "HEAD", "/api/operations/auth/v1/problem-head",
            "/auth/v1/problem-head", source.getTargetServiceCode(), "PUBLIC", "PUBLIC", null, null, "DROP", "NONE",
            List.of(), List.of("origin"), List.of(), 0, source.getBodyReceiveTimeoutMillis(),
            source.getResponseTimeoutMillis(), source.getGuardRules()));
        return new com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody(
            snapshot.getSchemaVersion(), snapshot.getTargets(), List.copyOf(routes));
    }

    private HeadRawResponse head(String path) {
        return GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set("X-Real-IP", "203.0.113.17");
            headers.set(HttpHeaders.ORIGIN, "https://operations-console.test");
        }).head().uri(path).responseSingle((response, content) -> content.asByteArray().defaultIfEmpty(new byte[0])
            .map(body -> new HeadRawResponse(response.status().code(),
                response.responseHeaders().get(HttpHeaders.CONTENT_TYPE),
                response.responseHeaders().get("X-Request-Id"),
                java.util.Optional.ofNullable(response.responseHeaders().get(HttpHeaders.CONTENT_LENGTH))
                    .map(Long::parseLong).orElse(-1L), body.length)))
            .block(Duration.ofSeconds(3));
    }

    private record RawGatewayResponse(int status, String contentType, String requestId, String allowOrigin,
        String body) {
    }

    private record HeadRawResponse(int status, String contentType, String requestId, long contentLength,
        int bodyLength) {}
}
