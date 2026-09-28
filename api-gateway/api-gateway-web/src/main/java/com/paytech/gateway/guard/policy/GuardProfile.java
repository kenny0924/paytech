package com.paytech.gateway.guard.policy;

import java.time.Duration;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/** 一条 Gateway 路由在一个身份范围内的不可变 Guard 配置。 */
public record GuardProfile(List<RateLimitRule> rateLimits, List<ConcurrencyRule> concurrencyLimits,
    Duration leaseDuration, Duration stateTtl) {

    public GuardProfile {
        rateLimits = List.copyOf(Objects.requireNonNull(rateLimits, "限流规则不可为空"));
        concurrencyLimits = List.copyOf(Objects.requireNonNull(concurrencyLimits, "并发规则不可为空"));
        validateUniqueDimensions(rateLimits, concurrencyLimits);
        validateDuration(leaseDuration, "并发租约时长必须为正");
        validateDuration(stateTtl, "Guard 状态 TTL 必须为正");
        if (stateTtl.compareTo(leaseDuration) < 0) {
            throw new IllegalArgumentException("Guard 状态 TTL 不可短于并发租约时长");
        }
    }

    private static void validateUniqueDimensions(List<RateLimitRule> rateLimits,
        List<ConcurrencyRule> concurrencyLimits) {
        validateUniqueRateDimensions(rateLimits);
        validateUniqueConcurrencyDimensions(concurrencyLimits);
    }

    private static void validateUniqueRateDimensions(List<RateLimitRule> rules) {
        Set<GuardDimension> dimensions = new HashSet<>();
        if (rules.stream().anyMatch(rule -> !dimensions.add(rule.dimension()))) {
            throw new IllegalArgumentException("Guard Profile 存在重复的限流维度");
        }
    }

    private static void validateUniqueConcurrencyDimensions(List<ConcurrencyRule> rules) {
        Set<GuardDimension> dimensions = new HashSet<>();
        if (rules.stream().anyMatch(rule -> !dimensions.add(rule.dimension()))) {
            throw new IllegalArgumentException("Guard Profile 存在重复的并发维度");
        }
    }

    private static void validateDuration(Duration value, String message) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }
}
