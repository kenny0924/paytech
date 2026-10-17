package com.paytech.gateway.web;

import java.util.Objects;
import java.util.Optional;
import java.util.Set;

import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.ClientRoutingProperties;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;

/** 在终端提交前移除下游伪造值并重建 Gateway 拥有的响应 Header。 */
@Component
public final class TrustedTerminalResponseHeaders {
    private static final Set<String> OWNED_TERMINAL_HEADERS =
        Set.of(GatewayProtocol.REQUEST_ID, HttpHeaders.CACHE_CONTROL, HttpHeaders.PRAGMA, HttpHeaders.VARY,
            HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS,
            HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
            HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, HttpHeaders.ACCESS_CONTROL_MAX_AGE);

    private final OperationsRoutingProperties properties;
    private final ClientRoutingProperties clientProperties;

    public TrustedTerminalResponseHeaders(OperationsRoutingProperties properties, ClientRoutingProperties clientProperties) {
        this.properties = Objects.requireNonNull(properties, "Operations 路由配置不可为空");
        this.clientProperties = Objects.requireNonNull(clientProperties, "Client 路由配置不可为空");
    }

    public void protect(ServerWebExchange exchange) {
        HttpHeaders headers = exchange.getResponse().getHeaders();
        OWNED_TERMINAL_HEADERS.forEach(headers::remove);
        trustedRequestId(exchange).ifPresent(requestId -> headers.set(GatewayProtocol.REQUEST_ID, requestId));
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null) {
            return;
        }
        headers.set(HttpHeaders.CACHE_CONTROL, "no-store");
        headers.set(HttpHeaders.PRAGMA, "no-cache");
        String origin = switch (route.channel()) {
            case OPERATIONS -> properties.consoleOrigin();
            case CLIENT -> clientProperties.trustedOrigin();
            default -> null;
        };
        if (origin == null) {
            headers.setDate(System.currentTimeMillis());
            return;
        }
        headers.set(HttpHeaders.VARY, HttpHeaders.ORIGIN);
        if (!origin
            .equals(exchange.getAttribute(GatewayExchangeAttributes.TRUSTED_CONSOLE_ORIGIN))) {
            return;
        }
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin);
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, route.method().name() + ", OPTIONS");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
            route.corsRequestHeaders().stream().sorted().collect(java.util.stream.Collectors.joining(", ")));
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, GatewayProtocol.EXPOSE_HEADERS);
    }

    /** Returns the Gateway-created request ID and rejects a corrupt present value without exposing it. */
    public Optional<String> trustedRequestId(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(GatewayExchangeAttributes.REQUEST_ID);
        if (value == null) {
            return Optional.empty();
        }
        if (!(value instanceof String requestId) || !ApiProblemProfile.isValidRequestId(requestId)) {
            throw new IllegalStateException("Gateway 可信 Request ID 不变量被破坏");
        }
        return Optional.of(requestId);
    }

}
