package com.paytech.platform.support.test.configuration;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.core.env.MapPropertySource;

/** Mock 测试启动时复用仓库本地连接配置，不修改进程环境或导入其他服务的秘密。 */
public class LocalMockEnvironmentInitializer implements ApplicationContextInitializer<ConfigurableApplicationContext> {

    private static final Set<String> CONNECTION_KEYS = Set.of("MOR_PLATFORM_DB_PASSWORD", "MOR_REDIS_PASSWORD", "PAYTECH_REDIS_PASSWORD",
        "MOR_MINIO_PLATFORM_ACCESS_KEY", "MOR_MINIO_PLATFORM_SECRET_KEY", "MOR_MINIO_ROOT_USER",
        "MOR_MINIO_ROOT_PASSWORD", "MOR_DATA_QUERY_FIXTURE_READER_PASSWORD", "MOR_DATA_QUERY_FIXTURE_WRITER_PASSWORD");

    /** MinIO 访问键与管理员账号名最短 3 位，其余连接配置沿用 16 位下限。 */
    private static final Set<String> SHORT_KEYS = Set.of("MOR_MINIO_PLATFORM_ACCESS_KEY", "MOR_MINIO_ROOT_USER");

    /**
     * 本地环境目录的唯一锚点（design/10 §4.4）：paytech-parent 经 Surefire 透传，IDE 运行可用 {@code -D} 覆盖。
     * <li>同名属性也写入低优先级来源，供 YAML 定位证书等本地文件。</li>
     */
    public static final String LOCAL_ENVIRONMENT = "paytech.local-environment.dir";

    /** 未设置锚点时的回退：相对测试模块工作目录的仓库级 local-environment（paytech/platform-support/&lt;module&gt;）。 */
    static final String FALLBACK = "../../../local-environment";

    /** 识别 local-environment 目录的版本化模板文件。 */
    private static final String MARKER = ".env.example";

    private final Path localEnvironment;

    /** Spring 测试按系统属性定位，未设置时按工作目录回退。 */
    public LocalMockEnvironmentInitializer() {
        this.localEnvironment = null;
    }

    /** 允许机制测试提供独立的 local-environment 目录，仅使用合成配置验证加载行为。 */
    public LocalMockEnvironmentInitializer(Path localEnvironment) {
        this.localEnvironment = localEnvironment;
    }

    /** 在创建数据源前提供低优先级密码来源；显式环境变量和 JVM 配置继续优先。 */
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("mock")) {
            return;
        }
        context.getEnvironment().getPropertySources()
            .addLast(new MapPropertySource("platformMockLocalConnection", localProperties(localEnvironment())));
    }

    /** 返回已确认的 local-environment 目录；锚点缺失或指向错误目录时明确失败。 */
    public Path localEnvironment() {
        if (localEnvironment != null) {
            return requireLocalEnvironment(localEnvironment, "构造参数");
        }
        return resolve(System.getProperty(LOCAL_ENVIRONMENT), Path.of(System.getProperty("user.dir")));
    }

    /**
     * 按锚点或回退规则解析 local-environment。
     *
     * @param configured 系统属性 {@value #LOCAL_ENVIRONMENT} 的值，可为空
     * @param workingDirectory 测试 JVM 工作目录，锚点为空时用于回退
     * @return 规范化的绝对目录
     * @throws IllegalStateException 解析结果不是 local-environment 目录
     */
    public static Path resolve(String configured, Path workingDirectory) {
        if (configured != null && !configured.isBlank()) {
            return requireLocalEnvironment(Path.of(configured), "系统属性 " + LOCAL_ENVIRONMENT);
        }
        return requireLocalEnvironment(workingDirectory.resolve(FALLBACK), "回退路径 " + FALLBACK);
    }

    /** 读取单个本地连接配置：显式环境变量与 JVM 配置优先，其次 .env.local；均缺失返回 null。 */
    public String connectionValue(String key) {
        String explicit = System.getProperty(key, System.getenv(key));
        if (explicit != null && !explicit.isBlank()) {
            return explicit;
        }
        return (String) loadConnectionProperties(localEnvironment().resolve(".env.local")).get(key);
    }

    /** 连接密码与 local-environment 路径一并作为最低优先级来源。 */
    private Map<String, Object> localProperties(Path environment) {
        Map<String, Object> values = loadConnectionProperties(environment.resolve(".env.local"));
        values.put(LOCAL_ENVIRONMENT, environment.toString());
        return values;
    }

    /** 以版本化模板确认目录确实是仓库 local-environment，不按目录名向上搜索。 */
    private static Path requireLocalEnvironment(Path candidate, String source) {
        Path directory = candidate.toAbsolutePath().normalize();
        if (!Files.isRegularFile(directory.resolve(MARKER))) {
            throw new IllegalStateException("无法定位仓库 local-environment：" + source + " 解析为 " + directory
                + "，其中缺少 " + MARKER + "；Maven 由 paytech-parent 透传 " + LOCAL_ENVIRONMENT
                + "，IDE 运行请添加 -D" + LOCAL_ENVIRONMENT + "=<仓库根>/local-environment");
        }
        return directory;
    }

    /** 只读取本服务数据库、Redis、MinIO 账号（含仅供测试临时建桶的管理员账号）与数据查询被查库账号配置；文件不存在时沿用 YAML 的仓库默认配置。 */
    private Map<String, Object> loadConnectionProperties(Path file) {
        Map<String, Object> values = new LinkedHashMap<>();
        if (!Files.exists(file)) {
            return values;
        }
        try (var reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            String line;
            while ((line = reader.readLine()) != null) {
                String entry = line.trim();
                if (entry.startsWith("export ")) {
                    entry = entry.substring(7).trim();
                }
                int separator = entry.indexOf('=');
                if (separator < 0) {
                    continue;
                }
                String key = entry.substring(0, separator).trim();
                if (CONNECTION_KEYS.contains(key)) {
                    values.put(key, connectionPassword(key, entry.substring(separator + 1).trim()));
                }
            }
            return values;
        } catch (IOException failure) {
            throw new IllegalStateException("无法读取 local-environment/.env.local 的 Mock 连接配置；请检查本地文件权限");
        }
    }

    /** 沿用 local-environment 初始化脚本的密码格式，不执行 Shell 或变量展开，不在错误中输出值。 */
    private String connectionPassword(String key, String value) {
        if (value.length() >= 2 && ((value.startsWith("\"") && value.endsWith("\""))
            || (value.startsWith("'") && value.endsWith("'")))) {
            value = value.substring(1, value.length() - 1);
        }
        if (!value.matches("[A-Za-z0-9._@%+=:-]{" + (SHORT_KEYS.contains(key) ? 3 : 16) + ",128}")) {
            throw new IllegalStateException("local-environment/.env.local 的 " + key + " 不符合本地连接配置格式");
        }
        return value;
    }
}
