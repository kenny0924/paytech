package com.paytech.foundation.redis.core.autoconfigure;

import org.redisson.api.RedissonClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.core.env.Environment;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.paytech.foundation.redis.api.RedisJsonTemplate;
import com.paytech.foundation.redis.core.coordination.FoundationRedissonClientDescriptor;
import com.paytech.foundation.redis.core.coordination.RedissonClientFactory;

/**
 * Redis 普通访问与可选分布式协调能力的 Spring Boot 自动配置。
 *
 * <p>
 * 普通 Redis 操作继续由 Spring Boot 创建 Lettuce 连接工厂；本配置只按需增加 Redisson 客户端。
 *
 * @author Codex
 */
@AutoConfiguration(after = {DataRedisAutoConfiguration.class, RedisKeyNamespaceAutoConfiguration.class})
@ConditionalOnClass({RedissonClient.class, RedisConnectionFactory.class})
@EnableConfigurationProperties({RedisCoordinationProperties.class, RedisNamespaceProperties.class})
@Import(RedisAutoConfiguration.RedissonCoordinationClientConfiguration.class)
public class RedisAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RedisJsonTemplate.class)
    RedisJsonTemplate redisJsonTemplate(StringRedisTemplate redisTemplate) {
        return new RedisJsonTemplate(redisTemplate);
    }

    @Configuration(proxyBeanMethods = false)
    @ConditionalOnMissingBean(RedissonClient.class)
    @ConditionalOnProperty(prefix = "redis.coordination", name = "enabled", havingValue = "true")
    static class RedissonCoordinationClientConfiguration {

        @Bean(destroyMethod = "shutdown")
        RedissonClient redissonClient(Environment environment, DataRedisProperties redisProperties,
            DataRedisConnectionDetails connectionDetails, RedisCoordinationProperties coordinationProperties,
            RedisNamespaceProperties namespaceProperties) {
            String applicationName = environment.getProperty("spring.application.name");
            String keyPrefix = namespaceProperties.resolveKeyPrefix(applicationName);
            return new RedissonClientFactory().create(connectionDetails, redisProperties, coordinationProperties,
                keyPrefix);
        }

        @Bean
        FoundationRedissonClientDescriptor foundationRedissonClientDescriptor(RedissonClient client,
            Environment environment, RedisNamespaceProperties namespaceProperties) {
            String keyPrefix = namespaceProperties.resolveKeyPrefix(
                environment.getProperty("spring.application.name"));
            return new FoundationRedissonClientDescriptor(client, keyPrefix);
        }
    }
}
