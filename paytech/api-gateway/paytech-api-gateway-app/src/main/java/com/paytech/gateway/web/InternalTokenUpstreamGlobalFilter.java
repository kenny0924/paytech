package com.paytech.gateway.web;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.foundation.internalauth.api.InternalTokenUnavailableException;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.security.GatewayInternalTokenPolicy;

import reactor.core.publisher.Mono;

/**
 * 转发到内部服务的请求在可信 Header 重建之后附加内部 Token（D26）。
 * <li>Netty 转发不经过 WebClient，因此不能挂 {@code InternalTokenExchangeFilterFunction}；Token 来源与 Purpose 与其一致。</li>
 * <li>公网 {@code Authorization} 已在重建时清除，Snapshot 校验也禁止转发该 Header，这里写入的是唯一取值。</li>
 * <li>取不到 Token 时不转发，返回 503。</li>
 */
@Component
public final class InternalTokenUpstreamGlobalFilter implements GlobalFilter, Ordered {
    private final GatewayErrorWriter writer;
    private final GatewayInternalTokenPolicy policy;

    public InternalTokenUpstreamGlobalFilter(GatewayErrorWriter writer, GatewayInternalTokenPolicy policy) {
        this.writer = writer;
        this.policy = policy;
    }

    /** 有运行时路由且策略开启时，以路由目标的服务发现名取 Token 并写入请求头。 */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null || !policy.enabled()) {
            return chain.filter(exchange);
        }
        return policy.token(route.target().discoveryServiceName())
            .onErrorResume(InternalTokenUnavailableException.class, unavailable -> Mono.empty())
            .flatMap(token -> chain.filter(withToken(exchange, token)).thenReturn(Boolean.TRUE))
            .switchIfEmpty(Mono.defer(() -> unavailable(exchange)).thenReturn(Boolean.TRUE)).then();
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.INTERNAL_TOKEN;
    }

    /** 以 Token 覆盖下游请求的 Authorization。 */
    private ServerWebExchange withToken(ServerWebExchange exchange, String token) {
        return exchange.mutate().request(request -> request.headers(headers -> headers.setBearerAuth(token))).build();
    }

    /** Token 不可用：记录依赖不可用并返回 503，不转发。 */
    private Mono<Void> unavailable(ServerWebExchange exchange) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.DEPENDENCY_UNAVAILABLE);
        return writer.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE,
            null);
    }
}
