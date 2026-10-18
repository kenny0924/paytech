package com.paytech.foundation.compensation.core.execution;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleSupplier;

import com.paytech.foundation.compensation.api.CompensationExecutionPolicy;

/**
 * 计算带上限和有界对称抖动的指数退避重试延迟。
 *
 * @author Codex
 */
public final class RetryDelayCalculator {

    private final DoubleSupplier randomSupplier;

    public RetryDelayCalculator() {
        this(() -> ThreadLocalRandom.current().nextDouble());
    }

    public RetryDelayCalculator(DoubleSupplier randomSupplier) {
        this.randomSupplier = Objects.requireNonNull(randomSupplier, "randomSupplier must not be null");
    }

    public Duration calculate(CompensationExecutionPolicy policy, int attemptNumber) {
        CompensationExecutionPolicy requiredPolicy = Objects.requireNonNull(policy, "policy must not be null");
        if (attemptNumber <= 0) {
            throw new IllegalArgumentException("attemptNumber must be positive");
        }
        double randomValue = randomSupplier.getAsDouble();
        if (!Double.isFinite(randomValue) || randomValue < 0.0D || randomValue > 1.0D) {
            throw new IllegalStateException("randomSupplier must return a value between 0.0 and 1.0");
        }

        long maximumNanos = saturatedNanos(requiredPolicy.maximumBackoff());
        long initialNanos = saturatedNanos(requiredPolicy.initialBackoff());
        double exponentialNanos = initialNanos * Math.pow(requiredPolicy.multiplier(), attemptNumber - 1.0D);
        double boundedNanos = Math.min(maximumNanos, exponentialNanos);
        double jitterFactor = 1.0D + ((randomValue * 2.0D) - 1.0D) * requiredPolicy.jitterRatio();
        long delayedNanos = Math.round(Math.min(maximumNanos, boundedNanos * jitterFactor));
        return Duration.ofNanos(Math.max(1L, delayedNanos));
    }

    private long saturatedNanos(Duration duration) {
        try {
            return duration.toNanos();
        } catch (ArithmeticException exception) {
            return Long.MAX_VALUE;
        }
    }
}
