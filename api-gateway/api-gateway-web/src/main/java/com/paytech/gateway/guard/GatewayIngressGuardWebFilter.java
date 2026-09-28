package com.paytech.gateway.guard;

import java.net.InetAddress;
import java.util.Objects;

import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import com.paytech.gateway.guard.concurrency.ConcurrencyCommandFactory;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.ratelimit.RateLimitCommandFactory;
import com.paytech.gateway.observability.GatewayMetrics.GuardPhase;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;

import reactor.core.publisher.Mono;

/** 在 Spring Security、请求体与 Gateway 路由链前执行 Route/IP Guard。 */
@Component
public final class GatewayIngressGuardWebFilter implements WebFilter, Ordered {
    private final RateLimitCommandFactory rateCommandFactory;
    private final ConcurrencyCommandFactory concurrencyCommandFactory;
    private final GatewayGuardAdmission guardAdmission;

    public GatewayIngressGuardWebFilter(RateLimitCommandFactory rateCommandFactory,
        ConcurrencyCommandFactory concurrencyCommandFactory, GatewayGuardAdmission guardAdmission) {
        this.rateCommandFactory = Objects.requireNonNull(rateCommandFactory, "Rate 命令工厂不可为空");
        this.concurrencyCommandFactory = Objects.requireNonNull(concurrencyCommandFactory, "Concurrency 命令工厂不可为空");
        this.guardAdmission = Objects.requireNonNull(guardAdmission, "Gateway Guard Admission 不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null) {
            return chain.filter(exchange);
        }
        return Mono.fromCallable(() -> new GuardContext(route.routeCode(), clientAddress(exchange), null, null))
            .onErrorMap(GuardDependencyUnavailableException.class, GuardContextDependencyException::new)
            .flatMap(context -> guardAdmission.admit(exchange,
                rateCommandFactory.createIngress(route.ingressGuard(), context),
                concurrencyCommandFactory.createIngress(route.ingressGuard(), context),
                GatewayExchangeAttributes.INGRESS_GUARD_LIFECYCLE, GuardPhase.INGRESS, () -> chain.filter(exchange)))
            .onErrorResume(GuardContextDependencyException.class,
                ignored -> guardAdmission.writeDependencyUnavailable(exchange));
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.INGRESS_GUARD;
    }

    private InetAddress clientAddress(ServerWebExchange exchange) {
        Object value = exchange.getAttribute(GatewayExchangeAttributes.CLIENT_ADDRESS);
        if (!(value instanceof InetAddress address)) {
            throw new GuardDependencyUnavailableException("Gateway Guard 缺少可信 Client IP");
        }
        return address;
    }

    private static final class GuardContextDependencyException extends RuntimeException {
        private GuardContextDependencyException(GuardDependencyUnavailableException cause) {
            super(cause);
        }
    }
}
