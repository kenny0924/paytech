package com.paytech.foundation.caffeine.core.autoconfigure;

import java.time.Duration;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.boot.context.properties.ConfigurationProperties;

import com.paytech.foundation.caffeine.core.error.CaffeineErrorCode;
import com.paytech.foundation.common.error.FailException;

/**
 * Caffeine 本地缓存配置。
 *
 * @author Codex
 */
@ConfigurationProperties(prefix = "caffeine")
public class CaffeineCacheProperties {

    private static final Duration MINIMUM_EXPIRATION = Duration.ofMillis(1);

    /** 单个缓存最多保存的条目数。 */
    private long maximumSize = 10_000;

    /** 条目写入后的过期时间。 */
    private Duration expireAfterWrite = Duration.ofMinutes(10);

    /** 可选的缓存名称白名单；为空时按使用到的名称动态创建缓存。 */
    private Set<String> cacheNames = new LinkedHashSet<>();

    public long getMaximumSize() {
        return maximumSize;
    }

    public void setMaximumSize(long maximumSize) {
        this.maximumSize = maximumSize;
    }

    public Duration getExpireAfterWrite() {
        return expireAfterWrite;
    }

    public void setExpireAfterWrite(Duration expireAfterWrite) {
        this.expireAfterWrite = expireAfterWrite;
    }

    public Set<String> getCacheNames() {
        return cacheNames;
    }

    public void setCacheNames(Set<String> cacheNames) {
        this.cacheNames = cacheNames == null ? new LinkedHashSet<>() : new LinkedHashSet<>(cacheNames);
    }

    /**
     * 校验本地缓存配置，避免创建无边界或不会过期的缓存。
     */
    void validate() {
        if (maximumSize < 1) {
            throw configurationError("maximum-size必须大于零");
        }
        if (expireAfterWrite == null || expireAfterWrite.compareTo(MINIMUM_EXPIRATION) < 0) {
            throw configurationError("expire-after-write必须至少为1毫秒");
        }
        for (String cacheName : cacheNames) {
            if (cacheName == null || cacheName.isBlank()) {
                throw configurationError("cache-names不能包含空名称");
            }
        }
    }

    private FailException configurationError(String message) {
        return new FailException(CaffeineErrorCode.CONFIGURATION_INVALID, "caffeine." + message);
    }
}
