package com.paytech.gateway.auto.security;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.foundation.internalauth.api.InternalAuthMode;
import com.paytech.foundation.internalauth.api.InternalTokenSupplier;
import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.runtime.GatewayRuntimeTarget;
import com.paytech.gateway.security.GatewayInternalTokenPolicy;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.InternalTokenUpstreamGlobalFilter;
import com.paytech.platform.support.api.enums.GatewayIdempotencyMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;

/**
 * 转发链路附加内部 Token 的过滤器分支：无路由直通、覆盖 Authorization、Token 不可用时 503 且不转发。
 *
 * @see InternalTokenUpstreamGlobalFilter#filter(ServerWebExchange, GatewayFilterChain)
 */
class InternalTokenUpstreamFilterAutoTest {

    private static final String REQUEST_ID = "internal-token-filter-test";

    private final List<String> requestedTargets = new ArrayList<>();

    private final List<ServerWebExchange> forwarded = new ArrayList<>();

    @Test
    void test_noDataFound_withoutRuntimeRouteIsPassThrough() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/local"));

        filter((target, purpose) -> "token-" + target).filter(exchange, this::forward).block(Duration.ofSeconds(1));

        Assertions.assertEquals(1, forwarded.size());
        Assertions.assertNull(forwarded.getFirst().getRequest().getHeaders().getFirst(HttpHeaders.AUTHORIZATION));
        Assertions.assertEquals(List.of(), requestedTargets);
    }

    @Test
    void test_normalFlow_tokenReplacesAuthorizationForRouteTarget() {
        MockServerWebExchange exchange = routedExchange();

        filter((target, purpose) -> "token-" + target).filter(exchange, this::forward).block(Duration.ofSeconds(1));

        Assertions.assertEquals(1, forwarded.size());
        Assertions.assertEquals(List.of("Bearer token-" + GatewaySnapshotFixture.OPERATIONS_TARGET),
            forwarded.getFirst().getRequest().getHeaders().get(HttpHeaders.AUTHORIZATION));
        Assertions.assertEquals(List.of(GatewaySnapshotFixture.OPERATIONS_TARGET), requestedTargets);
    }

    @Test
    void test_dependencyUnavailable_tokenFailureReturns503WithoutForwarding() {
        MockServerWebExchange exchange = routedExchange();

        filter((target, purpose) -> {
            throw new InternalTokenUnavailableException(new IllegalStateException("Platform 签发不可用"));
        }).filter(exchange, this::forward).block(Duration.ofSeconds(1));

        Assertions.assertEquals(List.of(), forwarded);
        Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        Assertions.assertEquals(GatewayOutcome.DEPENDENCY_UNAVAILABLE,
            exchange.getAttribute(GatewayExchangeAttributes.OUTCOME));
    }

    private InternalTokenUpstreamGlobalFilter filter(InternalTokenSupplier supplier) {
        InternalTokenSupplier recording = (target, purpose) -> {
            requestedTargets.add(target);
            return supplier.token(target, purpose);
        };
        return new InternalTokenUpstreamGlobalFilter(new GatewayErrorWriter(ServerCodecConfigurer.create()),
            new GatewayInternalTokenPolicy(InternalAuthMode.REQUIRED, recording));
    }

    private Mono<Void> forward(ServerWebExchange exchange) {
        forwarded.add(exchange);
        return Mono.empty();
    }

    private static MockServerWebExchange routedExchange() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post("/auth/v1/login")
            .header(HttpHeaders.AUTHORIZATION, "Bearer public-caller-value"));
        String target = GatewaySnapshotFixture.OPERATIONS_TARGET;
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE,
            new GatewayRuntimeRoute("internal-token-test", GatewayRouteChannel.OPERATIONS, HttpMethod.POST,
                "/api/operations/auth/v1/login", "/auth/v1/login",
                new GatewayRuntimeTarget(target, GatewayRouteChannel.OPERATIONS, target), "PUBLIC", "PUBLIC", null,
                null, "DROP", "NONE", Set.of(), Set.of(), Set.of(), 0, Duration.ofSeconds(1), Duration.ofSeconds(1),
                null, null, GatewayIdempotencyMode.NONE));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, REQUEST_ID);
        return exchange;
    }
}
