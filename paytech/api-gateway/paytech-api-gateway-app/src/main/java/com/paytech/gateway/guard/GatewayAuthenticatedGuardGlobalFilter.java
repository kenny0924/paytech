package com.paytech.gateway.guard;

import java.net.InetAddress;
import java.util.Objects;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.guard.concurrency.ConcurrencyCommandFactory;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.ratelimit.RateLimitCommandFactory;
import com.paytech.gateway.observability.GatewayMetrics.GuardPhase;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.security.identity.GatewayAuthenticatedIdentity;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;

import reactor.core.publisher.Mono;

/** 在认证后、请求体聚合前执行 Principal/Tenant Guard。 */
@Component
public final class GatewayAuthenticatedGuardGlobalFilter implements GlobalFilter, Ordered {
    private final RateLimitCommandFactory rateCommandFactory;
    private final ConcurrencyCommandFactory concurrencyCommandFactory;
    private final GatewayGuardAdmission guardAdmission;

    public GatewayAuthenticatedGuardGlobalFilter(RateLimitCommandFactory rateCommandFactory,
        ConcurrencyCommandFactory concurrencyCommandFactory, GatewayGuardAdmission guardAdmission) {
        this.rateCommandFactory = Objects.requireNonNull(rateCommandFactory, "Rate 命令工厂不可为空");
        this.concurrencyCommandFactory = Objects.requireNonNull(concurrencyCommandFactory, "Concurrency 命令工厂不可为空");
        this.guardAdmission = Objects.requireNonNull(guardAdmission, "Gateway Guard Admission 不可为空");
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        GatewayRuntimeRoute route = GatewayExchangeAttributes.runtimeRoute(exchange).orElse(null);
        if (route == null) {
            return chain.filter(exchange);
        }
        if (route.authenticatedGuard() == null) {
            return chain.filter(exchange);
        }
        return Mono.defer(() -> authenticatedContext(exchange, route))
            .onErrorMap(GuardDependencyUnavailableException.class, GuardContextDependencyException::new)
            .flatMap(context -> guardAdmission.admit(exchange,
                rateCommandFactory.createAuthenticated(route.authenticatedGuard(), context),
                concurrencyCommandFactory.createAuthenticated(route.authenticatedGuard(), context),
                GatewayExchangeAttributes.AUTHENTICATED_GUARD_LIFECYCLE, GuardPhase.AUTHENTICATED_SUBJECT,
                () -> chain.filter(exchange)))
            .onErrorResume(GuardContextDependencyException.class,
                ignored -> guardAdmission.writeDependencyUnavailable(exchange));
    }

    @Override
    public int getOrder() {
        return GatewayGlobalFilterOrder.AUTHENTICATED_GUARD;
    }

    private Mono<GuardContext> authenticatedContext(ServerWebExchange exchange, GatewayRuntimeRoute route) {
        return Mono.fromCallable(() -> {
            InetAddress clientAddress = clientAddress(exchange);
            GatewayAuthenticatedIdentity identity = GatewayExchangeAttributes.authenticatedIdentity(exchange)
                .orElseThrow(() -> new GuardDependencyUnavailableException("受保护路由缺少已验证 typed identity"));
            if (route.channel() == com.paytech.platform.support.api.enums.GatewayRouteChannel.CLIENT_API
                && !(identity instanceof com.paytech.gateway.security.clientapi.ClientApiIdentity)) {
                throw new GuardDependencyUnavailableException("Client API 路由缺少已验证 Client identity");
            }
            try {
                String principal = identity.principalKey();
                java.util.Optional<String> tenant = Objects.requireNonNull(identity.tenantKey());
                GuardContext context = new GuardContext(route.routeCode(), clientAddress, principal,
                    tenant.orElse(null));
                if (!context.hasAuthenticatedSubject()) {
                    throw new GuardDependencyUnavailableException("受保护路由缺少可信 Principal identity");
                }
                if (requiresTenant(route) && !context.hasTenantIdentity()) {
                    throw new GuardDependencyUnavailableException("受保护路由缺少可信 Tenant identity");
                }
                return context;
            } catch (GuardDependencyUnavailableException exception) {
                throw exception;
            } catch (RuntimeException exception) {
                throw new GuardDependencyUnavailableException("受保护路由 typed identity 无效");
            }
        });
    }

    private boolean requiresTenant(GatewayRuntimeRoute route) {
        return route.authenticatedGuard().rateLimits().stream()
            .anyMatch(rule -> rule.dimension() == com.paytech.gateway.guard.policy.GuardDimension.TENANT)
            || route.authenticatedGuard().concurrencyLimits().stream()
                .anyMatch(rule -> rule.dimension() == com.paytech.gateway.guard.policy.GuardDimension.TENANT);
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
