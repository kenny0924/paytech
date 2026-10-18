package com.paytech.gateway.guard.ratelimit;

import java.util.Objects;
import java.util.Optional;

import com.paytech.gateway.guard.policy.GuardDimension;

/** 原子 Token Bucket 的允许或拒绝结果。 */
public record RateLimitDecision(boolean allowed, long retryAfterSeconds, Optional<GuardDimension> rejectingDimension) {
    public RateLimitDecision {
        rejectingDimension = Objects.requireNonNull(rejectingDimension, "拒绝 Guard 维度结果不可为空");
        if (allowed && (retryAfterSeconds != 0 || rejectingDimension.isPresent())) {
            throw new IllegalArgumentException("允许结果的 Retry-After 必须为零且不得包含拒绝维度");
        }
        if (!allowed && (retryAfterSeconds < 1 || rejectingDimension.isEmpty())) {
            throw new IllegalArgumentException("拒绝结果必须包含拒绝维度且 Retry-After 为正整数秒");
        }
    }

    public static RateLimitDecision allowed(long retryAfterSeconds) {
        return new RateLimitDecision(true, retryAfterSeconds, Optional.empty());
    }

    public static RateLimitDecision rejected(long retryAfterSeconds, GuardDimension rejectingDimension) {
        return new RateLimitDecision(false, retryAfterSeconds,
            Optional.of(Objects.requireNonNull(rejectingDimension, "拒绝 Guard 维度不可为空")));
    }
}
