package com.paytech.foundation.redis.core.autoconfigure;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;
import com.paytech.foundation.redis.core.template.NamespacedStringRedisTemplate;

/**
 * StringRedisTemplate 服务级 Key Namespace 自动配置。
 *
 * @author Codex
 */
@AutoConfiguration(before = DataRedisAutoConfiguration.class)
@ConditionalOnClass({StringRedisTemplate.class, RedisConnectionFactory.class})
@EnableConfigurationProperties(RedisNamespaceProperties.class)
public class RedisKeyNamespaceAutoConfiguration {

    @Bean(name = {"stringRedisTemplate", "redisTemplate"})
    @ConditionalOnMissingBean(StringRedisTemplate.class)
    StringRedisTemplate stringRedisTemplate(ObjectProvider<RedisConnectionFactory> connectionFactoryProvider,
        Environment environment, RedisNamespaceProperties namespaceProperties) {
        RedisConnectionFactory connectionFactory = connectionFactoryProvider.getIfUnique();
        if (connectionFactory == null) {
            throw new FailException(RedisErrorCode.CONFIGURATION_INVALID,
                "StringRedisTemplate需要唯一的RedisConnectionFactory");
        }
        String applicationName = environment.getProperty("spring.application.name");
        String keyPrefix = namespaceProperties.resolveKeyPrefix(applicationName);
        return new NamespacedStringRedisTemplate(connectionFactory, keyPrefix);
    }
}
