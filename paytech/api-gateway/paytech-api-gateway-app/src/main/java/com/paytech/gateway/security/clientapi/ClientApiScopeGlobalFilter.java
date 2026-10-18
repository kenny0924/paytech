package com.paytech.gateway.security.clientapi;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.web.GatewayErrorCode;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;

/** 在认证后 Guard 与 Body 之间执行大小写敏感、无通配或前缀扩展的 Scope 判断。 */
@Component
public final class ClientApiScopeGlobalFilter implements GlobalFilter, Ordered {
    private final GatewayErrorWriter writer;

    public ClientApiScopeGlobalFilter(GatewayErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        var route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null || route.channel() != GatewayRouteChannel.CLIENT_API) {
            return chain.filter(exchange);
        }
        var identity = GatewayExchangeAttributes.authenticatedIdentity(exchange).orElse(null);
        if (!(identity instanceof ClientApiIdentity client) || route.requiredScopeCode() == null) {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.DEPENDENCY_UNAVAILABLE);
            return writer.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE, null);
        }
        if (!client.effectiveScopes().contains(route.requiredScopeCode())) {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.AUTHORIZATION_DENIED);
            return writer.write(exchange, HttpStatus.FORBIDDEN, GatewayErrorCode.INSUFFICIENT_SCOPE, null);
        }
        return chain.filter(exchange);
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.CLIENT_API_SCOPE;
    }
}
