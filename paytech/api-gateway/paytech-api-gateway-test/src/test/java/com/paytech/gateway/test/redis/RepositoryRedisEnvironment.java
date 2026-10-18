package com.paytech.gateway.test.redis;

import java.time.Duration;
import java.util.Objects;
import java.util.Properties;

import org.springframework.data.redis.connection.ReactiveRedisConnection;
import org.springframework.data.redis.connection.ReactiveRedisConnectionFactory;

import reactor.core.publisher.Mono;

/** 使用被测应用的 Redis 连接执行环境检查，不读取凭据或拥有客户端生命周期。 */
public final class RepositoryRedisEnvironment {
    private static final String START_INSTRUCTIONS = """
        请先启动仓库本地 Redis，并检查 application-auto.yml 的连接配置；
        若已有实例使用自定义密码，请在 Git 忽略的 application-auto.local.yml 中配置
        gateway.test.redis.local-password，或由安全来源提供 PAYTECH_REDIS_PASSWORD 覆盖。
        Redis 返回 WRONGPASS 表示凭据不匹配，不需要重启服务；仅未启动时执行：
        cd local-environment
        docker compose --env-file .env.local --profile base up -d redis
        """;

    private final ReactiveRedisConnectionFactory connectionFactory;
    private final String requiredVersion;
    private final Duration commandTimeout;

    public RepositoryRedisEnvironment(ReactiveRedisConnectionFactory connectionFactory, String requiredVersion,
        Duration commandTimeout) {
        this.connectionFactory = Objects.requireNonNull(connectionFactory, "Redis 连接工厂不可为空");
        this.requiredVersion = Objects.requireNonNull(requiredVersion, "Redis 版本约束不可为空");
        this.commandTimeout = Objects.requireNonNull(commandTimeout, "Redis 前置检查预算不可为空");
    }

    public ReactiveRedisConnectionFactory connectionFactory() {
        return connectionFactory;
    }

    public String requiredVersion() {
        return requiredVersion;
    }

    public String assertReady() {
        try {
            ServerState state = Mono
                .usingWhen(Mono.fromSupplier(() -> (ReactiveRedisConnection)connectionFactory.getReactiveConnection()),
                    connection -> connection.ping().zipWith(connection.serverCommands().info("server"),
                        ServerState::new),
                    connection -> connection.closeLater(), (connection, error) -> connection.closeLater(),
                    connection -> connection.closeLater())
                .timeout(commandTimeout).block(commandTimeout.plusSeconds(1));
            if (state == null || !"PONG".equals(state.ping())) {
                throw new IllegalStateException("Redis PING 未返回 PONG");
            }
            String version = state.serverInfo().getProperty("redis_version");
            if (!requiredVersion.equals(version)) {
                throw new IllegalStateException("Redis 版本必须为 " + requiredVersion + "，实际为 " + version);
            }
            return version;
        } catch (RuntimeException exception) {
            throw new AssertionError("Operations Gateway 测试 Redis 前置检查失败: " + exception.getClass().getSimpleName()
                + "。\n" + START_INSTRUCTIONS, exception);
        }
    }

    private record ServerState(String ping, Properties serverInfo) {
    }
}
