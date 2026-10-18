package com.paytech.gateway.auto.guard;

import java.net.InetAddress;
import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.paytech.gateway.guard.GatewayAuthenticatedGuardGlobalFilter;
import com.paytech.gateway.guard.concurrency.ConcurrencyCommand;
import com.paytech.gateway.guard.concurrency.ConcurrencyCommandFactory;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.ratelimit.RateLimitCommand;
import com.paytech.gateway.guard.ratelimit.RateLimitCommandFactory;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.security.identity.GatewayAuthenticatedIdentity;
import com.paytech.gateway.security.identity.OperationsIdentity;
import com.paytech.gateway.security.clientapi.ClientApiIdentity;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;
import com.paytech.gateway.web.GatewayProtocol;

import reactor.core.publisher.Mono;

/** 认证后 Guard 通过真实 Redis Adapter 消费 typed identity，并保持 Principal/Tenant Namespace 分离。 */
class ClientApiGuardFlowAutoTest extends GatewayAutoTestBase {
    private static final String CREDENTIAL = "830000017";
    private static final String TENANT = "810000042";
    private static final String FORGED = "caller-forged-identity-canary";

    @Autowired
    GatewayAuthenticatedGuardGlobalFilter guardFilter;

    @Autowired
    RateLimitCommandFactory rateCommandFactory;

    @Autowired
    ConcurrencyCommandFactory concurrencyCommandFactory;

    @Test
    void test_clientGuardUsesCredentialAndTenantFromTypedIdentityOnly() throws Exception {
        GatewayRuntimeRoute route = clientRoute();
        GuardContext context = new GuardContext(route.routeCode(), InetAddress.getByName("203.0.113.17"), CREDENTIAL,
            TENANT);
        MockServerWebExchange exchange = clientExchange(route, context.remoteAddress());
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY,
            new ClientApiIdentity(TENANT, "820000001", CREDENTIAL, Set.of("orders.write")));
        AtomicInteger ownerCalls = new AtomicInteger();

        guardFilter.filter(exchange, ignored -> Mono.fromRunnable(ownerCalls::incrementAndGet))
            .block(Duration.ofSeconds(5));

