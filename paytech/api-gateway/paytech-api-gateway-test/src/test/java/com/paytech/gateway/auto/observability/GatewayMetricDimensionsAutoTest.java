package com.paytech.gateway.auto.observability;

import com.paytech.gateway.routing.ClientRoutingProperties;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.paytech.gateway.guard.GatewayUpstreamTimeoutGlobalFilter;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.web.*;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;

class GatewayMetricDimensionsAutoTest {

    @Test
    void requestAndUpstreamLatencyUseRouteChannelAndProfileDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        GatewayRuntimeRoute route = loginRoute();

        metrics.recordRequest(route.routeCode(), route.channel(), route.securityProfileCode(),
            GatewayOutcome.UPSTREAM_RESPONSE, "2xx", TimeUnit.MILLISECONDS.toNanos(12));
        metrics.recordUpstreamLatency(route.routeCode(), route.channel(), route.securityProfileCode(),
            TimeUnit.MILLISECONDS.toNanos(8));

        Timer request = registry.get(GatewayMetrics.REQUEST_TIMER).tags("route", "operations-auth-login", "channel",
            "OPERATIONS", "profile", "PUBLIC", "outcome", "UPSTREAM_RESPONSE", "status_family", "2xx").timer();
        Timer upstream = registry.get(GatewayMetrics.UPSTREAM_LATENCY_TIMER)
            .tags("route", "operations-auth-login", "channel", "OPERATIONS", "profile", "PUBLIC").timer();
        assertEquals(1, request.count());
        assertEquals(1, upstream.count());
    }

    @Test
    void guardRejectionRecordsOnlyTheActualRejectingDimension() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        GatewayRuntimeRoute route = loginRoute();

        metrics.recordRateLimitRejection(route, GuardDimension.CLIENT_IP);
        metrics.recordConcurrencyLimitRejection(route, GuardDimension.SHARED_ROUTE);

        assertNull(findRejection(registry, "rate", "shared-route"));
        assertEquals(1D, rejection(registry, "rate", "client-ip"));
        assertEquals(1D, rejection(registry, "concurrency", "shared-route"));
        assertNull(findRejection(registry, "concurrency", "client-ip"));
    }

    @Test
    void clientTenantGuardRejectionUsesOnlyBoundedCatalogDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        GatewayRuntimeRoute route = new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.clientApiSnapshot()).catalog().all().getFirst();

        metrics.recordRateLimitRejection(route, GuardDimension.TENANT);

        Counter counter = registry.get(GatewayMetrics.GUARD_REJECTION_COUNTER)
            .tags("route", "client-api-test-post", "channel", "CLIENT_API", "profile", "CLIENT_API_CREDENTIAL",
                "limiter", "rate", "dimension", "tenant")
            .counter();
        assertEquals(1D, counter.count());
        assertEquals("client-api-test-post", counter.getId().getTag("route"));
        assertEquals("CLIENT_API", counter.getId().getTag("channel"));
        assertEquals("CLIENT_API_CREDENTIAL", counter.getId().getTag("profile"));
        assertEquals("tenant", counter.getId().getTag("dimension"));
    }

    @Test
    void requestWithoutAPinnedRouteUsesFiniteUnmatchedDimensions() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);

        metrics.recordRequest(null, null, null, GatewayOutcome.ROUTE_NOT_FOUND, "4xx", 1);

        Timer request = registry.get(GatewayMetrics.REQUEST_TIMER).tags("route", "UNMATCHED", "channel", "UNMATCHED",
            "profile", "UNMATCHED", "outcome", "ROUTE_NOT_FOUND", "status_family", "4xx").timer();
        assertEquals(1, request.count());
    }

    @Test
    void localMissingOriginBeforeDispatchDoesNotRecordUpstreamLatency() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        GatewayRuntimeRoute route = route("operations-auth-refresh");
        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.post(OperationsTestPaths.REFRESH).build());
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        GatewayUpstreamTimeoutGlobalFilter timeout =
            new GatewayUpstreamTimeoutGlobalFilter(new GatewayUpstreamFailureClassifier(), metrics);
        ConsoleBoundaryWebFilter boundary = new ConsoleBoundaryWebFilter(
            new OperationsRoutingProperties("https://operations-console.test", Duration.ofSeconds(1)),
            new GatewayErrorWriter(ServerCodecConfigurer.create()), new ClientRoutingProperties(false, null));

        timeout
            .filter(exchange,
                current -> boundary.filter(current, ignored -> Mono.error(new AssertionError("缺少 Origin 时不得进入上游分发"))))
            .block();

        assertEquals(HttpStatus.FORBIDDEN, exchange.getResponse().getStatusCode());
        assertNull(registry.find(GatewayMetrics.UPSTREAM_LATENCY_TIMER)
            .tags("route", "operations-auth-refresh", "channel", "OPERATIONS", "profile", "PUBLIC").timer());
    }

    @Test
    void reachingTheDispatchBoundaryRecordsUpstreamLatency() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewayMetrics metrics = new GatewayMetrics(registry);
        GatewayRuntimeRoute route = loginRoute();
        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.post(OperationsTestPaths.LOGIN).build());
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        GatewayUpstreamTimeoutGlobalFilter timeout =
            new GatewayUpstreamTimeoutGlobalFilter(new GatewayUpstreamFailureClassifier(), metrics);
        GatewayResponseContractGlobalFilter dispatchBoundary =
            new GatewayResponseContractGlobalFilter(cookiePolicy());

        timeout.filter(exchange, current -> dispatchBoundary.filter(current, ignored -> Mono.empty())).block();

        Timer upstream = registry.get(GatewayMetrics.UPSTREAM_LATENCY_TIMER)
            .tags("route", "operations-auth-login", "channel", "OPERATIONS", "profile", "PUBLIC").timer();
        assertEquals(1, upstream.count());
    }

    private double rejection(SimpleMeterRegistry registry, String limiter, String dimension) {
        return registry.get(GatewayMetrics.GUARD_REJECTION_COUNTER).tags("route", "operations-auth-login", "channel",
            "OPERATIONS", "profile", "PUBLIC", "limiter", limiter, "dimension", dimension).counter().count();
    }

    private Counter findRejection(SimpleMeterRegistry registry, String limiter, String dimension) {
        return registry.find(GatewayMetrics.GUARD_REJECTION_COUNTER).tags("route", "operations-auth-login", "channel",
            "OPERATIONS", "profile", "PUBLIC", "limiter", limiter, "dimension", dimension).counter();
    }

    private GatewayRuntimeRoute loginRoute() {
        return route("operations-auth-login");
    }

    private ConsoleRefreshCookiePolicy cookiePolicy() {
        return new ConsoleRefreshCookiePolicy(
            new OperationsRoutingProperties("https://operations-console.test", Duration.ofSeconds(1)));
    }

    private GatewayRuntimeRoute route(String routeCode) {
        return new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.operationsSnapshot()).catalog().all().stream()
            .filter(route -> routeCode.equals(route.routeCode())).findFirst().orElseThrow();
    }
}
