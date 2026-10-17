package com.paytech.foundation.messaging.test.support;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 从 {@code paytech.local-environment.dir}/.env.local 读取本地 RabbitMQ 账号（design/10 §4.4 的唯一锚点）。
 * <li>只读取 {@code MOR_RABBITMQ_USER}、{@code MOR_RABBITMQ_PASSWORD}、{@code MOR_RABBITMQ_VHOST}，不打印值。</li>
 * <li>文件或键缺失时失败并给出启动说明，不静默跳过。</li>
 */
public final class LocalRabbitEnvironment {

    /** 本地 Broker 地址。 */
    public static final String HOST = "127.0.0.1";

    /** 本地 Broker 端口；可用 {@code -Dpaytech.test.rabbitmq.port} 指向同账号的另一个 Loopback Broker。 */
    public static final int PORT = Integer.getInteger("paytech.test.rabbitmq.port", 5672);

    private static final String DIRECTORY_PROPERTY = "paytech.local-environment.dir";

    private static final List<String> KEYS = List.of("MOR_RABBITMQ_USER", "MOR_RABBITMQ_PASSWORD", "MOR_RABBITMQ_VHOST");

    private final Map<String, String> values;

    private LocalRabbitEnvironment(Map<String, String> values) {
        this.values = values;
    }

    /**
     * 读取账号；任何缺失都以 AssertionError 失败。
     *
     * @return 账号
     */
    public static LocalRabbitEnvironment load() {
        String directory = System.getProperty(DIRECTORY_PROPERTY);
        if (directory == null || directory.isBlank()) {
            throw unavailable("system property " + DIRECTORY_PROPERTY + " is not set");
        }
        Path file = Path.of(directory, ".env.local");
        if (!Files.isRegularFile(file)) {
            throw unavailable(file + " does not exist");
        }
        Map<String, String> found = new HashMap<>();
        try {
            for (String line : Files.readAllLines(file, StandardCharsets.UTF_8)) {
                int separator = line.indexOf('=');
                if (separator > 0 && KEYS.contains(line.substring(0, separator).trim())) {
                    found.put(line.substring(0, separator).trim(), unquote(line.substring(separator + 1).trim()));
                }
            }
        } catch (IOException exception) {
            throw unavailable("cannot read " + file);
        }
        for (String key : KEYS) {
            if (found.getOrDefault(key, "").isBlank()) {
                throw unavailable(key + " is missing in " + file);
            }
        }
        return new LocalRabbitEnvironment(found);
    }

    /**
     * 用户名。
     *
     * @return 用户名
     */
    public String username() {
        return values.get("MOR_RABBITMQ_USER");
    }

    /**
     * 密码。
     *
     * @return 密码
     */
    public String password() {
        return values.get("MOR_RABBITMQ_PASSWORD");
    }

    /**
     * VHost。
     *
     * @return VHost
     */
    public String virtualHost() {
        return values.get("MOR_RABBITMQ_VHOST");
    }

    private static String unquote(String value) {
        if (value.length() >= 2 && (value.startsWith("\"") && value.endsWith("\"")
            || value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1);
        }
        return value;
    }

    private static AssertionError unavailable(String reason) {
        return new AssertionError("Local RabbitMQ is not configured: " + reason + ". Start the repository "
            + "local-environment base profile (docker compose --env-file local-environment/.env.local -f "
            + "local-environment/compose.yaml --profile base up -d rabbitmq) with the rabbitmq_consistent_hash_exchange "
            + "plugin enabled, and run Maven from paytech/ or pass -D" + DIRECTORY_PROPERTY + "=<repo>/local-environment.");
    }
}
