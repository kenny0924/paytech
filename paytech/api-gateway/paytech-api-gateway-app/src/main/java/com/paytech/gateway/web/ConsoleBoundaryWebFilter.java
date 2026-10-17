package com.paytech.gateway.web;

import java.util.List;
import java.util.Locale;

import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.ClientRoutingProperties;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;

import reactor.core.publisher.Mono;

/** 在路由前按 Console 通道执行精确 Origin 与本地 CORS 预检边界。 */
@Component
public class ConsoleBoundaryWebFilter implements WebFilter, Ordered {
    private final OperationsRoutingProperties properties;
    private final GatewayErrorWriter errorWriter;
    private final ClientRoutingProperties clientProperties;

    public ConsoleBoundaryWebFilter(OperationsRoutingProperties properties, GatewayErrorWriter errorWriter,
        ClientRoutingProperties clientProperties) {
        this.properties = properties;
        this.errorWriter = errorWriter;
        this.clientProperties = clientProperties;
        if (properties.consoleOrigin().equals(clientProperties.trustedOrigin())) {
            throw new IllegalArgumentException("CLIENT 与 OPERATIONS Console 必须使用不同 Origin");
        }
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null || origin(route) == null) {
            return chain.filter(exchange);
        }
        if (exchange.getRequest().getMethod() == HttpMethod.OPTIONS) {
            return handlePreflight(exchange, route);
        }
        setRouteCode(exchange, route);
        // 同源 GET 可省略 Origin；继续由原 JWT、Guard 和 BFF 权限链处理，不从 Host 推导信任。
        if (exchange.getRequest().getMethod() == HttpMethod.GET
            && exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.ORIGIN).isEmpty()) {
            return chain.filter(exchange);
        }
        if (!isExactOrigin(exchange.getRequest().getHeaders(), route)) {
            return writeForbidden(exchange);
        }
        markTrustedOrigin(exchange, route);
        addCorsResponseHeaders(exchange.getResponse().getHeaders(), route);
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.OPERATIONS_BOUNDARY;
    }

    private Mono<Void> handlePreflight(ServerWebExchange exchange, GatewayRuntimeRoute route) {
        HttpHeaders headers = exchange.getRequest().getHeaders();
        setRouteCode(exchange, route);
        if (!isExactOrigin(headers, route)
            || !route.method().name().equals(singleHeader(headers, HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD))
            || !hasOnlyAllowedRequestHeaders(route,
                combinedHeaderList(headers, HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS))) {
            return writeForbidden(exchange);
        }
        markTrustedOrigin(exchange, route);
        addCorsResponseHeaders(exchange.getResponse().getHeaders(), route);
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.UPSTREAM_RESPONSE);
        exchange.getResponse().setStatusCode(HttpStatus.NO_CONTENT);
        return exchange.getResponse().setComplete();
    }

    private boolean isExactOrigin(HttpHeaders headers, GatewayRuntimeRoute route) {
        return origin(route).equals(singleHeader(headers, HttpHeaders.ORIGIN));
    }

    private String origin(GatewayRuntimeRoute route) {
        return switch (route.channel()) {
            case OPERATIONS -> properties.consoleOrigin();
            case CLIENT -> clientProperties.trustedOrigin();
            default -> null;
        };
    }

    private boolean hasOnlyAllowedRequestHeaders(GatewayRuntimeRoute route, String requestHeaders) {
        if (requestHeaders == null) {
            return true;
        }
        return java.util.Arrays.stream(requestHeaders.split(",", -1))
            .map(value -> value.trim().toLowerCase(Locale.ROOT)).allMatch(value -> !value.isEmpty() && route
                .corsRequestHeaders().stream().map(header -> header.toLowerCase(Locale.ROOT)).anyMatch(value::equals));
    }

    private String singleHeader(HttpHeaders headers, String name) {
        List<String> values = headers.getOrEmpty(name);
        return values.size() == 1 ? values.getFirst() : null;
    }

    private String combinedHeaderList(HttpHeaders headers, String name) {
        List<String> values = headers.getOrEmpty(name);
        return values.isEmpty() ? null : String.join(",", values);
    }

    private void setRouteCode(ServerWebExchange exchange, GatewayRuntimeRoute route) {
        exchange.getAttributes().put(GatewayExchangeAttributes.ROUTE_CODE, route.routeCode());
    }

    private void markTrustedOrigin(ServerWebExchange exchange, GatewayRuntimeRoute route) {
        exchange.getAttributes().put(GatewayExchangeAttributes.TRUSTED_CONSOLE_ORIGIN, origin(route));
    }

    private void addCorsResponseHeaders(HttpHeaders headers, GatewayRuntimeRoute route) {
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, origin(route));
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, route.method().name() + ", OPTIONS");
        headers.set(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS,
            route.corsRequestHeaders().stream().sorted().collect(java.util.stream.Collectors.joining(", ")));
        headers.set(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS, GatewayProtocol.EXPOSE_HEADERS);
    }

    private Mono<Void> writeForbidden(ServerWebExchange exchange) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.AUTHORIZATION_DENIED);
        return errorWriter.write(exchange, HttpStatus.FORBIDDEN, GatewayErrorCode.GATEWAY_FORBIDDEN, null);
    }
}
