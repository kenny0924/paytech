package com.paytech.foundation.caffeine.core.autoconfigure;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;

import com.github.benmanes.caffeine.cache.Caffeine;
import com.paytech.foundation.caffeine.api.CaffeineCacheConstants;

/**
 * 基于 Caffeine 的进程内本地缓存自动配置。
 *
 * @author Codex
 */
@EnableCaching
@AutoConfiguration(beforeName = "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration")
@ConditionalOnClass({Caffeine.class, CaffeineCacheManager.class})
@EnableConfigurationProperties(CaffeineCacheProperties.class)
public class CaffeineCacheAutoConfiguration {

    @Bean(name = CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME)
    @ConditionalOnMissingBean(name = CaffeineCacheConstants.CACHE_MANAGER_BEAN_NAME)
    CaffeineCacheManager caffeineCacheManager(CaffeineCacheProperties properties) {
        properties.validate();

        Caffeine<Object, Object> caffeine = Caffeine.newBuilder().maximumSize(properties.getMaximumSize())
            .expireAfterWrite(properties.getExpireAfterWrite()).recordStats();

        CaffeineCacheManager cacheManager = new CaffeineCacheManager();
        cacheManager.setCaffeine(caffeine);
        cacheManager.setAllowNullValues(false);
        if (!properties.getCacheNames().isEmpty()) {
            cacheManager.setCacheNames(properties.getCacheNames());
        }
        return cacheManager;
    }
}
