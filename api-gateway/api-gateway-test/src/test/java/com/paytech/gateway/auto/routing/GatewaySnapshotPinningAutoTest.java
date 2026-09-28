package com.paytech.gateway.auto.routing;

import com.paytech.gateway.routing.ClientRoutingProperties;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.paytech.gateway.guard.policy.ConcurrencyRule;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.policy.RateLimitRule;
import com.paytech.gateway.routing.DynamicRouteWebFilter;
import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.runtime.GatewayRuntimeCatalog;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.runtime.GatewayRuntimeTarget;
import com.paytech.gateway.routing.runtime.GatewayRuntimeSnapshot;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotStore;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.web.*;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

class GatewaySnapshotPinningAutoTest {

    @Test
    void pinsOneRouteForTheWholeRequestAcrossSnapshotReplacement() throws Exception {
        GatewayRuntimeRoute original = route("/auth/v1/original");
        GatewayRuntimeRoute replacement = route("/auth/v1/replacement");
        GatewaySnapshotStore store = storeWith(original);
        DynamicRouteWebFilter filter = filter(store);
        MockServerWebExchange exchange = publicExchange(HttpMethod.GET);

        filter.filter(exchange, current -> {
            GatewayRuntimeRoute pinned = current.getRequiredAttribute(GatewayExchangeAttributes.RUNTIME_ROUTE);
            store.replace(snapshot(replacement));
            assertSame(original, pinned);
            assertSame(original, current.getRequiredAttribute(GatewayExchangeAttributes.RUNTIME_ROUTE));
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertSame(original, exchange.getRequiredAttribute(GatewayExchangeAttributes.RUNTIME_ROUTE));
    }

    @Test
    void validEmptyCatalogReturnsLocal404WithoutCallingDownstream() throws Exception {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        store.replace(new GatewayRuntimeSnapshot(1, new GatewayRuntimeCatalog(List.of())));
        AtomicBoolean called = new AtomicBoolean();
        MockServerWebExchange exchange = publicExchange(HttpMethod.GET);

        filter(store).filter(exchange, ignored -> {
            called.set(true);
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertEquals(HttpStatus.NOT_FOUND, exchange.getResponse().getStatusCode());
        assertFalse(called.get());
    }

    @Test
    void pinsSafelyRenderedDownstreamPathForVariableRoute() throws Exception {
        GatewayRuntimeRoute route = route("/edge/clients/{clientId}", "/auth/clients/{clientId}");
        GatewaySnapshotStore store = storeWith(route);
        MockServerWebExchange exchange = publicExchange(HttpMethod.GET, "/edge/clients/%E2%82%AC%25value");

        filter(store).filter(exchange, current -> {
            assertEquals("/auth/clients/%E2%82%AC%25value",
                current.getRequiredAttribute(GatewayExchangeAttributes.DOWNSTREAM_PATH));
            return reactor.core.publisher.Mono.empty();
        }).block();
    }

    @Test
    void upstreamRewritePreservesTheCanonicalEncodedVariableExactlyOnce() throws Exception {
        GatewayRuntimeRoute route = route("/edge/clients/{clientId}", "/auth/clients/{clientId}");
        GatewaySnapshotStore store = storeWith(route);
        MockServerWebExchange exchange = publicExchange(HttpMethod.GET, "/edge/clients/%E2%82%AC%25value");
        UpstreamRequestGlobalFilter upstream = new UpstreamRequestGlobalFilter(
            new GatewayErrorWriter(ServerCodecConfigurer.create()), new ConsoleRefreshCookiePolicy(
                new OperationsRoutingProperties("https://operations-console.test", Duration.ofSeconds(1))));

        filter(store).filter(exchange, current -> {
            current.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "00000000-0000-0000-0000-000000000001");
            current.getAttributes().put(GatewayExchangeAttributes.ACTUAL_REQUEST_BODY_LENGTH, 0L);
            return upstream.filter(current, forwarded -> {
                assertEquals("/auth/clients/%E2%82%AC%25value", forwarded.getRequest().getURI().getRawPath());
                return reactor.core.publisher.Mono.empty();
            });
        }).block();
    }

    @Test
    void operationsPreflightUsesPinnedChannelAndCompletesLocallyOutsideLegacyPrefix() throws Exception {
        GatewayRuntimeRoute route = route("/edge/clients/{clientId}", "/auth/clients/{clientId}");
        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.method(HttpMethod.OPTIONS, "/edge/clients/client-1001")
                .header(HttpHeaders.ORIGIN, "https://operations-console.test")
                .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        AtomicBoolean called = new AtomicBoolean();
        ConsoleBoundaryWebFilter boundary = new ConsoleBoundaryWebFilter(
            new OperationsRoutingProperties("https://operations-console.test", Duration.ofSeconds(1)),
            new GatewayErrorWriter(ServerCodecConfigurer.create()), new ClientRoutingProperties(false, null));

        boundary.filter(exchange, ignored -> {
            called.set(true);
            return reactor.core.publisher.Mono.empty();
        }).block();

        assertEquals(HttpStatus.NO_CONTENT, exchange.getResponse().getStatusCode());
        assertFalse(called.get());
    }

    private DynamicRouteWebFilter filter(GatewaySnapshotStore store) {
        return new DynamicRouteWebFilter(store, new GatewayErrorWriter(ServerCodecConfigurer.create()));
    }

    private GatewaySnapshotStore storeWith(GatewayRuntimeRoute route) {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        store.replace(snapshot(route));
        return store;
    }

    private GatewayRuntimeSnapshot snapshot(GatewayRuntimeRoute route) {
        return new GatewayRuntimeSnapshot(1, new GatewayRuntimeCatalog(List.of(route)));
    }

    private MockServerWebExchange publicExchange(HttpMethod method) throws Exception {
        return publicExchange(method, OperationsTestPaths.SESSION);
    }

    private MockServerWebExchange publicExchange(HttpMethod method, String path) throws Exception {
        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.method(method, URI.create(path)));
        exchange.getAttributes().put(GatewayExchangeAttributes.CLIENT_ADDRESS, InetAddress.getByName("203.0.113.17"));
        exchange.getAttributes().put(GatewayExchangeAttributes.ENTRY_CHANNEL, GatewayRouteChannel.OPERATIONS);
        return exchange;
    }

    private GatewayRuntimeRoute route(String downstreamPath) {
        return route(OperationsTestPaths.SESSION, downstreamPath);
    }

    private GatewayRuntimeRoute route(String externalPath, String downstreamPath) {
        GuardProfile ingress =
            new GuardProfile(List.of(new RateLimitRule(GuardDimension.SHARED_ROUTE, 10, 10, Duration.ofMinutes(1))),
                List.of(new ConcurrencyRule(GuardDimension.SHARED_ROUTE, 2)), Duration.ofSeconds(30),
                Duration.ofSeconds(60));
        return new GatewayRuntimeRoute("operations-auth-session", GatewayRouteChannel.OPERATIONS, HttpMethod.GET, externalPath,
            downstreamPath, new GatewayRuntimeTarget("operations-console-bff", GatewayRouteChannel.OPERATIONS, "operations-console-bff"), "PUBLIC", "PUBLIC", null, null, "DROP", "NONE", Set.of(),
            Set.of("Origin"), Set.of(), 0, Duration.ofSeconds(5), Duration.ofSeconds(10), ingress, null);
    }
}