        Assertions.assertEquals(1, ownerCalls.get());
        assertOnlyExpectedPersistentKeys(route, context);
        Assertions.assertTrue(redisTestCleaner.guardKeys().stream()
            .noneMatch(key -> key.contains(FORGED)));
    }

    @Test
    void test_clientGuardFailsClosedForMissingInvalidOrTenantlessIdentity() throws Exception {
        GatewayRuntimeRoute route = clientRoute();
        for (Object identity : List.of(NoIdentity.INSTANCE, "not-a-typed-identity",
            new TestIdentity(null, Optional.of(TENANT), CREDENTIAL),
            new TestIdentity(" ", Optional.of(TENANT), CREDENTIAL),
            new TestIdentity(CREDENTIAL, Optional.empty(), CREDENTIAL),
            new TestIdentity(CREDENTIAL, Optional.of("\u2003"), CREDENTIAL),
            new TestIdentity(CREDENTIAL, null, CREDENTIAL))) {
            MockServerWebExchange exchange = clientExchange(route, InetAddress.getByName("203.0.113.17"));
            if (identity != NoIdentity.INSTANCE) {
                exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY, identity);
            }
            AtomicInteger ownerCalls = new AtomicInteger();

            guardFilter.filter(exchange, ignored -> Mono.fromRunnable(ownerCalls::incrementAndGet))
                .block(Duration.ofSeconds(5));

            Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
            Assertions.assertEquals(0, ownerCalls.get());
            Assertions.assertTrue(redisTestCleaner.guardKeys().isEmpty());
        }
    }

    @Test
    void test_operationsBootstrapIdentityStillUsesSubjectGuardWithoutTenant() throws Exception {
        GatewayRuntimeRoute route = operationsLogoutRoute();
        GuardContext context = new GuardContext(route.routeCode(), InetAddress.getLoopbackAddress(),
            "operations-subject", null);
        MockServerWebExchange exchange = exchange(route, context.remoteAddress());
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY,
            new OperationsIdentity("operations-subject", "operations-session", "BOOTSTRAP", null, null));
        AtomicInteger ownerCalls = new AtomicInteger();

        guardFilter.filter(exchange, ignored -> Mono.fromRunnable(ownerCalls::incrementAndGet))
            .block(Duration.ofSeconds(5));

        Assertions.assertEquals(1, ownerCalls.get());
        assertOnlyExpectedPersistentKeys(route, context);
    }

    @Test
    void test_operationsTenantContextStillUsesOnlyTheExistingSubjectDimension() throws Exception {
        GatewayRuntimeRoute route = operationsLogoutRoute();
        GuardContext context = new GuardContext(route.routeCode(), InetAddress.getLoopbackAddress(),
            "operations-subject", "internal-tenant");
        MockServerWebExchange exchange = exchange(route, context.remoteAddress());
        exchange.getAttributes().put(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY,
            new OperationsIdentity("operations-subject", "operations-session", "TENANT", "internal-tenant",
                "internal-member"));

        guardFilter.filter(exchange, ignored -> Mono.empty()).block(Duration.ofSeconds(5));

        assertOnlyExpectedPersistentKeys(route,
            new GuardContext(route.routeCode(), context.remoteAddress(), "operations-subject", null));
        Assertions.assertTrue(redisTestCleaner.guardKeys().stream()
            .noneMatch(key -> key.contains(":tenant:")));
    }

    @Test
    void test_authenticatedGuardOrderUsesTheTypedIdentitySlot() {
        Assertions.assertEquals(GatewayGlobalFilterOrder.AUTHENTICATED_GUARD, guardFilter.getOrder());
    }

    private void assertOnlyExpectedPersistentKeys(GatewayRuntimeRoute route, GuardContext context) {
        List<RateLimitCommand> rateCommands =
            rateCommandFactory.createAuthenticated(route.authenticatedGuard(), context);
        List<ConcurrencyCommand> concurrencyCommands =
            concurrencyCommandFactory.createAuthenticated(route.authenticatedGuard(), context);
        Set<String> expected = Stream.concat(rateCommands.stream().map(RateLimitCommand::key),
            concurrencyCommands.stream().map(command -> command.keys().metadataKey())).collect(Collectors.toSet());
        Set<String> actual = new HashSet<>(redisTestCleaner.guardKeys());

        Assertions.assertEquals(expected, actual);
        Assertions.assertTrue(concurrencyCommands.stream().map(ConcurrencyCommand::key)
            .noneMatch(actual::contains), "Guard 链完成后不得遗留活动 Lease Key");
    }

    private MockServerWebExchange clientExchange(GatewayRuntimeRoute route, InetAddress clientAddress) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest
            .post("/test/client-api/" + FORGED + "?tenantId=" + FORGED).header(HttpHeaders.AUTHORIZATION,
                "Bearer " + FORGED).header(GatewayProtocol.CLIENT_ID, FORGED)
            .header(GatewayProtocol.CREDENTIAL_ID, FORGED).body("{\"tenantId\":\"" + FORGED + "\"}"));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        exchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_ADDRESS, clientAddress);
        return exchange;
    }

    private MockServerWebExchange exchange(GatewayRuntimeRoute route, InetAddress clientAddress) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath()));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        exchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_ADDRESS, clientAddress);
        return exchange;
    }

    private GatewayRuntimeRoute clientRoute() {
        return new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.clientApiSnapshot()).catalog().all().getFirst();
    }

    private GatewayRuntimeRoute operationsLogoutRoute() {
        return new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.operationsSnapshot()).catalog().all().stream()
            .filter(route -> route.routeCode().equals("operations-auth-logout")).findFirst().orElseThrow();
    }

    private record TestIdentity(String principalKey, Optional<String> tenantKey, String accessSubject)
        implements GatewayAuthenticatedIdentity {
        @Override
        public String toString() {
            return "TestIdentity[principalPresent=" + (principalKey != null) + ", tenantPresent="
                + (tenantKey != null && tenantKey.isPresent()) + "]";
        }
    }

    private enum NoIdentity {
        INSTANCE
    }
}
