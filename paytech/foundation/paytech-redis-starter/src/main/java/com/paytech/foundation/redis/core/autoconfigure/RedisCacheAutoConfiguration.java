package com.paytech.foundation.redis.core.autoconfigure;

import java.util.List;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.paytech.foundation.redis.api.RedisCacheDefinition;
import com.paytech.foundation.redis.core.cache.RedisCacheManagerFactory;

/**
 * 将按类型注册的 Redis CacheManager 配置为 Spring Cache 默认实现。
 *
 * @author Codex
 */
@EnableCaching
@AutoConfiguration(after = {DataRedisAutoConfiguration.class, RedisKeyNamespaceAutoConfiguration.class},
    beforeName = "org.springframework.boot.cache.autoconfigure.CacheAutoConfiguration")
@ConditionalOnBean(RedisConnectionFactory.class)
@ConditionalOnClass({CacheManager.class, RedisCacheManager.class})
@EnableConfigurationProperties(RedisNamespaceProperties.class)
public class RedisCacheAutoConfiguration {

    @Bean(name = {"cacheManager", "redisCacheManager"})
    @Primary
    @ConditionalOnMissingBean(value = RedisCacheManager.class, name = {"cacheManager", "redisCacheManager"})
    RedisCacheManager redisCacheManager(ObjectProvider<RedisCacheDefinition<?>> definitionProvider,
        Environment environment, RedisConnectionFactory connectionFactory,
        RedisNamespaceProperties namespaceProperties) {
        String applicationName = environment.getProperty("spring.application.name");
        String keyPrefix = namespaceProperties.resolveKeyPrefix(applicationName);
        List<RedisCacheDefinition<?>> definitions = definitionProvider.orderedStream().toList();
        return new RedisCacheManagerFactory().create(connectionFactory, keyPrefix, definitions);
    }
}
