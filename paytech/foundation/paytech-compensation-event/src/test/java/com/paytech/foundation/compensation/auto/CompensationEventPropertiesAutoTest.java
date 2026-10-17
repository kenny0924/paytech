package com.paytech.foundation.compensation.auto;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import com.paytech.foundation.compensation.core.autoconfigure.CompensationEventProperties;

/**
 * 补偿配置绑定测试。
 *
 * @author Codex
 */
class CompensationEventPropertiesAutoTest {

    private final ApplicationContextRunner contextRunner =
        new ApplicationContextRunner().withUserConfiguration(PropertiesConfiguration.class);

    @Test
    void test_defaultsRequireNoCompensationConfiguration() {
        contextRunner.run(context -> {
            assertNull(context.getStartupFailure());
            CompensationEventProperties properties = context.getBean(CompensationEventProperties.class);
            assertTrue(properties.isEnabled());
            assertEquals(Duration.ofMinutes(2), properties.getLeaseDuration());
            assertEquals(100, properties.getScannerBatchSize());
            assertEquals(10, properties.getCoordinator().getCoreSize());
            assertEquals(20, properties.getCoordinator().getMaximumSize());
            assertEquals(80, properties.getCoordinator().getQueueCapacity());
            assertEquals(Duration.ofSeconds(60), properties.getCoordinator().getKeepAlive());
            assertEquals(20, properties.getHandler().getMaximumConcurrency());
        });
    }

    @Test
    void test_coordinatorAndHandlerCanBeConfiguredIndependently() {
        contextRunner.withPropertyValues("compensation.coordinator.core-size=3",
            "compensation.coordinator.maximum-size=7", "compensation.coordinator.queue-capacity=11",
            "compensation.coordinator.keep-alive=15s", "compensation.handler.maximum-concurrency=13")
            .run(context -> {
                assertNull(context.getStartupFailure());
                CompensationEventProperties properties = context.getBean(CompensationEventProperties.class);
                assertEquals(3, properties.getCoordinator().getCoreSize());
                assertEquals(7, properties.getCoordinator().getMaximumSize());
                assertEquals(11, properties.getCoordinator().getQueueCapacity());
                assertEquals(Duration.ofSeconds(15), properties.getCoordinator().getKeepAlive());
                assertEquals(13, properties.getHandler().getMaximumConcurrency());
            });
    }

    @Test
    void test_legacyPoolConfigurationFailsInsteadOfBeingIgnored() {
        contextRunner.withPropertyValues("compensation.pool.core-size=1")
            .run(context -> assertNotNull(context.getStartupFailure()));
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(CompensationEventProperties.class)
    static class PropertiesConfiguration {
    }
}
