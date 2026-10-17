package com.paytech.gateway.auto.observability;

import com.paytech.gateway.test.GatewayPublicTestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.observability.GatewayMetrics.GuardPhase;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.OperationsRouteCode;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore.RequestBarrier;
import com.paytech.gateway.test.time.MutableTestClock;

import io.micrometer.core.instrument.*;
import io.micrometer.core.instrument.Timer;

/** Gateway 自定义 Micrometer Meter 的真实请求信号与低基数标签测试。 */
class GatewayMetricsAutoTest extends GatewayAutoTestBase {
    private static final String REQUEST_TIMER = "gateway.requests";
    private static final String UPSTREAM_LATENCY_TIMER = "gateway.upstream.latency";
    private static final String GUARD_REJECTION_COUNTER = "gateway.guard.rejections";
    private static final String JWKS_REFRESH_COUNTER = "gateway.jwks.refreshes";
    private static final String ORIGIN = "https://operations-console.test";
    private static final String LOGIN_PATH = OperationsTestPaths.LOGIN;
    private static final String LOGOUT_PATH = OperationsTestPaths.LOGOUT;
    private static final String REFRESH_COOKIE = "__Secure-operations-refresh=opaque-refresh";
    private static final Set<String> ALLOWED_TAG_KEYS = Set.of("route", "channel", "profile", "outcome",
        "status_family", "limiter", "dimension", "phase", "result", "reason");
    private static final String RAW_PATH_CANARY = "meter-raw-path-canary";
    private static final MutableTestClock TEST_CLOCK = new MutableTestClock(JwtFixtureBuilder.NOW, ZoneOffset.UTC);

    @LocalServerPort
    private int port;

    @Autowired
    private MeterRegistry meterRegistry;

    @Autowired
    private GatewayMetrics gatewayMetrics;

    @Autowired
    private Environment environment;

    @TestBean(name = "operationsJwtClock", methodName = "mutableClock", enforceOverride = true)
    private Clock operationsJwtClock;

    private static Clock mutableClock() {
        return TEST_CLOCK;
    }

    @BeforeEach
    void resetClock() {
        TEST_CLOCK.reset(JwtFixtureBuilder.NOW);
    }

