package com.paytech.gateway.auto.web;

import com.paytech.gateway.routing.ClientRoutingProperties;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.ConsoleBoundaryWebFilter;
import com.paytech.gateway.web.TrustedTerminalResponseHeaders;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;

import reactor.core.publisher.Mono;

/** 同源读取可不带 Origin，但不得降低写操作或显式跨域请求的边界。 */
class OperationsReadOriginAutoTest {
    private static final String CONSOLE_ORIGIN = "https://operations-console.test";
    private static final OperationsRoutingProperties PROPERTIES =
        new OperationsRoutingProperties(CONSOLE_ORIGIN, Duration.ofSeconds(1));

    private static Stream<List<String>> invalidReadOrigins() {
        return Stream.of(List.of(""), List.of("null"), List.of("https://attacker.test"),
            List.of(CONSOLE_ORIGIN, CONSOLE_ORIGIN));
    }

    @Test
    void test_getWithoutOriginContinuesWithoutInventedCorsTrust() {
        MockServerWebExchange exchange = invoke("GET", List.of());

        Assertions.assertNull(exchange.getResponse().getStatusCode());
        Assertions.assertEquals("OPERATIONS_JWT",
            GatewayExchangeAttributes.runtimeRoute(exchange).orElseThrow().securityProfileCode(),
            "后续 JWT 安全配置仍读取同一受保护 Route");
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.TRUSTED_CONSOLE_ORIGIN));
        Assertions
            .assertFalse(exchange.getResponse().getHeaders().containsHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        Assertions.assertFalse(
            exchange.getResponse().getHeaders().containsHeader(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @Test
    void test_getWithExactOriginRetainsCorsContract() {
        MockServerWebExchange exchange = invoke("GET", List.of(CONSOLE_ORIGIN));

        Assertions.assertNull(exchange.getResponse().getStatusCode());
        Assertions.assertEquals(CONSOLE_ORIGIN,
            exchange.getResponse().getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        Assertions.assertEquals("true",
            exchange.getResponse().getHeaders().getFirst(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
    }

    @ParameterizedTest
    @MethodSource("invalidReadOrigins")
    void test_getWithAnExplicitInvalidOriginIsRejected(List<String> origins) {
        Assertions.assertEquals(HttpStatus.FORBIDDEN, invoke("GET", origins).getResponse().getStatusCode());
    }

    @ParameterizedTest
    @ValueSource(strings = {"POST", "PUT", "PATCH", "DELETE"})
    void test_writesStillRequireExactOrigin(String method) {
        Assertions.assertEquals(HttpStatus.FORBIDDEN, invoke(method, List.of()).getResponse().getStatusCode());
        Assertions.assertEquals(HttpStatus.FORBIDDEN,
            invoke(method, List.of("https://attacker.test")).getResponse().getStatusCode());
        Assertions.assertNull(invoke(method, List.of(CONSOLE_ORIGIN)).getResponse().getStatusCode());
    }

    private MockServerWebExchange invoke(String method, List<String> origins) {
        var request = MockServerHttpRequest.method(HttpMethod.valueOf(method), "/api/operations/iam/v1/accounts")
            .header(HttpHeaders.HOST, "attacker.example");
        origins.forEach(origin -> request.header(HttpHeaders.ORIGIN, origin));
        MockServerWebExchange exchange = MockServerWebExchange.from(request);
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route(method));
        AtomicInteger continuations = new AtomicInteger();
        var terminalHeaders = new TrustedTerminalResponseHeaders(PROPERTIES, new ClientRoutingProperties(false, null));
        exchange.getResponse().beforeCommit(() -> {
            terminalHeaders.protect(exchange);
            return Mono.empty();
        });

        new ConsoleBoundaryWebFilter(PROPERTIES, new GatewayErrorWriter(ServerCodecConfigurer.create()), new ClientRoutingProperties(false, null))
            .filter(exchange, accepted -> {
                continuations.incrementAndGet();
                return Mono.empty();
            }).block(Duration.ofSeconds(1));
        if (!exchange.getResponse().isCommitted()) {
            terminalHeaders.protect(exchange);
        }
        Assertions.assertEquals(exchange.getResponse().getStatusCode() == null ? 1 : 0, continuations.get());
        return exchange;
    }

    private GatewayRuntimeRoute route(String method) {
        var initial = GatewaySnapshotFixture.operationsSnapshot();
        var snapshot = new GatewayRouteSnapshot("operations-account-list", "OPERATIONS", method,
            "/api/operations/iam/v1/accounts", "/iam/v1/accounts", "operations-console-bff", "OPERATIONS_JWT",
            "HUMAN_PERMISSION", "ACCOUNT_READ", null, "DROP", "NONE", List.of("authorization"), List.of("origin"),
            List.of(), 0, 5_000, 10_000, initial.getRoutes().get(2).getGuardRules());
        return new GatewaySnapshotCompiler()
            .compile(new GatewayPublicSnapshotResponseBody(1, initial.getTargets(), List.of(snapshot))).catalog().all().getFirst();
    }
}
