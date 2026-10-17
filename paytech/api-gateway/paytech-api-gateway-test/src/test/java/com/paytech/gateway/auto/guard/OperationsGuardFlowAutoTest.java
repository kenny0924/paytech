package com.paytech.gateway.auto.guard;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import java.net.InetAddress;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Function;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.data.domain.Range;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.connection.zset.DefaultTuple;
import org.springframework.data.redis.connection.zset.Tuple;
import org.springframework.data.redis.core.types.Expiration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.gateway.guard.*;
import com.paytech.gateway.guard.concurrency.*;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.ratelimit.RateLimitCommand;
import com.paytech.gateway.guard.ratelimit.RateLimitCommandFactory;
import com.paytech.gateway.guard.ratelimit.ReactiveRateLimiter;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.routing.OperationsRouteCatalog;
import com.paytech.gateway.routing.OperationsRouteCode;
import com.paytech.gateway.routing.OperationsRouteDefinition;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.security.ReactiveJwksProvider;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore.RequestBarrier;
import com.paytech.gateway.web.*;

import io.micrometer.core.instrument.MeterRegistry;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.ByteBufFlux;

/** Operations 全 HTTP Guard 顺序、决策与 Lease 终止语义测试。 */
@ExtendWith(OutputCaptureExtension.class)
class OperationsGuardFlowAutoTest extends GatewayAutoTestBase {
    private static final String ORIGIN = "https://operations-console.test";
    private static final String LOGIN_PATH = OperationsTestPaths.LOGIN;
    private static final String REFRESH_PATH = OperationsTestPaths.REFRESH;
    private static final String LOGOUT_PATH = OperationsTestPaths.LOGOUT;
    private static final String REFRESH_COOKIE = "__Secure-operations-refresh=opaque-refresh";
    private static final String CLIENT_IP = "X-Real-IP";
    private static final String DEFAULT_CLIENT_IP = "203.0.113.17";
    private static final long MICROTOKEN = 1_000_000L;
    private static final Duration REDIS_TIMEOUT = Duration.ofSeconds(5);
    private static final String CACHED_KID = "shared-regression-key";

    @LocalServerPort
    int port;
    @Autowired
    RateLimitCommandFactory rateCommandFactory;
    @Autowired
    ReactiveRateLimiter rateLimiter;
    @Autowired
    ReactiveConcurrencyLimiter concurrencyLimiter;
    @Autowired
    ConcurrencyCommandFactory concurrencyCommandFactory;
    @Autowired
    OperationsRouteCatalog routeCatalog;
    @Autowired
    ReactiveRedisConnectionFactory connectionFactory;
    @Autowired
    MeterRegistry meterRegistry;
    @Autowired
    GatewayMetrics gatewayMetrics;
    @Autowired
    GatewayErrorWriter gatewayErrorWriter;
    @Autowired
    GatewayAuthenticatedGuardGlobalFilter guardFilter;
    @Autowired
    GatewayIngressGuardWebFilter ingressGuardFilter;
    @Autowired
    GatewayUpstreamTimeoutGlobalFilter bffTimeoutFilter;
    @Autowired
    RequestBodyLimitGlobalFilter bodyLimitFilter;
    @Autowired
    UpstreamRequestGlobalFilter upstreamRequestFilter;
    @Autowired
    ReactiveJwksProvider reactiveJwksProvider;

    @TestBean(name = "operationsJwtClock", methodName = "fixedClock", enforceOverride = true)
    Clock operationsJwtClock;

