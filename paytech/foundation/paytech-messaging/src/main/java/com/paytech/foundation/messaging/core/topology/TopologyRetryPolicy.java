package com.paytech.foundation.messaging.core.topology;

import java.time.Duration;
import java.util.Objects;

/**
 * 重声明失败项的有界指数退避（配置 {@code messaging.recovery.*}）：不限次数，直到成功或应用停止。
 *
 * @param initialInterval 首次重试前的等待，正数
 * @param maxInterval 重试间隔上限，不小于首次等待
 * @param multiplier 每次连续失败后的间隔倍数，不小于 1
 * @param errorThreshold 连续失败达到该次数时记一次 ERROR，正数
 * @author Claude
 */
public record TopologyRetryPolicy(Duration initialInterval, Duration maxInterval, double multiplier,
    int errorThreshold) {

    public TopologyRetryPolicy {
        Objects.requireNonNull(initialInterval, "messaging.recovery.initial-interval must not be null");
        Objects.requireNonNull(maxInterval, "messaging.recovery.max-interval must not be null");
        if (initialInterval.isZero() || initialInterval.isNegative()) {
            throw new IllegalArgumentException("messaging.recovery.initial-interval 必须为正: " + initialInterval);
        }
        if (maxInterval.compareTo(initialInterval) < 0) {
            throw new IllegalArgumentException("messaging.recovery.max-interval 不能小于 initial-interval: "
                + maxInterval + " < " + initialInterval);
        }
        if (!(multiplier >= 1.0) || Double.isInfinite(multiplier)) {
            throw new IllegalArgumentException("messaging.recovery.multiplier 必须是不小于 1 的有限数: " + multiplier);
        }
        if (errorThreshold < 1) {
            throw new IllegalArgumentException("messaging.recovery.error-threshold 必须为正: " + errorThreshold);
        }
    }

    /**
     * 第 n 次连续失败后的重试间隔：{@code initialInterval × multiplier^(n-1)}，不超过上限。
     *
     * @param consecutiveFailures 连续失败次数；不大于 0 时按首次等待
     * @return 重试间隔
     */
    public Duration delayAfter(int consecutiveFailures) {
        if (consecutiveFailures <= 1) {
            return initialInterval;
        }
        double millis = initialInterval.toMillis() * Math.pow(multiplier, consecutiveFailures - 1);
        if (millis >= maxInterval.toMillis()) {
            return maxInterval;
        }
        return Duration.ofMillis((long) millis);
    }
}
