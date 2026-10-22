package com.paytech.platform.support.auto.configuration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Configuration;

import com.paytech.platform.support.filedocument.core.configuration.FileStorageProperties;

/**
 * 存储快照刷新与存储管理锁参数的启动校验：默认值合法，刷新间隔不足 1 秒、负等待、非正租约或超过 Integer.MAX_VALUE 毫秒拒绝启动。
 * 只装配配置类与 Bean Validation，不启动业务应用。
 *
 * @see FileStorageProperties#isTimingValid()
 */
class FileStoragePropertiesTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
        .withUserConfiguration(PropertiesOnly.class);

    /** 未配置时刷新 30 秒、等待 500 毫秒、租约 30 秒；等待可为 0。 */
    @Test
    void test_defaultsAreValid() {
        runner.run(context -> {
            assertThat(context).hasNotFailed();
            var properties = context.getBean(FileStorageProperties.class);
            assertThat(properties.getSnapshotRefresh()).isEqualTo(Duration.ofSeconds(30));
            assertThat(properties.getLockWaitTime()).isEqualTo(Duration.ofMillis(500));
            assertThat(properties.getLockLeaseTime()).isEqualTo(Duration.ofSeconds(30));
        });
        runner.withPropertyValues("files.storage.lock-wait-time=0ms").run(context -> assertThat(context).hasNotFailed());
    }

    /** 每组只设置一个非法值。 */
    @ParameterizedTest
    @ValueSource(strings = {"files.storage.snapshot-refresh=500ms", "files.storage.lock-wait-time=-1ms",
        "files.storage.lock-wait-time=25d", "files.storage.lock-lease-time=0ms", "files.storage.lock-lease-time=25d"})
    void test_invalidValueFailsStartup(String setting) {
        runner.withPropertyValues(setting).run(context -> assertThat(context).hasFailed());
    }

    /** 仅绑定文件存储配置。 */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(FileStorageProperties.class)
    static class PropertiesOnly {
    }
}
