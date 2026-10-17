package com.paytech.gateway.auto.web;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;

import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.InvalidUpstreamResponseException;
import com.paytech.gateway.web.ConsoleRefreshCookiePolicy;
import com.paytech.gateway.web.GatewayResponseContractGlobalFilter;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;

import reactor.core.publisher.Mono;

/** 改密和 Bootstrap 完成响应不再读写 Refresh Cookie。 */
class OperationsAuthenticationCookieAutoTest {
    private static final String CLEAR_COOKIE =
        "__Secure-operations-refresh=; Path=/api/operations/auth/v1; " + "Max-Age=0; Secure; HttpOnly; SameSite=Strict";
    private final GatewayResponseContractGlobalFilter filter =
        new GatewayResponseContractGlobalFilter(new ConsoleRefreshCookiePolicy(
            new OperationsRoutingProperties("https://operations-console.test", Duration.ofSeconds(1))));

    @ParameterizedTest
    @ValueSource(strings = {"/auth/v1/password", "/auth/v1/bootstrap/administrator"})
    void test_newAuthenticationCommandsCompleteWithoutCookie(String path) {
        MockServerWebExchange exchange = exchange(path);

        filter.filter(exchange, forwarded -> {
            forwarded.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
            return Mono.empty();
        }).block(Duration.ofSeconds(1));

        Assertions.assertEquals(HttpStatus.NO_CONTENT, exchange.getResponse().getStatusCode());
        Assertions.assertFalse(exchange.getResponse().getHeaders().containsHeader(HttpHeaders.SET_COOKIE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"/auth/v1/password", "/auth/v1/bootstrap/administrator"})
    void test_newAuthenticationCommandsCannotClearOrIssueRefreshCookie(String path) {
        MockServerWebExchange exchange = exchange(path);

        Assertions.assertThrows(InvalidUpstreamResponseException.class, () -> filter.filter(exchange, forwarded -> {
            forwarded.getResponse().getHeaders().set(HttpHeaders.SET_COOKIE, CLEAR_COOKIE);
            return Mono.empty();
        }).block(Duration.ofSeconds(1)));
        Assertions.assertFalse(exchange.getResponse().getHeaders().containsHeader(HttpHeaders.SET_COOKIE));
    }

    private MockServerWebExchange exchange(String path) {
        var initial = GatewaySnapshotFixture.operationsSnapshot();
        var snapshot = new GatewayRouteSnapshot("operations-password-command", "OPERATIONS", "POST",
            "/api/operations" + path, path, "operations-console-bff", "OPERATIONS_JWT", "HUMAN_AUTHENTICATED", null,
            null, "DROP", "NONE", List.of("authorization", "content-type"), List.of("origin", "content-type"),
            List.of(), 16_384, 5_000, 10_000, initial.getRoutes().get(2).getGuardRules());
        var route = new GatewaySnapshotCompiler()
            .compile(new GatewayPublicSnapshotResponseBody(1, initial.getTargets(), List.of(snapshot))).catalog().all().getFirst();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath()));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        return exchange;
    }
}
