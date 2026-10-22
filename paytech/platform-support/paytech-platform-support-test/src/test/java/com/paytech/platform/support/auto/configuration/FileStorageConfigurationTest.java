package com.paytech.platform.support.auto.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Map;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import com.paytech.platform.support.configuration.FileStorageConfiguration;

/**
 * 文件存储启动检查：YAML 形式与环境变量形式的任一旧键都使启动失败并提示改到运营存储管理；files.enabled 时缺少 Redis 资源协调或
 * 本地补偿执行器也拒绝启动。依赖检查只装配本配置类，不启动业务应用。
 *
 * @see FileStorageConfiguration#FileStorageConfiguration(org.springframework.core.env.Environment)
 */
class FileStorageConfigurationTest {

    final ApplicationContextRunner runner = new ApplicationContextRunner()
        .withUserConfiguration(FileStorageConfiguration.class);

    /** 每个旧键（含下级键）都拒绝启动，并给出迁移提示。 */
    @ParameterizedTest(name = "{0}")
    @ValueSource(strings = {"files.onboarding.storage-provider", "files.onboarding.storage-container-ref",
        "files.onboarding.scan-policy-code", "files.onboarding.upload-seconds", "files.onboarding.minio.endpoint",
        "files.onboarding.s3.region"})
    void test_removedKeyRejected(String key) {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("legacy", Map.of(key, "value")));

        var failure = assertThrows(IllegalStateException.class, () -> new FileStorageConfiguration(environment));

        assertTrue(failure.getMessage().contains("存储配置已改为运营存储管理"));
    }

    /** 环境变量形式的旧键同样拒绝。 */
    @Test
    void test_environmentVariableRejected() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().replace(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
            new SystemEnvironmentPropertySource(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME,
                Map.of("FILES_ONBOARDING_MINIO_ACCESS_KEY", "value")));

        assertThrows(IllegalStateException.class, () -> new FileStorageConfiguration(environment));
    }

    /** 新键与入驻开关、CCT 测试直连等保留键不触发拒绝。 */
    @Test
    void test_currentKeysAccepted() {
        var environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new MapPropertySource("current", Map.of("files.enabled", "true",
            "files.onboarding.enabled", "true", "files.onboarding.cct-test-base-url", "",
            "files.storage.snapshot-refresh", "30s")));

        assertDoesNotThrow(() -> new FileStorageConfiguration(environment));
    }

    /** files.enabled 时 Redis 资源协调与本地补偿执行器都必须启用；文件能力关闭时不检查。 */
    @Test
    void test_enabledDependencies() {
        runner.withPropertyValues("files.enabled=true", "redis.coordination.enabled=true")
            .run(context -> assertThat(context).hasNotFailed());
        runner.withPropertyValues("files.enabled=true").run(context -> assertThat(context).hasFailed()
            .getFailure().rootCause().hasMessageContaining("files.enabled 需要 Redis 资源协调与本地补偿执行器"));
        runner.withPropertyValues("files.enabled=true", "redis.coordination.enabled=true", "compensation.enabled=false")
            .run(context -> assertThat(context).hasFailed());
        runner.withPropertyValues("files.enabled=false").run(context -> assertThat(context).hasNotFailed());
    }
}
