package com.paytech.foundation.redis.auto.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.ConfigurableApplicationContext;

import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockAutoConfiguration;
import com.paytech.foundation.redis.test.configuration.ResourceLockTestConsumerConfiguration;
import com.paytech.foundation.redis.test.configuration.ResourceLockTestConsumerConfiguration.BusinessFailure;
import com.paytech.foundation.redis.test.configuration.ResourceLockTestConsumerConfiguration.CommandCaller;

class ResourceLockTransactionBoundaryAutoTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withInitializer(ResourceLockTransactionBoundaryAutoTest::loadProperties)
        .withConfiguration(AutoConfigurations.of(RedisResourceLockAutoConfiguration.class))
        .withUserConfiguration(ResourceLockTestConsumerConfiguration.class);

    @Test
    void acquiresBeforeTransactionAndReleasesAfterCommit() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            assertThat(context.getBean(CommandCaller.class).invoke(false)).isEqualTo("done");

            assertThat(events(context)).containsExactly("lock:gateway-configuration:catalog",
                "acquire:gateway-configuration:catalog", "transaction-begin", "business", "commit",
                "release:gateway-configuration:catalog");
        });
    }

    @Test
    void releasesOnlyAfterRollbackAndPreservesTheBusinessFailure() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();

            assertThatThrownBy(() -> context.getBean(CommandCaller.class).invoke(true))
                .isExactlyInstanceOf(BusinessFailure.class);

            assertThat(events(context)).containsExactly("lock:gateway-configuration:catalog",
                "acquire:gateway-configuration:catalog", "transaction-begin", "business", "rollback",
                "release:gateway-configuration:catalog");
        });
    }

    @SuppressWarnings("unchecked")
    private List<String> events(org.springframework.context.ApplicationContext context) {
        return (List<String>)context.getBean("resourceLockEvents");
    }

    private static void loadProperties(ConfigurableApplicationContext context) {
        try {
            new YamlPropertySourceLoader()
                .load("resource-lock-test", new ClassPathResource("application-resource-lock-auto.yml"))
                .forEach(context.getEnvironment().getPropertySources()::addFirst);
        } catch (IOException failure) {
            throw new IllegalStateException("Cannot load resource lock test configuration", failure);
        }
    }
}
