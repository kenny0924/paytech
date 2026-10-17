package com.paytech.gateway.guard;

import java.util.Objects;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;
import com.paytech.gateway.web.GatewayUpstreamFailureClassifier;
import com.paytech.gateway.web.GatewayUpstreamFailureClassifier.Failure;
import com.paytech.gateway.web.GatewayUpstreamTimeoutException;

import reactor.core.publisher.Mono;

/** 在请求体完成后为已编译路由包裹完整上游响应链超时与上游耗时。 */
@Component
public final class GatewayUpstreamTimeoutGlobalFilter implements GlobalFilter, Ordered {
    private final GatewayUpstreamFailureClassifier failureClassifier;
    private final GatewayMetrics gatewayMetrics;

    public GatewayUpstreamTimeoutGlobalFilter(GatewayUpstreamFailureClassifier failureClassifier,
        GatewayMetrics gatewayMetrics) {
        this.failureClassifier = Objects.requireNonNull(failureClassifier, "上游失败分类器不可为空");
        this.gatewayMetrics = Objects.requireNonNull(gatewayMetrics, "Gateway Metrics 不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null) {
            return chain.filter(exchange);
        }
        return chain.filter(exchange).onErrorMap(this::isFrameworkUpstreamTimeout, ignored -> upstreamTimeout(exchange))
            .timeout(route.responseTimeout(), Mono.defer(() -> Mono.error(upstreamTimeout(exchange))))
            .doFinally(ignored -> recordUpstreamLatency(exchange, route));
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.UPSTREAM_TIMEOUT;
    }

    private boolean isFrameworkUpstreamTimeout(Throwable throwable) {
        return failureClassifier.classify(throwable).filter(failure -> failure == Failure.UPSTREAM_TIMEOUT).isPresent();
    }

    private GatewayUpstreamTimeoutException upstreamTimeout(ServerWebExchange exchange) {
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.UPSTREAM_TIMEOUT);
        return new GatewayUpstreamTimeoutException();
    }

    private void recordUpstreamLatency(ServerWebExchange exchange, GatewayRuntimeRoute route) {
        Object value = exchange.getAttribute(GatewayExchangeAttributes.UPSTREAM_DISPATCH_STARTED_AT_NANOS);
        if (!(value instanceof Long startedAtNanos)) {
            return;
        }
        gatewayMetrics.recordUpstreamLatency(route.routeCode(), route.channel(), route.securityProfileCode(),
            Math.max(0, System.nanoTime() - startedAtNanos));
    }
}
