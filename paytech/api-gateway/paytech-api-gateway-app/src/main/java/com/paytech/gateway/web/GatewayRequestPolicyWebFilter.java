package com.paytech.gateway.web;

import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;

/** 所有已匹配 Route 共用 Query allowlist；Client 单值业务 Header 不允许静默选第一个值。 */
@Component
public final class GatewayRequestPolicyWebFilter implements WebFilter, Ordered {
    private final GatewayErrorWriter writer;

    public GatewayRequestPolicyWebFilter(GatewayErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        var route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null) {
            return chain.filter(exchange);
        }
        boolean validQueries = exchange.getRequest().getQueryParams().keySet().stream()
            .allMatch(route.queryParameterNames()::contains);
        boolean validHeaders = route.channel() != GatewayRouteChannel.CLIENT_API
            || route.forwardedRequestHeaders().stream()
                .allMatch(name -> exchange.getRequest().getHeaders().getOrEmpty(name).size() <= 1);
        if (!validQueries || !validHeaders) {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.INVALID_REQUEST);
            return writer.write(exchange, HttpStatus.BAD_REQUEST, GatewayErrorCode.GATEWAY_INVALID_REQUEST, null);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.REQUEST_POLICY;
    }
}
