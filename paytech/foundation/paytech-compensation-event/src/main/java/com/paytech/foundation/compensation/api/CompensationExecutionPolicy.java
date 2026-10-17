package com.paytech.foundation.compensation.api;

import java.time.Duration;
import java.util.Objects;

/**
 * 单一受控事件类型的超时与重试策略。
 *
 * @param timeout
 *            处理器最长执行时间
 * @param maximumAttempts
 *            包含首次执行在内的 Handler 最大执行次数
 * @param initialBackoff
 *            首次重试延迟
 * @param maximumBackoff
 *            重试延迟上限
 * @param multiplier
 *            指数退避倍数
 * @param jitterRatio
 *            有界随机抖动比例
 * @author Codex
 */
public record CompensationExecutionPolicy(Duration timeout, int maximumAttempts, Duration initialBackoff,
    Duration maximumBackoff, double multiplier, double jitterRatio) {

    public CompensationExecutionPolicy {
        timeout = requirePositive("timeout", timeout);
        if (maximumAttempts <= 0) {
            throw new IllegalArgumentException("maximumAttempts must be positive");
        }
        initialBackoff = requirePositive("initialBackoff", initialBackoff);
        maximumBackoff = requirePositive("maximumBackoff", maximumBackoff);
        if (initialBackoff.compareTo(maximumBackoff) > 0) {
            throw new IllegalArgumentException("initialBackoff must not exceed maximumBackoff");
        }
        if (!Double.isFinite(multiplier) || multiplier < 1.0D) {
            throw new IllegalArgumentException("multiplier must be finite and at least 1.0");
        }
        if (!Double.isFinite(jitterRatio) || jitterRatio < 0.0D || jitterRatio > 0.5D) {
            throw new IllegalArgumentException("jitterRatio must be between 0.0 and 0.5");
        }
    }

    public static CompensationExecutionPolicy defaults() {
        return new CompensationExecutionPolicy(Duration.ofSeconds(30), 5, Duration.ofSeconds(1), Duration.ofMinutes(5),
            2.0D, 0.2D);
    }

    private static Duration requirePositive(String field, Duration value) {
        Duration requiredValue = Objects.requireNonNull(value, field + " must not be null");
        if (requiredValue.isZero() || requiredValue.isNegative()) {
            throw new IllegalArgumentException(field + " must be positive");
        }
        return requiredValue;
    }
}
