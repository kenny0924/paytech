package com.paytech.foundation.redis.core.autoconfigure;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/** 服务级资源锁等待和显式租约配置。 */
@ConfigurationProperties(prefix = "redis.coordination.resource-lock")
public class RedisResourceLockProperties {

    private Duration waitTime;
    private Duration leaseTime;
    private Long waitNanos;
    private Long leaseNanos;

    public Duration getWaitTime() {
        return waitTime;
    }

    public void setWaitTime(Duration waitTime) {
        this.waitTime = waitTime;
    }

    public Duration getLeaseTime() {
        return leaseTime;
    }

    public void setLeaseTime(Duration leaseTime) {
        this.leaseTime = leaseTime;
    }

    public void validateRequiredConfiguration() {
        waitNanos = requiredNanos("wait-time", waitTime, true);
        leaseNanos = requiredNanos("lease-time", leaseTime, false);
    }

    public long requiredWaitNanos() {
        if (waitNanos == null) {
            throw configurationError("wait-time未完成启动校验");
        }
        return waitNanos;
    }

    public long requiredLeaseNanos() {
        if (leaseNanos == null) {
            throw configurationError("lease-time未完成启动校验");
        }
        return leaseNanos;
    }

    private long requiredNanos(String property, Duration duration, boolean zeroAllowed) {
        if (duration == null) {
            throw configurationError(property + "必须显式配置");
        }
        try {
            long nanos = duration.toNanos();
            if (zeroAllowed ? nanos < 0 : nanos <= 0) {
                throw configurationError(property + (zeroAllowed ? "不能小于0" : "必须大于0"));
            }
            if (!zeroAllowed && duration.toMillis() < 1) {
                throw configurationError(property + "不能小于1毫秒");
            }
            return nanos;
        } catch (ArithmeticException failure) {
            throw configurationError(property + "超出有限纳秒范围");
        }
    }

    private FailException configurationError(String message) {
        return new FailException(RedisErrorCode.CONFIGURATION_INVALID,
            "redis.coordination.resource-lock." + message);
    }
}
