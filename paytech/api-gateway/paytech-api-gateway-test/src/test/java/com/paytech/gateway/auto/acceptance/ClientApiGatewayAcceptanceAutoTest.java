package com.paytech.gateway.auto.acceptance;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import com.paytech.gateway.guard.concurrency.ConcurrencyCommand;
import com.paytech.gateway.guard.concurrency.ConcurrencyCommandFactory;
import com.paytech.gateway.guard.concurrency.ConcurrencyLease;
import com.paytech.gateway.guard.concurrency.ReactiveConcurrencyLimiter;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.test.ClientApiGatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.clientapi.ClientApiOwnerScenarioStore.Response;
import com.paytech.gateway.test.simulator.platform.PlatformCredentialValidationSimulator;
import com.paytech.gateway.web.GatewayProtocol;

import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

/** 真 Redis 与真实双 HTTP 边界证明 Guard 顺序、失败短路和所有终态租约释放。 */
class ClientApiGatewayAcceptanceAutoTest extends ClientApiGatewayAutoTestBase {
    @Autowired
    private ReactiveRedisConnectionFactory redis;
    @Autowired
    private ConcurrencyCommandFactory commands;
    @Autowired
    private ReactiveConcurrencyLimiter limiter;

    @AfterEach
    void assertNoLeasesBeforeBaseCleanup() {
        Awaitility.await().atMost(Duration.ofSeconds(3)).until(() -> heldLeases() == 0);
    }

