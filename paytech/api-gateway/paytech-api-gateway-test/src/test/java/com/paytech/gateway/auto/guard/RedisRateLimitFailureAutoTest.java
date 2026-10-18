package com.paytech.gateway.auto.guard;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.gateway.guard.GuardDependencyUnavailableException;
import com.paytech.gateway.guard.concurrency.ConcurrencyDecision;
import com.paytech.gateway.guard.concurrency.RedisReactiveConcurrencyLimiter;
import com.paytech.gateway.guard.policy.GuardDimension;
import com.paytech.gateway.guard.ratelimit.RateLimitDecision;
import com.paytech.gateway.guard.ratelimit.RedisReactiveRateLimiter;

/** Redis 响应的纯解析边界；实际 Redis 不可用由 RedisGuardUnavailableAutoTest 验证完整 HTTP 链。 */
class RedisRateLimitFailureAutoTest {
    @Test
    void test_malformedRedisTokenBucketReplyFailsClosed() throws Exception {
        Method toDecision = RedisReactiveRateLimiter.class.getDeclaredMethod("toDecision", List.class);
        toDecision.setAccessible(true);

        for (List<Long> malformedReply : List.of(List.of(1L, 1L, 0L), List.of(1L, 0L, 2L), List.of(0L, 0L, 1L),
            List.of(0L, 1L, 0L), List.of(0L, 1L, 5L), List.of(0L, 9_007_199_254_740_992L, 1L))) {
            InvocationTargetException exception = Assertions.assertThrows(InvocationTargetException.class,
                () -> toDecision.invoke(null, malformedReply), malformedReply.toString());
            Assertions.assertInstanceOf(GuardDependencyUnavailableException.class, exception.getCause());
        }
    }

    @Test
    void test_tokenBucketReplyMapsTheActualRejectingDimension() throws Exception {
        Method toDecision = RedisReactiveRateLimiter.class.getDeclaredMethod("toDecision", List.class);
        toDecision.setAccessible(true);

        RateLimitDecision decision = (RateLimitDecision)toDecision.invoke(null, List.of(0L, 7L, 2L));

        Assertions.assertEquals(RateLimitDecision.rejected(7, GuardDimension.CLIENT_IP), decision);
    }

    @Test
    void test_tokenBucketReplyMapsTenantDimensionCodeFour() throws Exception {
        Method toDecision = RedisReactiveRateLimiter.class.getDeclaredMethod("toDecision", List.class);
        toDecision.setAccessible(true);

        RateLimitDecision decision = (RateLimitDecision)toDecision.invoke(null, List.of(0L, 7L, 4L));

        Assertions.assertEquals(RateLimitDecision.rejected(7, GuardDimension.TENANT), decision);
    }

    @Test
    void test_concurrencyReplyMapsTheActualRejectingDimension() throws Exception {
        Method toDecision =
            RedisReactiveConcurrencyLimiter.class.getDeclaredMethod("toDecision", List.class, String.class, List.class);
        toDecision.setAccessible(true);

        ConcurrencyDecision decision =
            (ConcurrencyDecision)toDecision.invoke(null, List.of(0L, 11L, 3L), UUID.randomUUID().toString(),
                List.of("gateway:test:v1:guard:v3:route:concurrency:authenticated-subject:user:leases"));

        Assertions.assertEquals(ConcurrencyDecision.rejected(11, GuardDimension.AUTHENTICATED_SUBJECT), decision);
    }

    @Test
    void test_concurrencyReplyMapsTenantDimensionCodeFour() throws Exception {
        Method toDecision =
            RedisReactiveConcurrencyLimiter.class.getDeclaredMethod("toDecision", List.class, String.class, List.class);
        toDecision.setAccessible(true);

        ConcurrencyDecision decision =
            (ConcurrencyDecision)toDecision.invoke(null, List.of(0L, 11L, 4L), UUID.randomUUID().toString(),
                List.of("gateway:test:v1:guard:v3:route:concurrency:tenant:tenant:leases"));

        Assertions.assertEquals(ConcurrencyDecision.rejected(11, GuardDimension.TENANT), decision);
    }
}