    @Test
    void test_requestTimerCoversAllStableTerminalStatusFamilies() {
        assertRequestMetric("operations-auth-login", "UPSTREAM_RESPONSE", "2xx", () -> {
            simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
                BffScenarioStore.Response.json(200, "{}"));
            loginRequest().exchange().expectStatus().isOk().expectBody().returnResult();
        });
        assertRequestMetric("operations-auth-logout", "AUTHENTICATION_FAILED", "4xx",
            () -> gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN).exchange().expectStatus()
                .isUnauthorized().expectBody().returnResult());
        assertRequestMetric("operations-auth-login", "AUTHORIZATION_DENIED", "4xx",
            () -> gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, "https://forbidden.test")
                .bodyValue("{}").exchange().expectStatus().isForbidden().expectBody().returnResult());
        assertRequestMetric("UNMATCHED", "ROUTE_NOT_FOUND", "4xx",
            () -> gatewayClient().get().uri("/api/operations/" + RAW_PATH_CANARY).exchange().expectStatus().isNotFound()
                .expectBody().returnResult());
        assertRequestMetric("operations-auth-login", "PAYLOAD_TOO_LARGE", "4xx",
            () -> gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
                .bodyValue("x".repeat(16_385)).exchange().expectStatus().isEqualTo(413).expectBody().returnResult());
        assertRequestMetric("operations-auth-login", "REQUEST_HEADER_FIELDS_TOO_LARGE", "4xx",
            () -> gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, "x".repeat(2_049)).bodyValue("{}")
                .exchange().expectStatus().isEqualTo(431).expectBody().returnResult());
        assertRequestMetric("operations-auth-login", "UPSTREAM_INVALID_RESPONSE", "5xx", () -> {
            simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
                BffScenarioStore.Response.immediateDisconnect());
            loginRequest().exchange().expectStatus().isEqualTo(502).expectBody().returnResult();
        });
        assertRequestMetric("operations-auth-login", "UPSTREAM_TIMEOUT", "5xx", () -> {
            simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
                BffScenarioStore.Response.delayed(200, "{}", Duration.ofSeconds(2)));
            loginRequest().exchange().expectStatus().isEqualTo(504).expectBody().returnResult();
        });
        Assertions.assertEquals(3, simulator().journal().count());
        assertUpstreamTimerRecorded("operations-auth-login", "OPERATIONS", "PUBLIC");
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_localAuthenticationAndOriginFailuresDoNotRecordUpstreamLatency() {
        long logoutBefore = upstreamTimerCount("operations-auth-logout", "OPERATIONS", "OPERATIONS_JWT");
        gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN).exchange().expectStatus()
            .isUnauthorized().expectBody().returnResult();
        Assertions.assertEquals(logoutBefore,
            upstreamTimerCount("operations-auth-logout", "OPERATIONS", "OPERATIONS_JWT"));

        long refreshBefore = upstreamTimerCount("operations-auth-refresh", "OPERATIONS", "PUBLIC");
        gatewayClient().post().uri(OperationsTestPaths.REFRESH).contentType(MediaType.APPLICATION_JSON)
            .bodyValue("{\"refreshToken\":\"json-refresh\"}").exchange().expectStatus().isForbidden().expectBody()
            .returnResult();
        Assertions.assertEquals(refreshBefore, upstreamTimerCount("operations-auth-refresh", "OPERATIONS", "PUBLIC"));
        Assertions.assertEquals(0, simulator().journal().count());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_rateAndConcurrencyRejectionsUseOnlyFiniteLimiterValues() {
        double sharedRateBefore = rejectionCounterValue("rate", "shared-route");
        double rateBefore = rejectionCounterValue("rate", "client-ip");
        for (int index = 0; index < 5; index++) {
            simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
                BffScenarioStore.Response.json(200, "{}"));
            loginRequest().exchange().expectStatus().isOk().expectBody().returnResult();
        }
        loginRequest().exchange().expectStatus().isEqualTo(429).expectBody().returnResult();
        awaitRejectionCounter(rateBefore + 1, "rate", "client-ip");
        Assertions.assertEquals(sharedRateBefore, rejectionCounterValue("rate", "shared-route"));
        assertTimerRecorded("operations-auth-login", "RATE_LIMITED", "4xx");

        redisTestCleaner.cleanGuardKeys();
        RequestBarrier firstBarrier = barrierForLogin();
        CompletableFuture<EntityExchangeResult<byte[]>> first = startLogin();
        firstBarrier.awaitRequest(Duration.ofSeconds(3));
        RequestBarrier secondBarrier = barrierForLogin();
        CompletableFuture<EntityExchangeResult<byte[]>> second = startLogin();
        secondBarrier.awaitRequest(Duration.ofSeconds(3));
        double sharedConcurrencyBefore = rejectionCounterValue("concurrency", "shared-route");
        double concurrencyBefore = rejectionCounterValue("concurrency", "client-ip");
        try {
            loginRequest().exchange().expectStatus().isEqualTo(429).expectBody().returnResult();
            awaitRejectionCounter(concurrencyBefore + 1, "concurrency", "client-ip");
            Assertions.assertEquals(sharedConcurrencyBefore, rejectionCounterValue("concurrency", "shared-route"));
            assertTimerRecorded("operations-auth-login", "CONCURRENCY_LIMITED", "4xx");
        } finally {
            firstBarrier.release();
            secondBarrier.release();
        }
        Assertions.assertEquals(200, await(first).getStatus().value());
        Assertions.assertEquals(200, await(second).getStatus().value());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_jwksRefreshMetricsCountActualSharedFetchOnceForSuccessAndFailure() {
        double failureBefore = counterValue(JWKS_REFRESH_COUNTER, "outcome", "failure");
        double suppressedBefore = counterValue(JWKS_REFRESH_COUNTER, "outcome", "suppressed");
        GatewayAutoTestRuntime.jwksSimulator().scenarios().expectStatus(503);
        logoutRequest(JwtFixtureBuilder.template().keyId("metric-failure-a").build()).exchange().expectStatus()
            .isEqualTo(503).expectBody().returnResult();
        awaitCounter(JWKS_REFRESH_COUNTER, failureBefore + 1, "outcome", "failure");

        logoutRequest(JwtFixtureBuilder.template().keyId("metric-failure-b").build()).exchange().expectStatus()
            .isEqualTo(503).expectBody().returnResult();
        awaitCounter(JWKS_REFRESH_COUNTER, suppressedBefore + 1, "outcome", "suppressed");
        GatewayAutoTestRuntime.jwksSimulator().journal().verifySingleGet();

        TEST_CLOCK.advance(Duration.ofSeconds(5));
        double successBefore = counterValue(JWKS_REFRESH_COUNTER, "outcome", "success");
        JwtFixtureBuilder firstSuccessfulJwt = JwtFixtureBuilder.template().keyId("metric-success-a");
        JwtFixtureBuilder secondSuccessfulJwt = JwtFixtureBuilder.template().keyId("metric-success-b");
        com.paytech.gateway.test.simulator.jwks.JwksScenarioStore.RequestBarrier jwksBarrier =
            GatewayAutoTestRuntime.jwksSimulator().scenarios()
                .expectJwksBehindBarrier(JwtFixtureBuilder.jwksJson(List.of("metric-success-a", "metric-success-b")));
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout", BffScenarioStore.Response.json(200, "{}"));
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout", BffScenarioStore.Response.json(200, "{}"));
        CompletableFuture<EntityExchangeResult<byte[]>> first = startLogout(firstSuccessfulJwt.build());
        jwksBarrier.awaitRequest(Duration.ofSeconds(3));
        CompletableFuture<EntityExchangeResult<byte[]>> second = startLogout(secondSuccessfulJwt.build());
        Awaitility.await().pollDelay(Duration.ofMillis(100)).atMost(Duration.ofSeconds(1)).untilAsserted(() -> {
            Assertions.assertEquals(2, GatewayAutoTestRuntime.jwksSimulator().journal().count());
            Assertions.assertFalse(first.isDone());
            Assertions.assertFalse(second.isDone());
        });
        jwksBarrier.release();
        Assertions.assertEquals(200, await(first).getStatus().value());
        Assertions.assertEquals(200, await(second).getStatus().value());
        awaitCounter(JWKS_REFRESH_COUNTER, successBefore + 1, "outcome", "success");

        double suppressedAfterFailure = counterValue(JWKS_REFRESH_COUNTER, "outcome", "suppressed");
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout", BffScenarioStore.Response.json(200, "{}"));
        logoutRequest(firstSuccessfulJwt.build()).exchange().expectStatus().isOk().expectBody().returnResult();
        Assertions.assertEquals(suppressedAfterFailure, counterValue(JWKS_REFRESH_COUNTER, "outcome", "suppressed"));
        logoutRequest(JwtFixtureBuilder.template().keyId("metric-success-miss").build()).exchange().expectStatus()
            .isUnauthorized().expectBody().returnResult();
        awaitCounter(JWKS_REFRESH_COUNTER, suppressedAfterFailure + 1, "outcome", "suppressed");
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyGets(2);
    }

    @Test
    void test_allCustomMetersAndGatewayPathMetricConfigurationRemainBounded() {
        double releaseBefore = counterValue(GatewayMetrics.CONCURRENCY_RELEASE_FAILURE_COUNTER, "phase", "ingress");
        gatewayMetrics.recordConcurrencyReleaseFailure(GuardPhase.INGRESS);
        Assertions.assertEquals(releaseBefore + 1,
            counterValue(GatewayMetrics.CONCURRENCY_RELEASE_FAILURE_COUNTER, "phase", "ingress"));

        Assertions.assertFalse(environment
            .getRequiredProperty("spring.cloud.gateway.server.webflux.metrics.path-tags.enabled", Boolean.class));
        Assertions.assertFalse(environment
            .getRequiredProperty("spring.cloud.gateway.server.webflux.metrics.tags.path.enabled", Boolean.class));
        meterRegistry.getMeters().stream()
            .filter(meter -> "spring.cloud.gateway.requests".equals(meter.getId().getName()))
            .forEach(meter -> Assertions
                .assertTrue(meter.getId().getTags().stream().noneMatch(tag -> "path".equals(tag.getKey()))));

        meterRegistry.getMeters().stream().filter(meter -> meter.getId().getName().startsWith("gateway."))
            .forEach(this::assertBoundedCustomMeter);
    }

    private void assertRequestMetric(String route, String outcome, String statusFamily, Runnable request) {
        long before = timerCount(route, outcome, statusFamily);
        request.run();
        Awaitility.await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> Assertions.assertEquals(before + 1, timerCount(route, outcome, statusFamily)));
    }

    private void assertTimerRecorded(String route, String outcome, String statusFamily) {
        Assertions.assertTrue(timerCount(route, outcome, statusFamily) >= 1,
            () -> "缺少请求 Timer: " + route + "/" + outcome + "/" + statusFamily);
    }

    private long timerCount(String route, String outcome, String statusFamily) {
        Timer timer = meterRegistry.find(REQUEST_TIMER).tags("route", route, "channel", channel(route), "profile",
            profile(route), "outcome", outcome, "status_family", statusFamily).timer();
        return timer == null ? 0 : timer.count();
    }

    private void assertUpstreamTimerRecorded(String route, String channel, String profile) {
        Assertions.assertTrue(upstreamTimerCount(route, channel, profile) >= 1);
    }

    private long upstreamTimerCount(String route, String channel, String profile) {
        Timer timer = meterRegistry.find(UPSTREAM_LATENCY_TIMER)
            .tags("route", route, "channel", channel, "profile", profile).timer();
        return timer == null ? 0 : timer.count();
    }

    private double rejectionCounterValue(String limiter, String dimension) {
        Counter counter = meterRegistry.find(GUARD_REJECTION_COUNTER).tags("route", "operations-auth-login", "channel",
            "OPERATIONS", "profile", "PUBLIC", "limiter", limiter, "dimension", dimension).counter();
        return counter == null ? 0 : counter.count();
    }

    private void awaitRejectionCounter(double expected, String limiter, String dimension) {
        Awaitility.await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> Assertions.assertEquals(expected, rejectionCounterValue(limiter, dimension)));
    }

    private double counterValue(String name, String key, String value) {
        Counter counter = meterRegistry.find(name).tag(key, value).counter();
        return counter == null ? 0 : counter.count();
    }

    private void awaitCounter(String name, double expected, String key, String value) {
        Awaitility.await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> Assertions.assertEquals(expected, counterValue(name, key, value)));
    }

    private void assertBoundedCustomMeter(Meter meter) {
        for (Tag tag : meter.getId().getTags()) {
            Assertions.assertTrue(ALLOWED_TAG_KEYS.contains(tag.getKey()),
                () -> "自定义 Meter 使用未批准 Tag Key: " + meter.getId());
            Assertions.assertTrue(allowedTagValues().get(tag.getKey()).contains(tag.getValue()),
                () -> "自定义 Meter 使用未批准 Tag Value: " + meter.getId());
            Assertions.assertFalse(tag.getValue().contains(RAW_PATH_CANARY));
        }
    }

    private Map<String, Set<String>> allowedTagValues() {
        Set<String> routes =
            new HashSet<>(Arrays.stream(OperationsRouteCode.values()).map(OperationsRouteCode::getCode).toList());
        routes.add("UNMATCHED");
        Set<String> outcomes = new HashSet<>(Arrays.stream(GatewayOutcome.values()).map(Enum::name).toList());
        outcomes.addAll(Set.of("success", "failure", "suppressed"));
        return Map.of("route", Set.copyOf(routes), "channel",
            Set.of(
                "ALL", "OPERATIONS", "CLIENT", "PARTNER", "CHECKOUT", "CLIENT_API", "CHANNEL_CALLBACK", "UNMATCHED"),
            "profile",
            Set.of("PUBLIC", "OPERATIONS_JWT", "CLIENT_JWT", "PARTNER_JWT", "CLIENT_API_CREDENTIAL", "CHECKOUT_SESSION",
                "CHANNEL_CALLBACK", "UNMATCHED"),
            "outcome", Set.copyOf(outcomes), "status_family", Set.of("1xx", "2xx", "3xx", "4xx", "5xx", "NONE"),
            "limiter", Set.of("rate", "concurrency"), "phase", Set.of("ingress", "authenticated-subject"), "dimension",
            Set.copyOf(Arrays.stream(GuardDimension.values()).map(GuardDimension::getCode).toList()), "result",
            Set.of("NEVER_ATTEMPTED", "SUCCESS", "FAILURE", "SUPPRESSED"), "reason",
            Set.of("NONE", "EMPTY_RESPONSE", "INVALID_SNAPSHOT", "CONTROL_PLANE_UNAVAILABLE"));
    }

    private String channel(String route) {
        return "UNMATCHED".equals(route) ? "UNMATCHED" : "OPERATIONS";
    }

    private String profile(String route) {
        if ("UNMATCHED".equals(route)) {
            return "UNMATCHED";
        }
        return "operations-auth-logout".equals(route) ? "OPERATIONS_JWT" : "PUBLIC";
    }

    private RequestBarrier barrierForLogin() {
        RequestBarrier barrier = new RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(200, "{}").waitingFor(barrier));
        return barrier;
    }

    private CompletableFuture<EntityExchangeResult<byte[]>> startLogin() {
        return CompletableFuture.supplyAsync(() -> loginRequest().exchange().expectBody().returnResult());
    }

    private CompletableFuture<EntityExchangeResult<byte[]>> startLogout(String accessToken) {
        return CompletableFuture.supplyAsync(() -> logoutRequest(accessToken).exchange().expectBody().returnResult());
    }

    private EntityExchangeResult<byte[]> await(CompletableFuture<EntityExchangeResult<byte[]>> response) {
        try {
            return response.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("等待 Gateway 请求结束失败", exception);
        }
    }

    private WebTestClient.RequestHeadersSpec<?> loginRequest() {
        return gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
    }

    private WebTestClient.RequestHeadersSpec<?> logoutRequest(String token) {
        return gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header(HttpHeaders.COOKIE, REFRESH_COOKIE)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
    }

    private WebTestClient gatewayClient() {
        return GatewayPublicTestClient.operations(port)
            .defaultHeader("X-Real-IP", "203.0.113.17").responseTimeout(Duration.ofSeconds(5)).build();
    }

    private com.paytech.gateway.test.simulator.bff.OperationsBffSimulator simulator() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }
}
