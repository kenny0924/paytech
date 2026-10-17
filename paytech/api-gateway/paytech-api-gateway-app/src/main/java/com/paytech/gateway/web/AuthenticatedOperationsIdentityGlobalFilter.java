package com.paytech.gateway.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.security.identity.OperationsIdentity;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;

/** 在上游净化后，为受保护 Operations 路由重建已校验的 JWT 上下文。 */
@Component
public final class AuthenticatedOperationsIdentityGlobalFilter implements GlobalFilter, Ordered {
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (!isAuthenticatedOperationsRoute(exchange)) {
            return chain.filter(exchange);
        }
        OperationsIdentity identity = GatewayExchangeAttributes.authenticatedIdentity(exchange)
            .filter(OperationsIdentity.class::isInstance).map(OperationsIdentity.class::cast).orElse(null);
        return chain.filter(identity == null ? exchange : withTrustedIdentity(exchange, identity));
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.TRUSTED_DOWNSTREAM_IDENTITY;
    }

    private boolean isAuthenticatedOperationsRoute(ServerWebExchange exchange) {
        return GatewayExchangeAttributes.runtimeRoute(exchange).filter(route -> route.channel() == GatewayRouteChannel.OPERATIONS)
            .map(GatewayRuntimeRoute::securityProfileCode).filter("OPERATIONS_JWT"::equals).isPresent();
    }

    private ServerWebExchange withTrustedIdentity(ServerWebExchange exchange, OperationsIdentity identity) {
        ServerHttpRequest request = exchange.getRequest().mutate().headers(headers -> {
            headers.set(GatewayProtocol.ACTOR_SUBJECT, identity.subject());
            headers.set(GatewayProtocol.SESSION_ID, identity.sessionId());
            headers.set(GatewayProtocol.ACTOR_TYPE, "PLATFORM_USER");
            headers.set(GatewayProtocol.CHANNEL, "OPERATIONS");
            headers.set(GatewayProtocol.REALM, "OPERATIONS");
            headers.set(GatewayProtocol.CONTEXT_TYPE, identity.contextType());
            if (identity.isTenantContext()) {
                headers.set(GatewayProtocol.TENANT_ID, identity.tenantId());
                headers.set(GatewayProtocol.TENANT_TYPE, "INTERNAL");
                headers.set(GatewayProtocol.TENANT_MEMBER_ID, identity.tenantMemberId());
            }
        }).build();
        return exchange.mutate().request(request).build();
    }
}
