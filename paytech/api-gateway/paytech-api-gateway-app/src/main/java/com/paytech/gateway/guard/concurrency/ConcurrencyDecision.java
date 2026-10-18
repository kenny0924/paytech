package com.paytech.gateway.guard.concurrency;

import java.util.Objects;
import java.util.Optional;

import com.paytech.gateway.guard.policy.GuardDimension;

/** 原子多维并发获取的允许或拒绝结果。 */
public record ConcurrencyDecision(boolean allowed, long retryAfterSeconds, Optional<ConcurrencyLease> lease,
    Optional<GuardDimension> rejectingDimension) {
    public ConcurrencyDecision {
        lease = Objects.requireNonNull(lease, "并发 Lease 结果不可为空");
        rejectingDimension = Objects.requireNonNull(rejectingDimension, "拒绝 Guard 维度结果不可为空");
        if (allowed && (retryAfterSeconds != 0 || lease.isEmpty() || rejectingDimension.isPresent())) {
            throw new IllegalArgumentException("允许结果必须包含 Lease、Retry-After 为零且不得包含拒绝维度");
        }
        if (!allowed && (retryAfterSeconds < 1 || lease.isPresent() || rejectingDimension.isEmpty())) {
            throw new IllegalArgumentException("拒绝结果不得包含 Lease，且必须包含拒绝维度与正整数 Retry-After");
        }
    }

    public static ConcurrencyDecision allowed(ConcurrencyLease lease) {
        return new ConcurrencyDecision(true, 0, Optional.of(Objects.requireNonNull(lease, "并发 Lease 不可为空")),
            Optional.empty());
    }

    public static ConcurrencyDecision rejected(long retryAfterSeconds, GuardDimension rejectingDimension) {
        return new ConcurrencyDecision(false, retryAfterSeconds, Optional.empty(),
            Optional.of(Objects.requireNonNull(rejectingDimension, "拒绝 Guard 维度不可为空")));
    }
}
