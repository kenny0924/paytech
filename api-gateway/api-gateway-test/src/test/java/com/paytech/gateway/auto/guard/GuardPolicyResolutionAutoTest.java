package com.paytech.gateway.auto.guard;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.gateway.guard.policy.ConcurrencyRule;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.policy.RateLimitRule;
import com.paytech.gateway.routing.OperationsRouteCatalog;
import com.paytech.gateway.routing.OperationsRouteCode;
import com.paytech.gateway.routing.OperationsRouteDefinition;

/** Operations 路由目录中静态 Guard Profile 的业务值测试。 */
class GuardPolicyResolutionAutoTest {
    private static final Duration ONE_MINUTE = Duration.ofMinutes(1);
    private static final Duration LEASE_DURATION = Duration.ofSeconds(30);
    private static final Duration STATE_TTL = Duration.ofSeconds(60);

    private static Map<OperationsRouteCode, OperationsRouteDefinition> routes() {
        return new OperationsRouteCatalog(Duration.ofSeconds(10)).all().stream()
            .collect(Collectors.toUnmodifiableMap(OperationsRouteDefinition::code, Function.identity()));
    }

    private static void assertProfile(GuardProfile profile, List<RateLimitRule> rateLimits,
        List<ConcurrencyRule> concurrencyLimits) {
        Assertions.assertAll(() -> Assertions.assertEquals(rateLimits, profile.rateLimits()),
            () -> Assertions.assertEquals(concurrencyLimits, profile.concurrencyLimits()),
            () -> Assertions.assertEquals(LEASE_DURATION, profile.leaseDuration()),
            () -> Assertions.assertEquals(STATE_TTL, profile.stateTtl()));
    }

    private static RateLimitRule rate(GuardDimension dimension, long capacity, long refillTokens) {
        return new RateLimitRule(dimension, capacity, refillTokens, ONE_MINUTE);
    }

    private static ConcurrencyRule concurrency(GuardDimension dimension, int limit) {
        return new ConcurrencyRule(dimension, limit);
    }

    @Test
    void test_catalogFreezesExactIngressProfilesForLoginRefreshAndLogout() {
        Map<OperationsRouteCode, OperationsRouteDefinition> routes = routes();

        Assertions.assertAll(
            () -> assertProfile(routes.get(OperationsRouteCode.AUTH_LOGIN).ingressGuard(),
                List.of(rate(GuardDimension.SHARED_ROUTE, 300, 300), rate(GuardDimension.CLIENT_IP, 5, 5)),
                List.of(concurrency(GuardDimension.SHARED_ROUTE, 20), concurrency(GuardDimension.CLIENT_IP, 2))),
            () -> assertProfile(routes.get(OperationsRouteCode.AUTH_REFRESH).ingressGuard(),
                List.of(rate(GuardDimension.SHARED_ROUTE, 600, 600), rate(GuardDimension.CLIENT_IP, 10, 30)),
                List.of(concurrency(GuardDimension.SHARED_ROUTE, 50), concurrency(GuardDimension.CLIENT_IP, 3))),
            () -> assertProfile(routes.get(OperationsRouteCode.AUTH_LOGOUT).ingressGuard(),
                List.of(rate(GuardDimension.SHARED_ROUTE, 300, 300), rate(GuardDimension.CLIENT_IP, 10, 30)),
                List.of(concurrency(GuardDimension.SHARED_ROUTE, 50), concurrency(GuardDimension.CLIENT_IP, 3))));
    }

    @Test
    void test_catalogKeepsLoginAndRefreshAnonymousWhileLogoutRetainsSubjectProtection() {
        Map<OperationsRouteCode, OperationsRouteDefinition> routes = routes();

        Assertions.assertAll(
            () -> Assertions.assertNull(routes.get(OperationsRouteCode.AUTH_LOGIN).authenticatedGuard()),
            () -> Assertions.assertNull(routes.get(OperationsRouteCode.AUTH_REFRESH).authenticatedGuard()),
            () -> assertProfile(routes.get(OperationsRouteCode.AUTH_LOGOUT).authenticatedGuard(),
                List.of(rate(GuardDimension.AUTHENTICATED_SUBJECT, 10, 10)),
                List.of(concurrency(GuardDimension.AUTHENTICATED_SUBJECT, 2))));
    }
}
