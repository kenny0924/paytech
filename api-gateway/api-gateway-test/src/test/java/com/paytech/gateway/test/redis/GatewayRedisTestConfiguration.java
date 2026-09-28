package com.paytech.gateway.test.redis;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

import com.paytech.gateway.test.GatewayAutoTestRuntime;

/** 测试探针与生产 Gateway 复用同一个 Spring 管理的 Redis 连接工厂。 */
@TestConfiguration(proxyBeanMethods = false)
public class GatewayRedisTestConfiguration {
    @Bean
    RepositoryRedisEnvironment repositoryRedisEnvironment(ReactiveRedisConnectionFactory connectionFactory,
        @Value("${gateway.test.redis.required-version}") String requiredVersion,
        @Value("${gateway.test.redis.precondition-timeout}") Duration preconditionTimeout) {
        return new RepositoryRedisEnvironment(connectionFactory, requiredVersion, preconditionTimeout);
    }

    @Bean
    RedisTestCleaner redisTestCleaner(ReactiveRedisConnectionFactory connectionFactory) {
        return new RedisTestCleaner(connectionFactory, GatewayAutoTestRuntime.redisNamespace());
    }
}