    private static void await(CountDownLatch latch, Duration timeout, String message) {
        try {
            Assertions.assertTrue(latch.await(timeout.toMillis(), TimeUnit.MILLISECONDS), message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(message, exception);
        }
    }

    private static ByteBuffer utf8(String value) {
        return StandardCharsets.UTF_8.encode(value);
    }

    private static String decode(ByteBuffer value) {
        return StandardCharsets.UTF_8.decode(value.asReadOnlyBuffer()).toString();
    }

    private static JwtFixtureBuilder jwt() {
        return JwtFixtureBuilder.template().keyId(CACHED_KID);
    }

    private static Clock fixedClock() {
        return Clock.fixed(JwtFixtureBuilder.NOW, ZoneOffset.UTC);
    }

    @BeforeEach
    void prewarmJwksCacheThroughRealHttpProvider() {
        jwksSimulator().scenarios().respondWithJwks(JwtFixtureBuilder.jwksJson(CACHED_KID));
        try {
            reactiveJwksProvider.select(SignedJWT.parse(jwt().build())).single().block(Duration.ofSeconds(1));
        } catch (java.text.ParseException exception) {
            throw new IllegalStateException("无法解析 JWKS 预热 JWT", exception);
        }
        jwksSimulator().reset();
    }

    @Test
    void test_loginRefreshAndLogoutUseTheirExactRateAndConcurrencyDimensions() throws Exception {
        RequestBarrier loginBarrier = barrierFor("/auth/v1/login", 200);
        assertHeldRoute(OperationsRouteCode.AUTH_LOGIN, null, List.of(300L, 5L), List.of(20, 2), loginRequest(),
            loginBarrier);
        assertNoSubjectGuardKeys();

        RequestBarrier refreshBarrier = barrierFor("/auth/v1/refresh", 200);
        assertHeldRoute(OperationsRouteCode.AUTH_REFRESH, null, List.of(600L, 10L), List.of(50, 3), refreshRequest(),
            refreshBarrier);
        assertNoSubjectGuardKeys();

        JwtFixtureBuilder jwt = jwt();
        RequestBarrier logoutBarrier = barrierFor("/auth/v1/logout", 200);
        assertHeldRoute(OperationsRouteCode.AUTH_LOGOUT, JwtFixtureBuilder.SUBJECT, List.of(300L, 10L, 10L),
            List.of(50, 3, 2), logoutRequest(jwt.build()), logoutBarrier);
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_ingressGuardRedisKeysUseTrustedNginxClientIpInsteadOfSocketPeer() {
        String trustedClientIp = "203.0.113.77";
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login", BffScenarioStore.Response.json(200, "{}"));

        gatewayClient(trustedClientIp).post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange().expectStatus().isOk().expectBody()
            .returnResult();

        GuardCommands trustedCommands = commands(OperationsRouteCode.AUTH_LOGIN, null, trustedClientIp);
        GuardCommands socketCommands = commands(OperationsRouteCode.AUTH_LOGIN, null, "127.0.0.1");
        List<String> keys = redisTestCleaner.guardKeys();
        Assertions.assertAll(
            () -> Assertions.assertEquals(4L * MICROTOKEN, bucketTokens(trustedCommands.rate().get(1).key())),
            () -> Assertions.assertTrue(keys.contains(trustedCommands.rate().get(1).key())),
            () -> Assertions.assertFalse(keys.contains(socketCommands.rate().get(1).key())));
    }

    @Test
    void test_optionsConsumesNoGuardState() {
        gatewayClient().options().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST").exchange().expectStatus().isNoContent()
            .expectBody().returnResult();
        assertNoGuardState();
    }

    @Test
    void test_forbiddenOriginConsumesNoGuardState() {
        gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, "https://evil.test").bodyValue("{}")
            .exchange().expectStatus().isForbidden().expectBody().returnResult();
        assertNoGuardState();
    }

    @Test
    void test_missingJwtConsumesIngressRateAndReleasesIngressLeaseWithoutSubjectState() {
        gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.COOKIE, REFRESH_COOKIE).bodyValue("{}").exchange().expectStatus().isUnauthorized()
            .expectBody().returnResult();

        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGOUT, JwtFixtureBuilder.SUBJECT);
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(9L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertTrue(noActiveConcurrencyLeases());
        assertNoSubjectGuardKeys();
        Assertions.assertEquals(0, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_unknownRouteConsumesNoGuardState() {
        assertProblem(gatewayClient().post().uri("/api/operations/auth/v1/unknown")
            .header(HttpHeaders.ORIGIN, ORIGIN).bodyValue("{}").exchange(), 404, "GATEWAY_ROUTE_NOT_FOUND");
        assertNoGuardState();
    }

    @Test
    void test_ingressConcurrencyRejectionPrecedesJwtBodyAndBff() {
        GuardCommands ingressCommands = commands(OperationsRouteCode.AUTH_LOGOUT, null);
        seedFullDimension(ingressCommands.concurrency().getFirst(), redisTimeMillis() + 3_900L);
        String validButUnresolvedToken = jwt().build();

        assertProblem(logoutRequest(validButUnresolvedToken).exchange().expectHeader()
            .valueEquals(HttpHeaders.RETRY_AFTER, "4"), 429, "GATEWAY_CONCURRENCY_LIMITED");

        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(ingressCommands.rate().get(0).key()));
        Assertions.assertEquals(9L * MICROTOKEN, bucketTokens(ingressCommands.rate().get(1).key()));
        assertNoSubjectGuardKeys();
        Assertions.assertEquals(0, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_ingressRateRejectionPrecedesJwtBodyAndBff() {
        GuardCommands ingressCommands = commands(OperationsRouteCode.AUTH_LOGOUT, null);
        for (int index = 0; index < 10; index++) {
            Assertions.assertTrue(rateLimiter.evaluate(ingressCommands.rate()).block(REDIS_TIMEOUT).allowed());
        }

        assertProblem(logoutRequest(jwt().build()).exchange().expectHeader()
            .value(HttpHeaders.RETRY_AFTER, value -> Assertions.assertTrue(Long.parseLong(value) > 0)), 429,
            "GATEWAY_RATE_LIMITED");

        Assertions.assertTrue(noActiveConcurrencyLeases());
        assertNoSubjectGuardKeys();
        Assertions.assertEquals(0, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_synchronousGuardContextFailureUsesStable503WithoutConsumingRedis() {
        MockServerWebExchange ingressExchange = MockServerWebExchange.from(MockServerHttpRequest.post(LOGIN_PATH));
        ingressExchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE,
            runtimeRoute("operations-auth-login"));
        ingressGuardFilter
            .filter(ingressExchange,
                ignored -> Mono.error(new AssertionError("Ingress context failure must stop the chain")))
            .block(Duration.ofSeconds(1));
        Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, ingressExchange.getResponse().getStatusCode());

        MockServerWebExchange subjectExchange = MockServerWebExchange.from(MockServerHttpRequest.post(LOGOUT_PATH));
        subjectExchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE,
            runtimeRoute("operations-auth-logout"));
        subjectExchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_ADDRESS,
            InetAddress.getLoopbackAddress());
        guardFilter
            .filter(subjectExchange,
                ignored -> Mono.error(new AssertionError("Subject context failure must stop the chain")))
            .block(Duration.ofSeconds(1));
        Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, subjectExchange.getResponse().getStatusCode());
        assertNoGuardState();
    }

