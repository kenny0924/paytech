package com.paytech.foundation.redis.core.lock;

import java.util.function.Supplier;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.micrometer.core.instrument.MeterRegistry;

/** 只使用 Prefix 和固定状态维度的资源锁观察边界。 */
public final class ResourceLockObservation {

    private static final Logger LOGGER = LoggerFactory.getLogger(ResourceLockObservation.class);

    private final Supplier<MeterRegistry> meterRegistries;

    /** 显式提供 Registry 的独立使用方保留原构造方式。 */
    public ResourceLockObservation(MeterRegistry meterRegistry) {
        this(() -> meterRegistry);
    }

    /** Advisor 初始化阶段只保留查找函数，避免 Registry 跳过监控 Bean 后处理器。 */
    private ResourceLockObservation(Supplier<MeterRegistry> meterRegistries) {
        this.meterRegistries = java.util.Objects.requireNonNull(meterRegistries, "meterRegistries");
    }

    /** 容器中的 Registry 只在实际记录锁指标时解析。 */
    public static ResourceLockObservation deferred(Supplier<MeterRegistry> meterRegistries) {
        return new ResourceLockObservation(meterRegistries);
    }

    public void acquired(String prefix) {
        increment(prefix, "acquired");
    }

    public void contended(String prefix) {
        increment(prefix, "contended");
    }

    public void interrupted(String prefix) {
        increment(prefix, "interrupted");
    }

    public void unavailable(String prefix) {
        increment(prefix, "unavailable");
    }

    public void ownershipLost(String prefix) {
        increment(prefix, "ownership-lost");
        safeWarn("Resource lock ownership lost before release: prefix={}, category={}", prefix, "lease");
    }

    public void releaseFailed(String prefix, RuntimeException failure) {
        increment(prefix, "release-failed");
        safeWarn("Resource lock release failed: prefix={}, category={}", prefix,
            failure instanceof IllegalMonitorStateException ? "ownership" : "redis");
    }

    /** Registry 的查找或计数失败均不得改变锁与业务结果。 */
    private void increment(String prefix, String status) {
        try {
            MeterRegistry meterRegistry = meterRegistries.get();
            if (meterRegistry != null) {
                meterRegistry.counter("paytech.resource.lock", "prefix", prefix, "status", status).increment();
            }
        } catch (RuntimeException ignored) {
            safeWarn("Resource lock metric failed: prefix={}, category={}", prefix, "metrics");
        }
    }

    private void safeWarn(String message, String prefix, String category) {
        try {
            LOGGER.warn(message, prefix, category);
        } catch (RuntimeException ignored) {
            // 观察基础设施不得改变锁或业务结果。
        }
    }
}