    @Test
    void test_unknownQueryAndDuplicateBusinessHeaderStopBeforeAnyGuardOrPlatform() {
        assertProblem(client().post().uri(GatewaySnapshotFixture.CLIENT_API_PATH + "?tenantId=spoof")
            .exchange().expectBody().returnResult(), 400, "INVALID_REQUEST");
        assertProblem(authenticatedRequest().header("Idempotency-Key", "one", "two")
            .exchange().expectBody().returnResult(), 400, "INVALID_REQUEST");
        assertTrue(redisTestCleaner.guardKeys().isEmpty());
        assertEquals(0, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @Test
    void test_optionsDoesNotBorrowPostRouteOrCallDependencies() {
        var result = client().options().uri(GatewaySnapshotFixture.CLIENT_API_PATH)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "authorization")
            .header(HttpHeaders.ORIGIN, "https://operations-console.test").exchange().expectBody().returnResult();
        assertProblem(result, 404, "NOT_FOUND");
        assertFalse(result.getResponseHeaders().containsHeader(HttpHeaders.VARY));
        assertFalse(result.getResponseHeaders().containsHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        assertTrue(redisTestCleaner.guardKeys().isEmpty());
        assertEquals(0, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @ParameterizedTest
    @ValueSource(strings = {"client-ip", "authenticated-subject", "tenant"})
    void test_guardRejectionPrecedesPlatformOrScopeAtItsOwnPhase(String dimension) throws Exception {
        GatewayRuntimeRoute route = route();
        GuardContext context = context(route);
        boolean ingress = dimension.equals("client-ip");
        List<ConcurrencyCommand> phase = ingress ? commands.createIngress(route.ingressGuard(), context)
            : commands.createAuthenticated(route.authenticatedGuard(), context);
        ConcurrencyCommand command = phase.stream().filter(item -> item.key().contains(":" + dimension + ":")).findFirst().orElseThrow();
        List<ConcurrencyLease> held = new ArrayList<>();
        try {
            for (int index = 0; index < command.limit(); index++) {
                var decision = limiter.acquire(List.of(command)).block(Duration.ofSeconds(2));
                assertTrue(decision.allowed());
                held.add(decision.lease().orElseThrow());
            }
            if (!ingress) {
                // 合法身份没有 Scope：仍必须先被已满的主体/Tenant Guard 拒绝为 429。
                GatewayAutoTestRuntime.credentialSimulator().expectValid();
            }
            var result = authenticatedRequest().exchange().expectBody().returnResult();
            assertProblem(result, 429, "RATE_LIMIT_EXCEEDED");
            assertNotNull(result.getResponseHeaders().getFirst(HttpHeaders.RETRY_AFTER));
            assertEquals(ingress ? 0 : 1, GatewayAutoTestRuntime.credentialSimulator().count());
            assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
        } finally {
            held.forEach(lease -> limiter.release(lease).block(Duration.ofSeconds(2)));
        }
    }

    @Test
    void test_scopeFailureConsumesAuthenticatedTenantAndCredentialRateCapacity() {
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.read");
        assertProblem(authenticatedRequest().exchange().expectBody().returnResult(), 403, "INSUFFICIENT_SCOPE");
        List<String> keys = redisTestCleaner.guardKeys();
        assertTrue(keys.stream().anyMatch(key -> key.contains(":rate:authenticated-subject:")
            && key.endsWith(PlatformCredentialValidationSimulator.CREDENTIAL_ID)));
        assertTrue(keys.stream().anyMatch(key -> key.contains(":rate:tenant:")
            && key.endsWith(PlatformCredentialValidationSimulator.TENANT)));
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @Test
    void test_oversizeBodyIs413AfterAuthenticationButBeforeOwner() {
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write");
        assertProblem(authenticatedRequest().bodyValue("x".repeat(16_385)).exchange().expectBody().returnResult(),
            413, "REQUEST_TOO_LARGE");
        assertEquals(1, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void test_ownerTimeoutAndProtocolErrorReleaseBothGuardPhases(boolean timeout) {
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write");
        Response response = timeout ? Response.json(200, "{}").withDelay(Duration.ofSeconds(3))
            : Response.json(422, "{\"invalid\":true}");
        GatewayAutoTestRuntime.clientApiOwnerSimulator().scenarios().expect(HttpMethod.POST,
            GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH, response);
        assertProblem(authenticatedRequest().exchange().expectBody().returnResult(), timeout ? 504 : 502,
            timeout ? "DEPENDENCY_TIMEOUT" : "UPSTREAM_PROTOCOL_ERROR");
        assertEquals(1, GatewayAutoTestRuntime.credentialSimulator().count());
        assertEquals(1, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    @Test
    void test_clientCancellationReleasesAllFourHeldLeases() {
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write");
        GatewayAutoTestRuntime.clientApiOwnerSimulator().scenarios().expect(HttpMethod.POST,
            GatewaySnapshotFixture.CLIENT_API_DOWNSTREAM_PATH, Response.json(200, "{}").withDelay(Duration.ofSeconds(3)));
        var subscription = rawRequest().send(ByteBufFlux.fromString(Mono.just("{}")))
            .responseSingle((response, bytes) -> bytes.asString()).subscribe(ignored -> {}, error -> {});
        try {
            Awaitility.await().atMost(Duration.ofSeconds(2))
                .until(() -> GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count() == 1);
            assertEquals(4, heldLeases());
        } finally {
            subscription.dispose();
        }
    }

    @Test
    void test_bodyReceiveTimeoutIs408AndDoesNotReachOwner() {
        GatewayAutoTestRuntime.credentialSimulator().expectValid("orders.write");
        var result = rawRequest().send(ByteBufFlux.fromString(reactor.core.publisher.Flux.concat(
            Mono.just("{"), Mono.delay(Duration.ofSeconds(7)).map(ignored -> "}"))))
            .responseSingle((response, body) -> body.asString().map(text -> new BodyResponse(response.status().code(), text)))
            .block(Duration.ofSeconds(8));
        assertNotNull(result);
        assertEquals(408, result.status());
        assertTrue(result.body().contains("\"code\":\"REQUEST_TIMEOUT\""));
        assertEquals(0, GatewayAutoTestRuntime.clientApiOwnerSimulator().journal().count());
    }

    private HttpClient.RequestSender rawRequest() {
        return HttpClient.create().disableRetry(true).headers(headers -> {
            headers.set(GatewayProtocol.ENTRY_CHANNEL, "CLIENT_API");
            headers.set(GatewayProtocol.CLIENT_IP, "203.0.113.17");
            headers.set(HttpHeaders.AUTHORIZATION, "Bearer " + PlatformCredentialValidationSimulator.CREDENTIAL);
            headers.set(HttpHeaders.CONTENT_TYPE, "application/json");
        }).post().uri("http://127.0.0.1:" + port + GatewaySnapshotFixture.CLIENT_API_PATH);
    }

    private GatewayRuntimeRoute route() {
        return new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.clientApiSnapshot()).catalog().all().getFirst();
    }

    private GuardContext context(GatewayRuntimeRoute route) throws Exception {
        return new GuardContext(route.routeCode(), InetAddress.getByName("203.0.113.17"),
            PlatformCredentialValidationSimulator.CREDENTIAL_ID, PlatformCredentialValidationSimulator.TENANT);
    }

    private long heldLeases() {
        return redisTestCleaner.guardKeys().stream().filter(key -> key.endsWith(":leases"))
            .mapToLong(key -> withRedis(connection -> connection.zSetCommands()
                .zCard(StandardCharsets.UTF_8.encode(key)).defaultIfEmpty(0L))).sum();
    }

    private <T> T withRedis(Function<ReactiveRedisConnection, Mono<T>> action) {
        return Mono.usingWhen(Mono.fromSupplier(redis::getReactiveConnection), action,
            ReactiveRedisConnection::closeLater, (connection, error) -> connection.closeLater(),
            ReactiveRedisConnection::closeLater).block(Duration.ofSeconds(2));
    }

    private record BodyResponse(int status, String body) {
        @Override public String toString() { return "BodyResponse[status=" + status + "]"; }
    }
}
