package com.paytech.gateway.auto.redis;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;

/** 仓库专用 Redis 的显式环境契约测试。 */
class RedisEnvironmentPreconditionAutoTest extends GatewayAutoTestBase {
    @Autowired
    private ReactiveRedisConnectionFactory connectionFactory;

    @Test
    void test_requiresAuthenticatedRepositoryPinnedRedisAndOwnedNamespace() {
        Assertions.assertEquals(repositoryRedisEnvironment.requiredVersion(),
            repositoryRedisEnvironment.assertReady());
        Assertions.assertTrue(GatewayAutoTestRuntime.redisNamespace().value()
            .matches("gateway:test:v1:[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}"));
        redisTestCleaner.assertSentinelPresent();
    }

    @Test
    void test_preconditionUsesTheApplicationConnectionConfiguredByAutoYaml() {
        Assertions.assertSame(connectionFactory, repositoryRedisEnvironment.connectionFactory());
        LettuceConnectionFactory lettuce = Assertions.assertInstanceOf(LettuceConnectionFactory.class, connectionFactory);
        Assertions.assertEquals("127.0.0.1", lettuce.getHostName());
        Assertions.assertEquals(16379, lettuce.getPort());
        Assertions.assertEquals(java.time.Duration.ofSeconds(2), lettuce.getClientConfiguration().getCommandTimeout());
        Assertions.assertTrue(lettuce.getStandaloneConfiguration().getPassword().isPresent());
        Assertions.assertEquals("8.2.1", repositoryRedisEnvironment.requiredVersion());
    }
}
