package com.paytech.gateway.auto.guard;

import java.net.InetAddress;
import java.time.Duration;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import com.paytech.gateway.guard.policy.ConcurrencyRule;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.gateway.guard.policy.RateLimitRule;

/** Operations 路由 Guard Profile 约束自动化测试。 */
class GuardProfileContractAutoTest {
    @org.junit.jupiter.api.Test
    void test_tenantDimensionKeepsExistingSerializedOrdersStable() {
        Assertions.assertAll(
            () -> Assertions.assertEquals("shared-route", GuardDimension.SHARED_ROUTE.getCode()),
            () -> Assertions.assertEquals(1, GuardDimension.SHARED_ROUTE.sortOrder()),
            () -> Assertions.assertEquals("client-ip", GuardDimension.CLIENT_IP.getCode()),
            () -> Assertions.assertEquals(2, GuardDimension.CLIENT_IP.sortOrder()),
            () -> Assertions.assertEquals("authenticated-subject", GuardDimension.AUTHENTICATED_SUBJECT.getCode()),
            () -> Assertions.assertEquals(3, GuardDimension.AUTHENTICATED_SUBJECT.sortOrder()),
            () -> Assertions.assertEquals("tenant", GuardDimension.TENANT.getCode()),
            () -> Assertions.assertEquals(4, GuardDimension.TENANT.sortOrder()));
    }

    @org.junit.jupiter.api.Test
    void test_guardContextValidatesPrincipalAndTenantWithoutLeakingEither() {
        String principalCanary = "principal-secret-canary";
        String tenantCanary = "tenant-secret-canary";
        var context = new com.paytech.gateway.guard.policy.GuardContext("client-api-test-post",
            InetAddress.getLoopbackAddress(), principalCanary, tenantCanary);

        Assertions.assertAll(() -> Assertions.assertEquals(principalCanary, context.authenticatedPrincipal()),
            () -> Assertions.assertEquals(tenantCanary, context.tenantIdentity()),
            () -> Assertions.assertFalse(context.toString().contains(principalCanary)),
            () -> Assertions.assertFalse(context.toString().contains(tenantCanary)),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> new com.paytech.gateway.guard.policy.GuardContext("client-api-test-post",
                    InetAddress.getLoopbackAddress(), " ", tenantCanary)),
            () -> Assertions.assertThrows(IllegalArgumentException.class,
                () -> new com.paytech.gateway.guard.policy.GuardContext("client-api-test-post",
                    InetAddress.getLoopbackAddress(), principalCanary, " ")));
    }

    private static Stream<Arguments> invalidProfileInputs() {
        return Stream.of(
            Arguments.of(List.of(rateRule(), rateRule()), List.of(concurrencyRule()), Duration.ofSeconds(1),
                Duration.ofSeconds(1)),
            Arguments.of(List.of(rateRule()), List.of(concurrencyRule(), concurrencyRule()), Duration.ofSeconds(1),
                Duration.ofSeconds(1)),
            Arguments.of(List.of(rateRule()), List.of(concurrencyRule()), null, Duration.ofSeconds(1)),
            Arguments.of(List.of(rateRule()), List.of(concurrencyRule()), Duration.ZERO, Duration.ofSeconds(1)),
            Arguments.of(List.of(rateRule()), List.of(concurrencyRule()), Duration.ofSeconds(-1),
                Duration.ofSeconds(1)),
            Arguments.of(List.of(rateRule()), List.of(concurrencyRule()), Duration.ofSeconds(1), null),
            Arguments.of(List.of(rateRule()), List.of(concurrencyRule()), Duration.ofSeconds(1), Duration.ZERO),
            Arguments.of(List.of(rateRule()), List.of(concurrencyRule()), Duration.ofSeconds(1),
                Duration.ofSeconds(-1)),
            Arguments.of(List.of(rateRule()), List.of(concurrencyRule()), Duration.ofSeconds(2),
                Duration.ofSeconds(1)));
    }

    private static Stream<Arguments> invalidRateRules() {
        return Stream.of(Arguments.of(null, 1L, 1L, Duration.ofSeconds(1), NullPointerException.class),
            Arguments.of(GuardDimension.SHARED_ROUTE, 0L, 1L, Duration.ofSeconds(1), IllegalArgumentException.class),
            Arguments.of(GuardDimension.SHARED_ROUTE, 1L, 0L, Duration.ofSeconds(1), IllegalArgumentException.class),
            Arguments.of(GuardDimension.SHARED_ROUTE, 1L, 1L, null, IllegalArgumentException.class),
            Arguments.of(GuardDimension.SHARED_ROUTE, 1L, 1L, Duration.ZERO, IllegalArgumentException.class),
            Arguments.of(GuardDimension.SHARED_ROUTE, 1L, 1L, Duration.ofSeconds(-1), IllegalArgumentException.class));
    }

    private static Stream<Arguments> invalidConcurrencyRules() {
        return Stream.of(Arguments.of(null, 1, NullPointerException.class),
            Arguments.of(GuardDimension.SHARED_ROUTE, 0, IllegalArgumentException.class));
    }

    private static RateLimitRule rateRule() {
        return new RateLimitRule(GuardDimension.SHARED_ROUTE, 1, 1, Duration.ofSeconds(1));
    }

    private static ConcurrencyRule concurrencyRule() {
        return new ConcurrencyRule(GuardDimension.SHARED_ROUTE, 1);
    }

    @ParameterizedTest
    @MethodSource("invalidProfileInputs")
    void test_profileRejectsInvalidRouteGuardContract(List<RateLimitRule> rateLimits,
        List<ConcurrencyRule> concurrencyLimits, Duration leaseDuration, Duration stateTtl) {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new GuardProfile(rateLimits, concurrencyLimits, leaseDuration, stateTtl));
    }

    @ParameterizedTest
    @MethodSource("invalidRateRules")
    void test_rateLimitRuleRejectsInvalidCapacityAndPeriod(GuardDimension dimension, long capacity, long refillTokens,
        Duration refillPeriod, Class<? extends RuntimeException> expectedException) {
        Assertions.assertThrows(expectedException,
            () -> new RateLimitRule(dimension, capacity, refillTokens, refillPeriod));
    }

    @ParameterizedTest
    @MethodSource("invalidConcurrencyRules")
    void test_concurrencyRuleRejectsInvalidDimensionAndLimit(GuardDimension dimension, int limit,
        Class<? extends RuntimeException> expectedException) {
        Assertions.assertThrows(expectedException, () -> new ConcurrencyRule(dimension, limit));
    }
}
