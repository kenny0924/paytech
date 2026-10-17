package com.paytech.gateway.web;

import java.nio.charset.CharsetEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

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
import com.paytech.gateway.routing.snapshot.GatewaySnapshotStore;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;

/** 在任何业务副作用前限制五个敏感/浏览器控制 Header 的解析后值预算。 */
@Component
public final class GatewayRequestHeaderLimitsWebFilter implements WebFilter, Ordered {
    private static final Map<String,
        Integer> VALUE_BUDGETS = Map.of(HttpHeaders.AUTHORIZATION.toLowerCase(Locale.ROOT), 8_192,
            HttpHeaders.COOKIE.toLowerCase(Locale.ROOT), 8_192, HttpHeaders.ORIGIN.toLowerCase(Locale.ROOT), 2_048,
            HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD.toLowerCase(Locale.ROOT), 2_048,
            HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS.toLowerCase(Locale.ROOT), 2_048);

    private final GatewayErrorWriter errorWriter;
    private final OperationsRoutingProperties routingProperties;
    private final GatewaySnapshotStore snapshotStore;
    private final ClientRoutingProperties clientProperties;

    public GatewayRequestHeaderLimitsWebFilter(GatewayErrorWriter errorWriter,
        OperationsRoutingProperties routingProperties, GatewaySnapshotStore snapshotStore, ClientRoutingProperties clientProperties) {
        this.errorWriter = Objects.requireNonNull(errorWriter, "Gateway error writer 不可为空");
        this.routingProperties = Objects.requireNonNull(routingProperties, "Operations 路由配置不可为空");
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "Gateway Snapshot Store 不可为空");
        this.clientProperties = Objects.requireNonNull(clientProperties, "Client 路由配置不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        GatewayRouteChannel channel = GatewayExchangeAttributes.entryChannel(exchange).orElse(null);
        if (channel == GatewayRouteChannel.CLIENT_API && !authorizationWithinBudget(exchange.getRequest().getHeaders())) {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.AUTHENTICATION_FAILED);
            return errorWriter.write(exchange, HttpStatus.UNAUTHORIZED, GatewayErrorCode.GATEWAY_UNAUTHORIZED, null);
        }
        if (withinBudgets(exchange.getRequest().getHeaders())) {
            return chain.filter(exchange);
        }
        deriveTrustedTerminalAttributes(exchange);
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.REQUEST_HEADER_FIELDS_TOO_LARGE);
        return errorWriter.write(exchange, HttpStatus.REQUEST_HEADER_FIELDS_TOO_LARGE,
            GatewayErrorCode.GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE, null);
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.REQUEST_HEADER_LIMIT;
    }

    private boolean withinBudgets(HttpHeaders headers) {
        Map<String, Long> totals = new java.util.HashMap<>();
        for (Map.Entry<String, java.util.List<String>> entry : headers.headerSet()) {
            String normalizedName = entry.getKey().toLowerCase(Locale.ROOT);
            Integer budget = VALUE_BUDGETS.get(normalizedName);
            if (budget == null || !addValues(totals, normalizedName, entry.getValue(), budget)) {
                if (budget == null) {
                    continue;
                }
                return false;
            }
        }
        return true;
    }

    private void deriveTrustedTerminalAttributes(ServerWebExchange exchange) {
        GatewayRouteChannel channel = GatewayExchangeAttributes.entryChannel(exchange).orElse(null);
        String trustedOrigin = channel == GatewayRouteChannel.OPERATIONS ? routingProperties.consoleOrigin()
            : channel == GatewayRouteChannel.CLIENT ? clientProperties.trustedOrigin() : null;
        if (trustedOrigin == null) {
            return;
        }
        TerminalRoute terminalRoute = resolveTerminalRoute(exchange);
        if (terminalRoute == null) {
            return;
        }
        GatewayRuntimeRoute route = terminalRoute.route();
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        exchange.getAttributes().put(GatewayExchangeAttributes.ROUTE_CODE, route.routeCode());
        if (!terminalRoute.originMayBeTrusted()) {
            return;
        }
        List<String> origins = exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.ORIGIN);
        if (origins.size() == 1 && trustedOrigin.equals(origins.getFirst())) {
            exchange.getAttributes().put(GatewayExchangeAttributes.TRUSTED_CONSOLE_ORIGIN,
                trustedOrigin);
        }
    }

    private boolean authorizationWithinBudget(HttpHeaders headers) {
        Map<String, Long> totals = new java.util.HashMap<>();
        for (Map.Entry<String, List<String>> entry : headers.headerSet()) {
            if (HttpHeaders.AUTHORIZATION.equalsIgnoreCase(entry.getKey())
                && !addValues(totals, HttpHeaders.AUTHORIZATION, entry.getValue(), 8_192)) {
                return false;
            }
        }
        return true;
    }

    private TerminalRoute resolveTerminalRoute(ServerWebExchange exchange) {
        GatewayRouteChannel channel = GatewayExchangeAttributes.entryChannel(exchange).orElse(null);
        String rawPath = exchange.getRequest().getURI().getRawPath();
        if (exchange.getRequest().getMethod() != HttpMethod.OPTIONS) {
            return snapshotStore.current()
                .flatMap(snapshot -> snapshot.catalog().find(channel,
                    exchange.getRequest().getMethod(), rawPath))
                .filter(route -> permitsQuery(route, exchange)).map(route -> new TerminalRoute(route, true))
                .orElse(null);
        }
        List<GatewayRuntimeRoute> candidates =
            snapshotStore.current().stream().flatMap(
                snapshot -> snapshot.catalog().matchesPath(channel, rawPath).stream())
                .map(match -> match.route()).filter(route -> permitsQuery(route, exchange)).toList();
        List<String> requestedMethods =
            exchange.getRequest().getHeaders().getOrEmpty(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD);
        if (requestedMethods.size() == 1) {
            List<GatewayRuntimeRoute> exact =
                candidates.stream().filter(route -> route.method().name().equals(requestedMethods.getFirst())).toList();
            if (exact.size() == 1) {
                return new TerminalRoute(exact.getFirst(), true);
            }
        }
        return candidates.size() == 1 ? new TerminalRoute(candidates.getFirst(), false) : null;
    }

    private boolean permitsQuery(GatewayRuntimeRoute route, ServerWebExchange exchange) {
        return exchange.getRequest().getQueryParams().keySet().stream().allMatch(route.queryParameterNames()::contains);
    }

    private boolean addValues(Map<String, Long> totals, String name, java.util.List<String> values, int budget) {
        long total = totals.getOrDefault(name, 0L);
        CharsetEncoder encoder = StandardCharsets.ISO_8859_1.newEncoder();
        for (String value : values) {
            if (value == null || !encoder.canEncode(value)) {
                return false;
            }
            try {
                total = Math.addExact(total, value.getBytes(StandardCharsets.ISO_8859_1).length);
            } catch (ArithmeticException exception) {
                return false;
            }
            if (total > budget) {
                return false;
            }
        }
        totals.put(name, total);
        return true;
    }

    private record TerminalRoute(GatewayRuntimeRoute route, boolean originMayBeTrusted) {
    }
}
