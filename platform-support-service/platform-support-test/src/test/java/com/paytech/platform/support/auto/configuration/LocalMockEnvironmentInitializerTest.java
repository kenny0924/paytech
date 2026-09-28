package com.paytech.platform.support.auto.configuration;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.env.MapPropertySource;

import com.paytech.platform.support.test.configuration.LocalMockEnvironmentInitializer;

/**
 * 验证 Mock 启动配置的自动加载、覆盖优先级与秘密隔离，文件只包含合成值。
 *
 * @see LocalMockEnvironmentInitializer#initialize(ConfigurableApplicationContext)
 */
class LocalMockEnvironmentInitializerTest {

    private static final String DATABASE_PASSWORD = "synthetic-platform-password";
    private static final String REDIS_PASSWORD = "synthetic-redis-password";
    @TempDir
    Path workspace;

    /** 只导入所需连接字段，支持本地文件中常见的引号和 export 写法。 */
    @Test
    void test_loadOnlyLocalConnectionPasswords() throws Exception {
        writeEnvironment("MOR_MYSQL_ROOT_PASSWORD=unrelated-root-secret\n"
            + "MOR_CCT_DB_PASSWORD=unrelated-cct-secret\n"
            + "MOR_PLATFORM_DB_PASSWORD='" + DATABASE_PASSWORD + "'\n"
            + "export PAYTECH_REDIS_PASSWORD=\"" + REDIS_PASSWORD + "\"\n");
        try (var context = mockContext()) {
            new LocalMockEnvironmentInitializer(workspace).initialize(context);
            var environment = context.getEnvironment();
            assertAll(() -> assertEquals(DATABASE_PASSWORD, environment.getProperty("spring.datasource.password")),
                () -> assertEquals(REDIS_PASSWORD, environment.getProperty("spring.data.redis.password")),
                () -> assertNull(environment.getProperty("MOR_MYSQL_ROOT_PASSWORD")),
                () -> assertNull(environment.getProperty("MOR_CCT_DB_PASSWORD")));
        }
    }

    /** 显式测试覆盖始终优先，不被本地文件静默替换。 */
    @ParameterizedTest
    @ValueSource(strings = {"PLATFORM_SUPPORT_TEST_DB_PASSWORD", "MOR_PLATFORM_DB_PASSWORD"})
    void test_explicitPasswordTakesPrecedence(String key) throws Exception {
        writeEnvironment("MOR_PLATFORM_DB_PASSWORD=" + DATABASE_PASSWORD);
        try (var context = mockContext()) {
            context.getEnvironment().getPropertySources()
                .addFirst(new MapPropertySource("explicitOverride", Map.of(key, "explicit-test-password")));
            new LocalMockEnvironmentInitializer(workspace).initialize(context);
            assertEquals("explicit-test-password", context.getEnvironment().getProperty("spring.datasource.password"));
        }
    }

    /** 没有私有本地文件时继续使用 YAML 声明的隔离默认值。 */
    @Test
    void test_missingLocalFileKeepsDefaults() {
        try (var context = mockContext()) {
            new LocalMockEnvironmentInitializer(workspace).initialize(context);
            assertEquals("default-local-password", context.getEnvironment().getProperty("spring.datasource.password"));
        }
    }

    /** 非 Mock Profile 不读取本地文件，避免影响其他启动方式。 */
    @Test
    void test_manualProfileDoesNotLoadLocalPasswords() throws Exception {
        writeEnvironment("MOR_PLATFORM_DB_PASSWORD=" + DATABASE_PASSWORD);
        try (var context = mockContext()) {
            context.getEnvironment().setActiveProfiles("manual");
            new LocalMockEnvironmentInitializer(workspace).initialize(context);
            assertNull(context.getEnvironment().getProperty("MOR_PLATFORM_DB_PASSWORD"));
        }
    }

    /** 错误只包含配置键，不能把受保护值或未解析表达式带入异常。 */
    @Test
    void test_invalidPasswordFailsWithoutExposingValue() throws Exception {
        String invalidValue = "sensitive-${DO_NOT_EXPAND}";
        writeEnvironment("MOR_PLATFORM_DB_PASSWORD=" + invalidValue);
        try (var context = mockContext()) {
            var failure = assertThrows(IllegalStateException.class,
                () -> new LocalMockEnvironmentInitializer(workspace).initialize(context));
            assertTrue(failure.getMessage().contains("MOR_PLATFORM_DB_PASSWORD"));
            assertFalse(failure.toString().contains(invalidValue));
            assertNull(failure.getCause());
        }
    }

    /** 最小容器只验证测试配置入口，不启动业务应用或连接基础设施。 */
    private GenericApplicationContext mockContext() {
        var context = new GenericApplicationContext();
        var environment = context.getEnvironment();
        environment.getPropertySources().remove("systemProperties");
        environment.getPropertySources().remove("systemEnvironment");
        environment.setActiveProfiles("mock");
        environment.getPropertySources().addLast(new MapPropertySource("mockYaml", Map.of(
            "spring.datasource.password", "${PLATFORM_SUPPORT_TEST_DB_PASSWORD:${MOR_PLATFORM_DB_PASSWORD:default-local-password}}",
            "spring.data.redis.password", "${PLATFORM_SUPPORT_TEST_REDIS_PASSWORD:${PAYTECH_REDIS_PASSWORD:default-local-redis}}")));
        return context;
    }

    /** 在独立临时目录写入合成连接配置，不接触真实 .env.local。 */
    private void writeEnvironment(String contents) throws Exception {
        Path directory = Files.createDirectories(workspace.resolve("local-environment"));
        Files.writeString(directory.resolve(".env.local"), contents);
    }
}
