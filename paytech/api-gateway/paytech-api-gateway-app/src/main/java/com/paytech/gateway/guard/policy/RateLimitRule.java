package com.paytech.gateway.guard.policy;

import java.time.Duration;
import java.util.Objects;

/** 一个 Guard Profile 内的不可变限流规则。 */
public record RateLimitRule(GuardDimension dimension, long capacity, long refillTokens, Duration refillPeriod) {

    public RateLimitRule {
        Objects.requireNonNull(dimension, "限流维度不可为空");
        if (capacity <= 0 || refillTokens <= 0 || refillPeriod == null || refillPeriod.isZero()
            || refillPeriod.isNegative()) {
            throw new IllegalArgumentException("限流容量、补充量和补充周期必须为正");
        }
    }
}
