package com.paytech.gateway.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.security.clientapi.ClientApiIdentity;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import reactor.core.publisher.Mono;

/** 空白 Header 重建之后只注入 Owner 所需的 Client 与 Credential ID。 */
@Component
public final class ClientApiDownstreamIdentityGlobalFilter implements GlobalFilter, Ordered {
    private final GatewayErrorWriter writer;

    public ClientApiDownstreamIdentityGlobalFilter(GatewayErrorWriter writer) {
        this.writer = writer;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        if (GatewayExchangeAttributes.runtimeRoute(exchange)
            .filter(route -> route.channel() == GatewayRouteChannel.CLIENT_API).isEmpty()) {
            return chain.filter(exchange);
        }
        var identity = GatewayExchangeAttributes.authenticatedIdentity(exchange).orElse(null);
        if (!(identity instanceof ClientApiIdentity client)) {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.DEPENDENCY_UNAVAILABLE);
            return writer.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE, null);
        }
        return chain.filter(exchange.mutate().request(request -> request.headers(headers -> {
            headers.set(GatewayProtocol.CLIENT_ID, client.clientId());
            headers.set(GatewayProtocol.CREDENTIAL_ID, client.credentialId());
        })).build());
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.TRUSTED_DOWNSTREAM_IDENTITY;
    }
}
