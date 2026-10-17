package com.paytech.foundation.redis.core.autoconfigure;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/**
 * Redisson 分布式协调配置。
 *
 * @author Codex
 */
@ConfigurationProperties(prefix = "redis.coordination")
public class RedisCoordinationProperties {

    /** 是否启用 Redisson 分布式协调能力。 */
    private boolean enabled;

    /** Redisson 通用任务线程数。 */
    private int threads = 4;

    /** Redisson Netty I/O 线程数。 */
    private int nettyThreads = 4;

    /** 普通连接池最小空闲连接数。 */
    private int connectionMinimumIdleSize = 1;

    /** 普通连接池最大连接数。 */
    private int connectionPoolSize = 16;

    /** 订阅连接池最小空闲连接数。 */
    private int subscriptionConnectionMinimumIdleSize = 1;

    /** 订阅连接池最大连接数。 */
    private int subscriptionConnectionPoolSize = 8;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getThreads() {
        return threads;
    }

    public void setThreads(int threads) {
        this.threads = threads;
    }

    public int getNettyThreads() {
        return nettyThreads;
    }

    public void setNettyThreads(int nettyThreads) {
        this.nettyThreads = nettyThreads;
    }

    public int getConnectionMinimumIdleSize() {
        return connectionMinimumIdleSize;
    }

    public void setConnectionMinimumIdleSize(int connectionMinimumIdleSize) {
        this.connectionMinimumIdleSize = connectionMinimumIdleSize;
    }

    public int getConnectionPoolSize() {
        return connectionPoolSize;
    }

    public void setConnectionPoolSize(int connectionPoolSize) {
        this.connectionPoolSize = connectionPoolSize;
    }

    public int getSubscriptionConnectionMinimumIdleSize() {
        return subscriptionConnectionMinimumIdleSize;
    }

    public void setSubscriptionConnectionMinimumIdleSize(int subscriptionConnectionMinimumIdleSize) {
        this.subscriptionConnectionMinimumIdleSize = subscriptionConnectionMinimumIdleSize;
    }

    public int getSubscriptionConnectionPoolSize() {
        return subscriptionConnectionPoolSize;
    }

    public void setSubscriptionConnectionPoolSize(int subscriptionConnectionPoolSize) {
        this.subscriptionConnectionPoolSize = subscriptionConnectionPoolSize;
    }

    /**
     * 校验启用协调能力时必须具备的完整资源配置。
     */
    public void validateEnabledConfiguration() {
        validateRange("threads", threads, 1, 64);
        validateRange("netty-threads", nettyThreads, 1, 64);
        validatePool("connection", connectionMinimumIdleSize, connectionPoolSize);
        validatePool("subscription-connection", subscriptionConnectionMinimumIdleSize, subscriptionConnectionPoolSize);
    }

    private void validatePool(String property, int minimumIdleSize, int poolSize) {
        validateRange(property + "-minimum-idle-size", minimumIdleSize, 1, 256);
        validateRange(property + "-pool-size", poolSize, 1, 256);
        if (minimumIdleSize > poolSize) {
            throw configurationError(property + "-minimum-idle-size不能大于" + property + "-pool-size");
        }
    }

    private void validateRange(String property, int value, int minimum, int maximum) {
        if (value < minimum || value > maximum) {
            throw configurationError(property + "必须在" + minimum + "到" + maximum + "之间");
        }
    }

    private FailException configurationError(String message) {
        return new FailException(RedisErrorCode.CONFIGURATION_INVALID, "redis.coordination." + message);
    }
}
