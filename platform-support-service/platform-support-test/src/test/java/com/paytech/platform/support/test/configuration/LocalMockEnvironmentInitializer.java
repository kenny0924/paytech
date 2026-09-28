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

    private static final Set<String> CONNECTION_KEYS = Set.of("MOR_PLATFORM_DB_PASSWORD", "PAYTECH_REDIS_PASSWORD");
    private final Path workspace;

    /** Spring 测试从编译产物或工作目录定位所属工作区，兼容 IDEA 与 Maven 的不同启动目录。 */
    public LocalMockEnvironmentInitializer() {
        this.workspace = null;
    }

    /** 允许机制测试提供独立目录，仅使用合成配置验证加载行为。 */
    public LocalMockEnvironmentInitializer(Path workspace) {
        this.workspace = workspace;
    }

    /** 在创建数据源前提供低优先级密码来源；显式环境变量和 JVM 配置继续优先。 */
    @Override
    public void initialize(ConfigurableApplicationContext context) {
        if (!Arrays.asList(context.getEnvironment().getActiveProfiles()).contains("mock")) {
            return;
        }
        Path root = workspace == null ? findWorkspace() : workspace;
        if (root == null) {
            return;
        }
        Map<String, Object> values = loadConnectionProperties(root.resolve("local-environment/.env.local"));
        if (!values.isEmpty()) {
            context.getEnvironment().getPropertySources()
                .addLast(new MapPropertySource("platformMockLocalConnection", values));
        }
    }

    /** 优先选择当前测试类所属工作区，不把其他检出目录的凭据混入当前测试。 */
    private Path findWorkspace() {
        try {
            Path classes = Path.of(getClass().getProtectionDomain().getCodeSource().getLocation().toURI());
            Path root = workspaceAbove(classes);
            return root != null ? root : workspaceAbove(Path.of(System.getProperty("user.dir")));
        } catch (java.net.URISyntaxException failure) {
            throw new IllegalStateException("无法定位 Mock 测试所属工作区");
        }
    }

    /** 以仓库本地环境模板和服务目录确认根目录，不依赖固定机器路径。 */
    private Path workspaceAbove(Path start) {
        for (Path directory = start.toAbsolutePath().normalize(); directory != null; directory = directory.getParent()) {
            if (Files.isRegularFile(directory.resolve("local-environment/.env.example"))
                && Files.isRegularFile(directory.resolve("platform-support-service/pom.xml"))) {
                return directory;
            }
        }
        return null;
    }

    /** 只读取本服务数据库与 Redis 密码；文件不存在时沿用 YAML 的仓库默认配置。 */
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
        if (!value.matches("[A-Za-z0-9._@%+=:-]{16,128}")) {
            throw new IllegalStateException("local-environment/.env.local 的 " + key + " 不符合本地连接配置格式");
        }
        return value;
    }
}
