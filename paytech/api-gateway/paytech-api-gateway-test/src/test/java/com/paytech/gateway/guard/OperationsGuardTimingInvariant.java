package com.paytech.gateway.guard;

import java.time.Duration;
import java.util.List;
import java.util.Objects;

import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.routing.OperationsRouteDefinition;

/** 验证历史静态 Operations 路由夹具的两阶段 Guard 时序。 */
public final class OperationsGuardTimingInvariant {
    private static final Duration JWKS_RESPONSE_BUDGET = Duration.ofSeconds(3);
    private static final Duration REDIS_OPERATION_BUDGET = Duration.ofSeconds(2);
    private static final Duration COMPLETE_BFF_CHAIN_BUDGET = Duration.ofSeconds(10);
    private static final Duration REQUIRED_STATE_TTL = Duration.ofSeconds(60);

    public OperationsGuardTimingInvariant() {}

    public OperationsGuardTimingInvariant(List<OperationsRouteDefinition> routes) {
        List<OperationsRouteDefinition> immutableRoutes =
            List.copyOf(Objects.requireNonNull(routes, "Operations 路由不可为空"));
        if (immutableRoutes.isEmpty()) {
            throw new IllegalArgumentException("Operations 路由不可为空");
        }
        immutableRoutes.forEach(this::validateRoute);
    }

    public Duration postIngressAcquireBudget(OperationsRouteDefinition route) {
        OperationsRouteDefinition validatedRoute = Objects.requireNonNull(route, "Operations 路由不可为空");
        Duration budget =
            validatedRoute.bodyReceiveTimeout().plus(COMPLETE_BFF_CHAIN_BUDGET).plus(REDIS_OPERATION_BUDGET);
        if (validatedRoute.authenticatedGuard() == null) {
            return budget;
        }
        return budget.plus(JWKS_RESPONSE_BUDGET).plus(REDIS_OPERATION_BUDGET).plus(REDIS_OPERATION_BUDGET)
            .plus(REDIS_OPERATION_BUDGET);
    }

    public Duration postAuthenticatedAcquireBudget(OperationsRouteDefinition route) {
        OperationsRouteDefinition validatedRoute = Objects.requireNonNull(route, "Operations 路由不可为空");
        if (validatedRoute.authenticatedGuard() == null) {
            throw new IllegalArgumentException("Operations 路由未声明 Subject Guard");
        }
        return validatedRoute.bodyReceiveTimeout().plus(COMPLETE_BFF_CHAIN_BUDGET).plus(REDIS_OPERATION_BUDGET);
    }

    private void validateRoute(OperationsRouteDefinition route) {
        OperationsRouteDefinition validatedRoute = Objects.requireNonNull(route, "Operations 路由不可为空");
        validateProfile(validatedRoute.ingressGuard(), postIngressAcquireBudget(validatedRoute), "Ingress",
            validatedRoute);
        if (validatedRoute.authenticatedGuard() != null) {
            validateProfile(validatedRoute.authenticatedGuard(), postAuthenticatedAcquireBudget(validatedRoute),
                "Subject", validatedRoute);
        }
    }

    private void validateProfile(GuardProfile profile, Duration requiredBudget, String phase,
        OperationsRouteDefinition route) {
        if (!REQUIRED_STATE_TTL.equals(profile.stateTtl())) {
            throw new IllegalArgumentException(phase + " Guard 状态 TTL 必须精确为 60 秒, route=" + route.code().getCode());
        }
        if (profile.leaseDuration().compareTo(requiredBudget) <= 0) {
            throw new IllegalArgumentException(phase + " Guard Lease 必须严格大于最坏路径预算, route=" + route.code().getCode()
                + ", budgetSeconds=" + requiredBudget.toSeconds());
        }
    }
}