    @Test
    void test_downstreamGuardDependencyExceptionIsNotMisclassifiedAsLimiterFailure() throws Exception {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(LOGIN_PATH)
            .remoteAddress(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 12_345)));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE,
            runtimeRoute("operations-auth-login"));
        exchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_ADDRESS,
            InetAddress.getByName(DEFAULT_CLIENT_IP));
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        GuardDependencyUnavailableException downstreamFailure =
            new GuardDependencyUnavailableException("downstream failure");

        GuardDependencyUnavailableException thrown = Assertions.assertThrows(GuardDependencyUnavailableException.class,
            () -> ingressGuardFilter
                .filter(exchange, ignored -> Mono.error(downstreamFailure))
                .block(Duration.ofSeconds(1)));

        Assertions.assertSame(downstreamFailure, thrown);
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(4L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertNull(exchange.getResponse().getStatusCode());
        Assertions.assertFalse(exchange.getResponse().isCommitted());
        awaitNoActiveConcurrencyLeases();
    }

    @Test
    void test_invalidJwtConsumesIngressAndReleasesLeaseWithoutSubjectState() {
        JwtFixtureBuilder jwt = jwt().signWithWrongKey();

        logoutRequest(jwt.build()).exchange().expectStatus().isUnauthorized().expectBody().returnResult();

        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGOUT, JwtFixtureBuilder.SUBJECT);
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(9L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertTrue(noActiveConcurrencyLeases());
        assertNoSubjectGuardKeys();
        Assertions.assertEquals(0, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_invalidJoseHeadersStillSpendIngressBeforeZeroNetworkRejection() {
        assertProblem(logoutRequest(jwt().algorithm(JWSAlgorithm.PS256).build()).exchange(), 401,
            "GATEWAY_UNAUTHORIZED");
        assertProblem(logoutRequest(jwt().keyId("invalid/kid").build()).exchange(), 401,
            "GATEWAY_UNAUTHORIZED");

        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGOUT, JwtFixtureBuilder.SUBJECT);
        assertTwoIngressTokensSpent(bucketTokens(commands.rate().get(0).key()), 298L, 299L);
        assertTwoIngressTokensSpent(bucketTokens(commands.rate().get(1).key()), 8L, 9L);
        Assertions.assertTrue(noActiveConcurrencyLeases());
        assertNoSubjectGuardKeys();
        Assertions.assertEquals(0, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    private void assertTwoIngressTokensSpent(long actual, long inclusiveLowerTokens, long exclusiveUpperTokens) {
        Assertions.assertTrue(actual >= inclusiveLowerTokens * MICROTOKEN && actual < exclusiveUpperTokens * MICROTOKEN,
            () -> "两次请求后 Bucket 必须已消耗两个 Token，实际 Microtoken=" + actual);
    }

    @Test
    void test_oversizeBodyConsumesIngressRateAndReleasesLeaseBeforeBff() {
        assertProblem(gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .bodyValue("x".repeat(16_385)).exchange(), 413, "GATEWAY_PAYLOAD_TOO_LARGE");

        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(4L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertTrue(noActiveConcurrencyLeases());
        assertNoSubjectGuardKeys();
        Assertions.assertEquals(0, simulator().journal().count());
        Assertions.assertEquals(0, simulator().transportProbe().requestStartCount());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_loginSlowBodyTimeoutConsumesIngressRateAndReleasesLeaseWithoutSubjectState() {
        RawResponse response = slowBodyRequest(LOGIN_PATH, headers -> {
        });

        Assertions.assertEquals(408, response.status());
        Assertions.assertTrue(response.body().contains("GATEWAY_REQUEST_TIMEOUT"));
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(4L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        awaitNoActiveConcurrencyLeases();
        assertNoSubjectGuardKeys();
        Assertions.assertEquals(0, simulator().transportProbe().requestStartCount());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_logoutSlowBodyTimeoutConsumesBothRatesAndReleasesBothLeases() {
        JwtFixtureBuilder jwt = jwt();
        RawResponse response = slowBodyRequest(LOGOUT_PATH, headers -> {
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.build());
            headers.set(HttpHeaders.COOKIE, REFRESH_COOKIE);
        });

        Assertions.assertEquals(408, response.status());
        Assertions.assertTrue(response.body().contains("GATEWAY_REQUEST_TIMEOUT"));
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGOUT, JwtFixtureBuilder.SUBJECT);
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(9L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertEquals(9L * MICROTOKEN, bucketTokens(commands.rate().get(2).key()));
        awaitNoActiveConcurrencyLeases();
        Assertions.assertEquals(0, simulator().transportProbe().requestStartCount());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_logoutOversizeBodyConsumesBothRatesAndReleasesBothLeases() {
        JwtFixtureBuilder jwt = jwt();

        assertProblem(gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.build()).header(HttpHeaders.COOKIE, REFRESH_COOKIE)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("x".repeat(16_385)).exchange(), 413,
            "GATEWAY_PAYLOAD_TOO_LARGE");

        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGOUT, JwtFixtureBuilder.SUBJECT);
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(9L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertEquals(9L * MICROTOKEN, bucketTokens(commands.rate().get(2).key()));
        awaitNoActiveConcurrencyLeases();
        Assertions.assertEquals(0, simulator().transportProbe().requestStartCount());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_logoutAggregationCancellationReleasesBothLeasesWithoutRefundingRate() {
        JwtFixtureBuilder jwt = jwt();
        Disposable request = GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set(CLIENT_IP, DEFAULT_CLIENT_IP);
            headers.set(HttpHeaders.ORIGIN, ORIGIN);
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.build());
            headers.set(HttpHeaders.COOKIE, REFRESH_COOKIE);
            headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            headers.remove(HttpHeaders.CONTENT_LENGTH);
        }).post().uri(LOGOUT_PATH)
            .send(Flux.concat(Mono.just(Unpooled.wrappedBuffer(new byte[] {1})), Mono.<ByteBuf>never())).response()
            .subscribe(ignored -> {
            }, ignored -> {
            });
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGOUT, JwtFixtureBuilder.SUBJECT);

        try {
            Awaitility.await().atMost(Duration.ofSeconds(3))
                .until(() -> commands.concurrency().stream().allMatch(command -> cardinality(command.key()) == 1));
            request.dispose();
            awaitNoActiveConcurrencyLeases();
        } finally {
            request.dispose();
        }

        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(9L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertEquals(9L * MICROTOKEN, bucketTokens(commands.rate().get(2).key()));
        Assertions.assertEquals(0, simulator().transportProbe().requestStartCount());
        jwksSimulator().journal().verifyNoRequests();
    }

    private void assertNoGuardState() {
        Assertions.assertEquals(List.of(), redisTestCleaner.guardKeys());
        Assertions.assertEquals(0, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_rateRejectionSkipsConcurrencyAcquireAndBff() {
        for (int index = 0; index < 5; index++) {
            simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
                BffScenarioStore.Response.json(200, "{}"));
            loginRequest().exchange().expectStatus().isOk().expectBody().returnResult();
        }

        assertProblem(loginRequest().exchange().expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "12"), 429,
            "GATEWAY_RATE_LIMITED");

        Assertions.assertEquals(5, simulator().journal().count());
        Assertions.assertTrue(noActiveConcurrencyLeases());
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        long remainingMicrotokens = bucketTokens(commands.rate().get(1).key());
        Assertions.assertTrue(remainingMicrotokens >= 0 && remainingMicrotokens < MICROTOKEN,
            () -> "IP Bucket 不应在拒绝后已补充出一个完整 Token: " + remainingMicrotokens);
    }

    @Test
    void test_concurrencyRejectionKeepsRateDeductionAndNeverCallsThirdBff() throws Exception {
        RequestBarrier firstBarrier = barrierFor("/auth/v1/login", 200);
        CompletableFuture<EntityExchangeResult<byte[]>> first = start(loginRequest());
        firstBarrier.awaitRequest(Duration.ofSeconds(3));
        RequestBarrier secondBarrier = barrierFor("/auth/v1/login", 200);
        CompletableFuture<EntityExchangeResult<byte[]>> second = start(loginRequest());
        secondBarrier.awaitRequest(Duration.ofSeconds(3));

        try {
            assertProblem(loginRequest().exchange().expectHeader().value(HttpHeaders.RETRY_AFTER, value -> {
                    long retryAfter = Long.parseLong(value);
                    Assertions.assertTrue(retryAfter >= 29 && retryAfter <= 30);
                }), 429, "GATEWAY_CONCURRENCY_LIMITED");
            GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
            Assertions.assertTrue(bucketTokens(commands.rate().get(0).key()) < 298L * MICROTOKEN);
            Assertions.assertTrue(bucketTokens(commands.rate().get(1).key()) < 3L * MICROTOKEN);
            Assertions.assertEquals(List.of(2L, 2L),
                commands.concurrency().stream().map(command -> cardinality(command.key())).toList());
            Assertions.assertEquals(2, simulator().journal().count());
        } finally {
            firstBarrier.release();
            secondBarrier.release();
        }

        Assertions.assertEquals(HttpStatus.OK, await(first).getStatus());
        Assertions.assertEquals(HttpStatus.OK, await(second).getStatus());
        awaitNoActiveConcurrencyLeases();
    }

    @Test
    void test_subjectConcurrencyRejectionReturnsBeforeHoldersReleaseAndKeepsBothRateDeductions() throws Exception {
        JwtFixtureBuilder jwt = jwt();
        String token = jwt.build();
        RequestBarrier firstBarrier = barrierFor("/auth/v1/logout", 200);
        CompletableFuture<EntityExchangeResult<byte[]>> first = start(logoutRequest(token));
        firstBarrier.awaitRequest(Duration.ofSeconds(3));
        RequestBarrier secondBarrier = barrierFor("/auth/v1/logout", 200);
        CompletableFuture<EntityExchangeResult<byte[]>> second = start(logoutRequest(token));
        secondBarrier.awaitRequest(Duration.ofSeconds(3));

        try {
            assertProblem(logoutRequest(token).exchange().expectHeader().value(HttpHeaders.RETRY_AFTER, value -> {
                    long retryAfter = Long.parseLong(value);
                    Assertions.assertTrue(retryAfter >= 29 && retryAfter <= 30);
                }), 429, "GATEWAY_CONCURRENCY_LIMITED");
            GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGOUT, JwtFixtureBuilder.SUBJECT);
            Assertions.assertTrue(bucketTokens(commands.rate().get(0).key()) < 298L * MICROTOKEN);
            Assertions.assertTrue(bucketTokens(commands.rate().get(1).key()) < 8L * MICROTOKEN);
            Assertions.assertTrue(bucketTokens(commands.rate().get(2).key()) < 8L * MICROTOKEN);
            Assertions.assertEquals(List.of(2L, 2L, 2L),
                commands.concurrency().stream().map(command -> cardinality(command.key())).toList());
            Assertions.assertEquals(2, simulator().journal().count());
        } finally {
            firstBarrier.release();
            secondBarrier.release();
        }

        Assertions.assertEquals(HttpStatus.OK, await(first).getStatus());
        Assertions.assertEquals(HttpStatus.OK, await(second).getStatus());
        awaitNoActiveConcurrencyLeases();
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_concurrency429UsesMaximumCeilingRetryAfterAcrossFullDimensions() {
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        long now = redisTimeMillis();
        seedFullDimension(commands.concurrency().get(0), now + 900L);
        seedFullDimension(commands.concurrency().get(1), now + 3_900L);

        assertProblem(loginRequest().exchange().expectHeader().valueEquals(HttpHeaders.RETRY_AFTER, "4"), 429,
            "GATEWAY_CONCURRENCY_LIMITED");

        Assertions.assertEquals(0, simulator().journal().count());
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(4L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
    }

    @Test
    void test_leaseReleasesAfterSuccessBusinessErrorDisconnectAndTimeout() {
        JwtFixtureBuilder jwt = jwt();
        String token = jwt.build();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout", BffScenarioStore.Response.json(200, "{}"));
        logoutRequest(token).exchange().expectStatus().isOk().expectBody().returnResult();
        awaitNoActiveConcurrencyLeases();

        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout",
            BffScenarioStore.Response.json(422,
                "{\"code\":\"BUSINESS_REJECTED\",\"message\":\"rejected\",\"requestId\":\""
                    + BffScenarioStore.REQUEST_ID_TOKEN
                    + "\",\"timestamp\":\"2026-09-06T10:11:12.123456Z\"}")
                .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId());
        logoutRequest(token).exchange().expectStatus().isEqualTo(422).expectBody().returnResult();
        awaitNoActiveConcurrencyLeases();

        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout",
            BffScenarioStore.Response.immediateDisconnect());
        assertProblem(logoutRequest(token).exchange(), 502, "UPSTREAM_PROTOCOL_ERROR");
        awaitNoActiveConcurrencyLeases();

        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout",
            BffScenarioStore.Response.delayed(200, "{}", Duration.ofSeconds(2)));
        assertProblem(logoutRequest(token).exchange(), 504, "GATEWAY_UPSTREAM_TIMEOUT");
        awaitNoActiveConcurrencyLeases();
        Assertions.assertEquals(4, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_streamedResponseTimeoutTerminatesCommittedResponseAndReleasesLease() {
        RequestBarrier remainingBodyBarrier = new RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.chunkedUntilReleased(200, "first-chunk", "-remaining", remainingBodyBarrier));
        CountDownLatch firstChunkReceived = new CountDownLatch(1);
        CountDownLatch responseTerminated = new CountDownLatch(1);
        AtomicInteger responseStatus = new AtomicInteger();
        AtomicReference<String> firstChunk = new AtomicReference<>();
        long startedNanos = System.nanoTime();
        Disposable request = GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set(CLIENT_IP, DEFAULT_CLIENT_IP);
            headers.set(HttpHeaders.ORIGIN, ORIGIN);
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        }).post().uri(LOGIN_PATH).send(ByteBufFlux.fromString(Mono.just("{}")))
            .response((response, content) -> content.asString().doOnNext(chunk -> {
                responseStatus.set(response.status().code());
                firstChunk.compareAndSet(null, chunk);
                firstChunkReceived.countDown();
            }).then()).doFinally(ignored -> responseTerminated.countDown()).subscribe(ignored -> {
            }, ignored -> {
            });

        try {
            remainingBodyBarrier.awaitRequest(Duration.ofSeconds(3));
            await(firstChunkReceived, Duration.ofSeconds(3), "客户端未收到 BFF 首个响应分块");
            GuardCommands heldCommands = commands(OperationsRouteCode.AUTH_LOGIN, null);
            Assertions
                .assertTrue(heldCommands.concurrency().stream().allMatch(command -> cardinality(command.key()) == 1));

            await(responseTerminated, Duration.ofSeconds(3), "完整下游响应链未在路由超时后终止");
            long elapsedMillis = Duration.ofNanos(System.nanoTime() - startedNanos).toMillis();
            Assertions.assertTrue(elapsedMillis >= 800 && elapsedMillis < 3_000,
                () -> "流式响应超时未使用路由 1 秒预算: " + elapsedMillis + "ms");
            Assertions.assertEquals(200, responseStatus.get(), "已提交响应不得重写为 504");
            Assertions.assertEquals("first-chunk", firstChunk.get());
            awaitNoActiveConcurrencyLeases();

            simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
                BffScenarioStore.Response.json(200, "{}"));
            loginRequest().exchange().expectStatus().isOk().expectBody().returnResult();
            awaitNoActiveConcurrencyLeases();
        } finally {
            request.dispose();
            remainingBodyBarrier.release();
        }
        Assertions.assertEquals(2, simulator().journal().count());
    }

    @Test
    void test_clientCancellationReleasesLeaseAfterBffBarrier() {
        JwtFixtureBuilder jwt = jwt();
        String token = jwt.build();
        RequestBarrier barrier = barrierFor("/auth/v1/logout", 200);
        Disposable request = GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set(CLIENT_IP, DEFAULT_CLIENT_IP);
            headers.set(HttpHeaders.ORIGIN, ORIGIN);
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + token);
            headers.set(HttpHeaders.COOKIE, REFRESH_COOKIE);
        }).post().uri(LOGOUT_PATH).send(ByteBufFlux.fromString(Mono.just("{}"))).response().subscribe(ignored -> {
        }, ignored -> {
        });

        barrier.awaitRequest(Duration.ofSeconds(3));
        try {
            GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGOUT, JwtFixtureBuilder.SUBJECT);
            Assertions.assertTrue(commands.concurrency().stream().allMatch(command -> cardinality(command.key()) == 1));
            request.dispose();
            awaitNoActiveConcurrencyLeases();
        } finally {
            request.dispose();
            barrier.release();
        }

        Assertions.assertEquals(1, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_cancelAfterRedisAcquireBeforeDecisionHandoffReleasesLeaseWithoutRefundingRate() {
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        AcquireResultBarrier barrier = new AcquireResultBarrier(concurrencyLimiter);
        GatewayGuardAdmission admission =
            new GatewayGuardAdmission(rateLimiter, barrier, gatewayErrorWriter, gatewayMetrics);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(LOGIN_PATH)
            .remoteAddress(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 12_345)));
        AtomicBoolean admitted = new AtomicBoolean();
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        Disposable request = admission
            .admit(exchange, commands.rate(), commands.concurrency(), GatewayExchangeAttributes.INGRESS_GUARD_LIFECYCLE,
                GatewayMetrics.GuardPhase.INGRESS, () -> Mono.fromRunnable(() -> admitted.set(true)).then(Mono.never()))
            .subscribe(ignored -> {
            }, unexpectedError::set);
        try {
            barrier.awaitAcquireResult(Duration.ofSeconds(3));
            Assertions.assertTrue(commands.concurrency().stream().allMatch(command -> cardinality(command.key()) == 1));

            request.dispose();
            barrier.releaseResult();
            awaitNoActiveConcurrencyLeases();
        } finally {
            request.dispose();
            barrier.releaseResult();
        }

        Assertions.assertFalse(admitted.get());
        Assertions.assertNull(unexpectedError.get());
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(4L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_cancelDuringLeaseOwnershipHandoffReleasesLeaseWithoutRefundingRate() {
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        OwnershipHandoffBarrier barrier = new OwnershipHandoffBarrier();
        GatewayGuardAdmission admission = OperationsGuardAdmissionTestSupport.withOwnershipHandoffGate(rateLimiter,
            concurrencyLimiter, gatewayErrorWriter, gatewayMetrics, barrier::awaitTransfer);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(LOGIN_PATH)
            .remoteAddress(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 12_345)));
        AtomicBoolean admitted = new AtomicBoolean();
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();

        Disposable request = admission
            .admit(exchange, commands.rate(), commands.concurrency(), GatewayExchangeAttributes.INGRESS_GUARD_LIFECYCLE,
                GatewayMetrics.GuardPhase.INGRESS, () -> Mono.fromRunnable(() -> admitted.set(true)).then(Mono.never()))
            .subscribe(ignored -> {
            }, unexpectedError::set);
        try {
            barrier.awaitTransferStarted(Duration.ofSeconds(3));
            Assertions.assertTrue(commands.concurrency().stream().allMatch(command -> cardinality(command.key()) == 1));

            request.dispose();
            barrier.releaseTransfer();
            awaitNoActiveConcurrencyLeases();
        } finally {
            request.dispose();
            barrier.releaseTransfer();
        }

        Assertions.assertFalse(admitted.get());
        Assertions.assertNull(unexpectedError.get());
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(4L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_ownershipHandoffFailureReleasesLeaseWithoutRefundingRate() {
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        IllegalStateException handoffFailure = new IllegalStateException("handoff failed");
        GatewayGuardAdmission admission = OperationsGuardAdmissionTestSupport.withOwnershipHandoffGate(rateLimiter,
            concurrencyLimiter, gatewayErrorWriter, gatewayMetrics, () -> Mono.error(handoffFailure));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(LOGIN_PATH)
            .remoteAddress(new java.net.InetSocketAddress(InetAddress.getLoopbackAddress(), 12_345)));
        AtomicBoolean admitted = new AtomicBoolean();
        AtomicReference<Throwable> observedError = new AtomicReference<>();

        admission
            .admit(exchange, commands.rate(), commands.concurrency(), GatewayExchangeAttributes.INGRESS_GUARD_LIFECYCLE,
                GatewayMetrics.GuardPhase.INGRESS, () -> Mono.fromRunnable(() -> admitted.set(true)).then(Mono.never()))
            .subscribe(ignored -> {
            }, observedError::set);

        Awaitility.await().atMost(Duration.ofSeconds(2)).until(() -> observedError.get() != null);
        awaitNoActiveConcurrencyLeases();
        Assertions.assertSame(handoffFailure, observedError.get());
        Assertions.assertFalse(admitted.get());
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(4L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_releaseFailurePreservesResponseAndEmitsBoundedMetricAndLog(CapturedOutput output) {
        double counterBefore = releaseFailureCount("ingress");
        RequestBarrier barrier = barrierFor("/auth/v1/login", 200);
        CompletableFuture<EntityExchangeResult<byte[]>> response = start(loginRequest());
        barrier.awaitRequest(Duration.ofSeconds(3));
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        ConcurrencyCommand earlier = commands.concurrency().get(0);
        ConcurrencyCommand later = commands.concurrency().get(1);
        String leaseId = members(earlier.key()).stream().findFirst().orElseThrow();
        replaceWithExpiringString(later.key(), later.keyTtlMillis());

        barrier.release();
        Assertions.assertEquals(HttpStatus.OK, await(response).getStatus());
        Awaitility.await().atMost(Duration.ofSeconds(2))
            .untilAsserted(() -> Assertions.assertEquals(counterBefore + 1, releaseFailureCount("ingress")));
        Assertions.assertTrue(output.getOut().contains("Gateway Guard concurrency lease release failed"));
        Assertions.assertEquals(Set.of(leaseId), members(earlier.key()));
        Assertions.assertEquals("release-blocker", stringValue(later.key()));
        Assertions.assertTrue(keyTtl(later.key()) > 0);
    }

    @Test
    void test_subjectAdmissionRunsBeforeBodyAndSanitizer() {
        Assertions.assertEquals(GatewayGlobalFilterOrder.INGRESS_GUARD,
            ingressGuardFilter.getOrder());
        Assertions.assertEquals(GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT, bodyLimitFilter.getOrder());
        Assertions.assertEquals(GatewayGlobalFilterOrder.AUTHENTICATED_GUARD, guardFilter.getOrder());
        Assertions.assertEquals(GatewayGlobalFilterOrder.UPSTREAM_TIMEOUT, bffTimeoutFilter.getOrder());
        Assertions.assertEquals(GatewayGlobalFilterOrder.UPSTREAM_REQUEST, upstreamRequestFilter.getOrder());
        Assertions.assertTrue(guardFilter.getOrder() < bodyLimitFilter.getOrder());
        Assertions.assertTrue(bodyLimitFilter.getOrder() < bffTimeoutFilter.getOrder());
        Assertions.assertTrue(bffTimeoutFilter.getOrder() < upstreamRequestFilter.getOrder());
        Assertions.assertTrue(upstreamRequestFilter.getOrder() < GatewayGlobalFilterOrder.NETTY_ROUTING);

        assertProblem(gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .bodyValue("x".repeat(16_385)).exchange(), 413, "GATEWAY_PAYLOAD_TOO_LARGE");
        GuardCommands commands = commands(OperationsRouteCode.AUTH_LOGIN, null);
        Assertions.assertEquals(299L * MICROTOKEN, bucketTokens(commands.rate().get(0).key()));
        Assertions.assertEquals(4L * MICROTOKEN, bucketTokens(commands.rate().get(1).key()));
        Assertions.assertTrue(noActiveConcurrencyLeases());
        Assertions.assertEquals(0, simulator().journal().count());
    }

    private void assertNoSubjectGuardKeys() {
        Assertions.assertTrue(redisTestCleaner.guardKeys().stream()
            .noneMatch(key -> key.contains(":authenticated-subject:")));
    }

    private void assertHeldRoute(OperationsRouteCode routeCode, String subject, List<Long> expectedRateCapacity,
        List<Integer> expectedConcurrencyLimit, WebTestClient.RequestHeadersSpec<?> request, RequestBarrier barrier) {
        CompletableFuture<EntityExchangeResult<byte[]>> response = start(request);
        barrier.awaitRequest(Duration.ofSeconds(3));
        GuardCommands commands = commands(routeCode, subject);
        try {
            Assertions.assertEquals(expectedRateCapacity,
                commands.rate().stream().map(command -> command.capacityMicrotokens() / MICROTOKEN).toList());
            Assertions.assertEquals(expectedConcurrencyLimit,
                commands.concurrency().stream().map(ConcurrencyCommand::limit).toList());
            Assertions.assertTrue(commands.concurrency().stream()
                .allMatch(command -> command.leaseDurationMillis() == 30_000L && command.keyTtlMillis() == 60_000L));
            for (RateLimitCommand command : commands.rate()) {
                Assertions.assertEquals(command.capacityMicrotokens() - MICROTOKEN, bucketTokens(command.key()));
            }
            Set<String> leaseIds = commands.concurrency().stream().map(command -> members(command.key()))
                .flatMap(Set::stream).collect(java.util.stream.Collectors.toSet());
            Assertions.assertEquals(subject == null ? 1 : 2, leaseIds.size());
            Assertions.assertTrue(commands.concurrency().stream().allMatch(command -> cardinality(command.key()) == 1));
        } finally {
            barrier.release();
        }
        Assertions.assertEquals(HttpStatus.OK, await(response).getStatus());
        Awaitility.await().atMost(Duration.ofSeconds(2))
            .until(() -> commands.concurrency().stream().allMatch(command -> cardinality(command.key()) == 0));
    }

    private RequestBarrier barrierFor(String path, int status) {
        RequestBarrier barrier = new RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, path,
            BffScenarioStore.Response.json(status, "{}").waitingFor(barrier));
        return barrier;
    }

    private GatewayRuntimeRoute runtimeRoute(String routeCode) {
        return new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.operationsSnapshot()).catalog().all().stream()
            .filter(route -> route.routeCode().equals(routeCode)).findFirst().orElseThrow();
    }

    private GuardCommands commands(OperationsRouteCode routeCode, String subject) {
        return commands(routeCode, subject, DEFAULT_CLIENT_IP);
    }

    private GuardCommands commands(OperationsRouteCode routeCode, String subject, String clientIp) {
        try {
            GuardContext context = new GuardContext(routeCode, InetAddress.getByName(clientIp), subject);
            OperationsRouteDefinition route = routeCatalog.all().stream()
                .filter(candidate -> candidate.code() == routeCode).findFirst().orElseThrow();
            List<RateLimitCommand> rateCommands =
                new ArrayList<>(rateCommandFactory.create(route.ingressGuard(), context));
            List<ConcurrencyCommand> concurrencyCommands =
                new ArrayList<>(concurrencyCommandFactory.create(route.ingressGuard(), context));
            if (route.authenticatedGuard() != null && context.hasAuthenticatedSubject()) {
                rateCommands.addAll(rateCommandFactory.create(route.authenticatedGuard(), context));
                concurrencyCommands.addAll(concurrencyCommandFactory.create(route.authenticatedGuard(), context));
            }
            return new GuardCommands(List.copyOf(rateCommands), List.copyOf(concurrencyCommands));
        } catch (java.net.UnknownHostException exception) {
            throw new IllegalStateException("Loopback 地址不可用", exception);
        }
    }

    private CompletableFuture<EntityExchangeResult<byte[]>> start(WebTestClient.RequestHeadersSpec<?> request) {
        return CompletableFuture.supplyAsync(() -> request.exchange().expectBody().returnResult());
    }

    private EntityExchangeResult<byte[]> await(CompletableFuture<EntityExchangeResult<byte[]>> response) {
        try {
            return response.get(5, TimeUnit.SECONDS);
        } catch (Exception exception) {
            throw new IllegalStateException("等待 Gateway 实际 HTTP 响应失败", exception);
        }
    }

    private WebTestClient.RequestHeadersSpec<?> loginRequest() {
        return gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
    }

    private WebTestClient.RequestHeadersSpec<?> refreshRequest() {
        return gatewayClient().post().uri(REFRESH_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.COOKIE, REFRESH_COOKIE).contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
    }

    private WebTestClient.RequestHeadersSpec<?> logoutRequest(String accessToken) {
        return gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken).header(HttpHeaders.COOKIE, REFRESH_COOKIE)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
    }

    private RawResponse slowBodyRequest(String path,
        java.util.function.Consumer<io.netty.handler.codec.http.HttpHeaders> headerCustomizer) {
        Flux<ByteBuf> frequentChunks = Flux.interval(Duration.ZERO, Duration.ofMillis(700))
            .map(ignored -> Unpooled.wrappedBuffer(new byte[] {1})).concatWith(Mono.<ByteBuf>never());
        return GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set(CLIENT_IP, DEFAULT_CLIENT_IP);
            headers.set(HttpHeaders.ORIGIN, ORIGIN);
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
            headers.set(HttpHeaders.TRANSFER_ENCODING, "chunked");
            headers.remove(HttpHeaders.CONTENT_LENGTH);
            headerCustomizer.accept(headers);
        }).post().uri(path).send(frequentChunks).responseSingle((response, content) -> content.asString()
            .defaultIfEmpty("").map(body -> new RawResponse(response.status().code(), body)))
            .block(Duration.ofSeconds(8));
    }

    private WebTestClient gatewayClient() {
        return gatewayClient(DEFAULT_CLIENT_IP);
    }

    private WebTestClient gatewayClient(String clientIp) {
        return GatewayPublicTestClient.operations(port).defaultHeader(CLIENT_IP, clientIp)
            .responseTimeout(Duration.ofSeconds(5)).build();
    }

    private com.paytech.gateway.test.simulator.bff.OperationsBffSimulator simulator() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }

    private com.paytech.gateway.test.simulator.jwks.JwksSimulator jwksSimulator() {
        return GatewayAutoTestRuntime.jwksSimulator();
    }

    private void awaitNoActiveConcurrencyLeases() {
        Awaitility.await().atMost(Duration.ofSeconds(2)).until(this::noActiveConcurrencyLeases);
    }

    private boolean noActiveConcurrencyLeases() {
        return concurrencyLeaseKeys().stream().allMatch(key -> cardinality(key) == 0);
    }

    private List<String> concurrencyLeaseKeys() {
        return redisTestCleaner.guardKeys().stream()
            .filter(key -> key.contains(":concurrency:") && key.endsWith(":leases")).toList();
    }

    private long bucketTokens(String key) {
        List<ByteBuffer> value =
            withRedis(connection -> connection.hashCommands().hMGet(utf8(key), List.of(utf8("t"))));
        Assertions.assertEquals(1, value.size());
        return Long.parseLong(decode(value.getFirst()));
    }

    private long cardinality(String key) {
        Long cardinality = withRedis(connection -> connection.zSetCommands().zCard(utf8(key)).defaultIfEmpty(0L));
        return cardinality == null ? 0L : cardinality;
    }

    private Set<String> members(String key) {
        List<String> values = withRedis(connection -> connection.zSetCommands().zRange(utf8(key), Range.closed(0L, -1L))
            .map(OperationsGuardFlowAutoTest::decode).collectList());
        return Set.copyOf(values);
    }

    private long redisTimeMillis() {
        return withRedis(connection -> connection.serverCommands().time(TimeUnit.MILLISECONDS));
    }

    private void seedFullDimension(ConcurrencyCommand command, long expiryMillis) {
        List<Tuple> leases = new ArrayList<>();
        for (int index = 0; index < command.limit(); index++) {
            leases.add(
                new DefaultTuple(UUID.randomUUID().toString().getBytes(StandardCharsets.UTF_8), (double)expiryMillis));
        }
        Map<ByteBuffer, ByteBuffer> metadata = new LinkedHashMap<>();
        metadata.put(utf8("hw"), utf8(Long.toString(redisTimeMillis())));
        metadata.put(utf8("l"), utf8(Integer.toString(command.limit())));
        withRedis(connection -> connection.zSetCommands().zAdd(utf8(command.key()), leases)
            .then(connection.hashCommands().hMSet(utf8(command.keys().metadataKey()), metadata))
            .then(connection.keyCommands().pExpire(utf8(command.key()), Duration.ofMillis(command.stateTtlMillis())))
            .then(connection.keyCommands().pExpire(utf8(command.keys().metadataKey()),
                Duration.ofMillis(command.stateTtlMillis())))
            .then());
    }

    private void replaceWithExpiringString(String key, long ttlMillis) {
        withRedis(
            connection -> connection
                .keyCommands().unlink(utf8(key)).then(connection.stringCommands().set(utf8(key),
                    utf8("release-blocker"), Expiration.milliseconds(ttlMillis), RedisStringCommands.SetOption.UPSERT))
                .then());
    }

    private String stringValue(String key) {
        return decode(withRedis(connection -> connection.stringCommands().get(utf8(key))));
    }

    private long keyTtl(String key) {
        return withRedis(connection -> connection.keyCommands().pTtl(utf8(key)));
    }

    private double releaseFailureCount(String phase) {
        return meterRegistry.find(GatewayMetrics.CONCURRENCY_RELEASE_FAILURE_COUNTER).tag("phase", phase).counter()
            .count();
    }

    private <T> T withRedis(Function<ReactiveRedisConnection, Mono<T>> operation) {
        return Mono.usingWhen(Mono.fromSupplier(connectionFactory::getReactiveConnection), operation,
            ReactiveRedisConnection::closeLater, (connection, error) -> connection.closeLater(),
            ReactiveRedisConnection::closeLater).block(REDIS_TIMEOUT);
    }

    private static final class AcquireResultBarrier implements ReactiveConcurrencyLimiter {
        private final ReactiveConcurrencyLimiter delegate;
        private final CountDownLatch resultReceived = new CountDownLatch(1);
        private final Sinks.One<Void> resultRelease = Sinks.one();

        private AcquireResultBarrier(ReactiveConcurrencyLimiter delegate) {
            this.delegate = delegate;
        }

        @Override
        public Mono<ConcurrencyDecision> acquire(List<ConcurrencyCommand> commands) {
            return delegate.acquire(commands).doOnNext(decision -> {
                if (decision.allowed()) {
                    resultReceived.countDown();
                }
            }).delayUntil(ignored -> resultRelease.asMono());
        }

        @Override
        public Mono<Void> release(ConcurrencyLease lease) {
            return delegate.release(lease);
        }

        private void awaitAcquireResult(Duration timeout) {
            await(resultReceived, timeout, "Redis Acquire 已写入但结果尚未进入 ownership handoff");
        }

        private void releaseResult() {
            resultRelease.tryEmitEmpty();
        }
    }

    private static final class OwnershipHandoffBarrier {
        private final CountDownLatch transferStarted = new CountDownLatch(1);
        private final Sinks.One<Void> transferRelease = Sinks.one();

        private Mono<Void> awaitTransfer() {
            return Mono.defer(() -> {
                transferStarted.countDown();
                return transferRelease.asMono();
            });
        }

        private void awaitTransferStarted(Duration timeout) {
            await(transferStarted, timeout, "Redis Acquire 已写入且 Lease 正在交接所有权");
        }

        private void releaseTransfer() {
            transferRelease.tryEmitEmpty();
        }
    }

    private record GuardCommands(List<RateLimitCommand> rate, List<ConcurrencyCommand> concurrency) {
    }

    private record RawResponse(int status, String body) {
    }
}
