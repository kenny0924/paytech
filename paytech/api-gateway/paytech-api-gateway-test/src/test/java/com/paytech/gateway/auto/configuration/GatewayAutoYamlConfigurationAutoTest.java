package com.paytech.gateway.auto.configuration;

import java.time.Duration;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.StandardEnvironment;

/** 仅验证 Spring 配置加载/绑定，不创建客户端或访问中间件。 */
class GatewayAutoYamlConfigurationAutoTest {
    private final ApplicationContextRunner configuration = new ApplicationContextRunner()
        .withInitializer(context -> {
            // 模拟 IDE 未注入环境的配置边界，不修改测试进程的真实环境。
            context.getEnvironment().getPropertySources()
                .remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
            context.getEnvironment().getPropertySources()
                .remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
            new ConfigDataApplicationContextInitializer().initialize(context);
        })
        .withPropertyValues("spring.profiles.active=auto",
            "gateway.test.local-config=optional:classpath:fixtures/no-local-config.yml");

    @Test
    void test_autoYamlProvidesFixedDefaultsWithoutProcessEnvironment() {
        configuration.run(context -> {
            Assertions.assertNull(context.getStartupFailure());
            var environment = context.getEnvironment();
            RedisSettings redis = Binder.get(environment).bind("spring.data.redis", RedisSettings.class).get();
            Assertions.assertEquals("127.0.0.1", redis.host());
            Assertions.assertEquals(16379, redis.port());
            Assertions.assertTrue("local-redis-change-me-2026".equals(redis.password()),
                "应使用仓库隔离测试账号默认值，不输出实际凭据");
            Assertions.assertEquals(Duration.ofSeconds(2), redis.connectTimeout());
            Assertions.assertEquals(Duration.ofSeconds(2), redis.timeout());
            Assertions.assertEquals("0", environment.getProperty("server.port"));
            Assertions.assertEquals("0", environment.getProperty("management.server.port"));
            Assertions.assertEquals("false", environment.getProperty("spring.cloud.nacos.discovery.enabled"));
            Assertions.assertEquals("false", environment.getProperty("spring.cloud.nacos.config.enabled"));
            Assertions.assertEquals("false", environment.getProperty("gateway.platform-snapshot.scheduling-enabled"));
            Assertions.assertEquals("PT0.5S", environment.getProperty("gateway.platform-credential.connect-timeout"));
        });
    }

    @Test
    void test_yamlAllowsExplicitCredentialOverrideWithoutJavaEnvironmentReads() {
        configuration.withPropertyValues("PAYTECH_REDIS_PASSWORD=synthetic-override-only").run(context -> {
            Assertions.assertNull(context.getStartupFailure());
            RedisSettings redis = Binder.get(context.getEnvironment()).bind("spring.data.redis", RedisSettings.class).get();
            Assertions.assertTrue("synthetic-override-only".equals(redis.password()),
                "Spring 应解析 YAML 覆盖入口，不输出实际凭据");
        });
    }

    @Test
    void test_autoYamlImportsLocalCredentialWithoutProcessEnvironment() {
        localConfiguration().run(context -> {
            Assertions.assertNull(context.getStartupFailure());
            RedisSettings redis = Binder.get(context.getEnvironment()).bind("spring.data.redis", RedisSettings.class).get();
            Assertions.assertTrue("synthetic-local-yaml-only".equals(redis.password()),
                "本机 YAML 应提供 Redis 凭据，不输出实际值");
            Assertions.assertArrayEquals(new String[] {"auto"}, context.getEnvironment().getActiveProfiles());
        });
    }

    @Test
    void test_explicitOverrideTakesPriorityOverLocalYaml() {
        localConfiguration().withPropertyValues("PAYTECH_REDIS_PASSWORD=synthetic-explicit-only").run(context -> {
            Assertions.assertNull(context.getStartupFailure());
            RedisSettings redis = Binder.get(context.getEnvironment()).bind("spring.data.redis", RedisSettings.class).get();
            Assertions.assertTrue("synthetic-explicit-only".equals(redis.password()));
        });
    }

    @Test
    void test_localCredentialDoesNotOverrideUnavailableScenario() {
        localConfiguration().withPropertyValues("gateway.test.redis.scenario=unavailable").run(context -> {
            Assertions.assertNull(context.getStartupFailure());
            RedisSettings redis = Binder.get(context.getEnvironment()).bind("spring.data.redis", RedisSettings.class).get();
            Assertions.assertTrue("private-unavailable-test".equals(redis.password()));
            Assertions.assertEquals(Duration.ofMillis(25), redis.timeout());
        });
    }

    private ApplicationContextRunner localConfiguration() {
        return configuration.withPropertyValues("gateway.test.local-config=classpath:fixtures/redis-local-configuration.yml");
    }

    @Test
    void test_yamlSelectsFaultSettingsWithoutAddingAnotherSpringProfile() {
        configuration.withPropertyValues("gateway.test.redis.scenario=unavailable").run(context -> {
            Assertions.assertNull(context.getStartupFailure());
            var environment = context.getEnvironment();
            RedisSettings redis = Binder.get(environment).bind("spring.data.redis", RedisSettings.class).get();
            Assertions.assertEquals(Duration.ofMillis(25), redis.connectTimeout());
            Assertions.assertEquals(Duration.ofMillis(25), redis.timeout());
            Assertions.assertTrue("private-unavailable-test".equals(redis.password()));
            Assertions.assertArrayEquals(new String[] {"auto"}, environment.getActiveProfiles());
        });
    }

    record RedisSettings(String host, int port, String password, Duration connectTimeout, Duration timeout) {
        @Override
        public String toString() {
            return "RedisSettings[credentials-redacted]";
        }
    }
}
