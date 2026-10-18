package com.paytech.gateway.web;

import java.util.Objects;

import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.RefreshCookieResponsePolicy;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;

import reactor.core.publisher.Mono;

/** 在 Netty 写出正文前按 Channel 验证并重建 Owner 响应 Header。 */
@Component
public final class GatewayResponseContractGlobalFilter implements GlobalFilter, Ordered {
    private final ConsoleRefreshCookiePolicy refreshCookiePolicy;
    private final ClientApiResponseHeaderPolicy clientApiPolicy = new ClientApiResponseHeaderPolicy();

    public GatewayResponseContractGlobalFilter(ConsoleRefreshCookiePolicy refreshCookiePolicy) {
        this.refreshCookiePolicy = Objects.requireNonNull(refreshCookiePolicy, "Refresh Cookie 响应策略不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange,
        org.springframework.cloud.gateway.filter.GatewayFilterChain chain) {
        if (GatewayExchangeAttributes.runtimeRoute(exchange).isEmpty()) {
            return chain.filter(exchange);
        }
        exchange.getAttributes().put(GatewayExchangeAttributes.UPSTREAM_DISPATCH_STARTED_AT_NANOS, System.nanoTime());
        return chain.filter(exchange).then(Mono.fromRunnable(() -> validateResponse(exchange)));
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.OPERATIONS_RESPONSE_CONTRACT;
    }

    private void validateResponse(ServerWebExchange exchange) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null || exchange.getResponse().isCommitted()) {
            return;
        }
        try {
            if (route.channel() == com.paytech.platform.support.api.enums.GatewayRouteChannel.CLIENT_API) {
                clientApiPolicy.validateAndRewrite(exchange.getResponse().getStatusCode(), exchange.getResponse().getHeaders());
            } else {
                refreshCookiePolicy.validateAndRewrite(exchange.getResponse().getHeaders(),
                    RefreshCookieResponsePolicy.valueOf(route.responseCookieMode()), route.channel());
            }
        } catch (InvalidUpstreamResponseException exception) {
            exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.UPSTREAM_INVALID_RESPONSE);
            clearUncommittedResponse(exchange);
            throw exception;
        }
    }

    private void clearUncommittedResponse(ServerWebExchange exchange) {
        exchange.getResponse().getHeaders().clear();
        exchange.getResponse().setStatusCode(null);
    }
}
