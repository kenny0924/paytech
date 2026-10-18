package com.paytech.foundation.redis.core.coordination;

import java.time.Duration;
import java.util.function.Consumer;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.client.codec.StringCodec;
import org.redisson.config.Config;
import org.redisson.config.SingleServerConfig;
import org.springframework.boot.data.redis.autoconfigure.DataRedisConnectionDetails;
import org.springframework.boot.data.redis.autoconfigure.DataRedisProperties;
import org.springframework.boot.ssl.SslBundle;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.autoconfigure.RedisCoordinationProperties;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/**
 * 根据 Spring Data Redis 连接配置创建仅用于协调能力的 Redisson 客户端。
 *
 * @author Codex
 */
public class RedissonClientFactory {

    private static final long MAXIMUM_TIMEOUT_MILLIS = Integer.MAX_VALUE;

    /**
     * 创建 Redisson 客户端。
     *
     * @param connectionDetails
     *            Spring Data Redis 解析后的连接信息
     * @param redisProperties
     *            Spring Data Redis 客户端参数
     * @param coordinationProperties
     *            Redisson 协调资源参数
     * @param keyPrefix
     *            已解析的服务级 Key 前缀
     * @return Redisson 客户端
     */
    public RedissonClient create(DataRedisConnectionDetails connectionDetails, DataRedisProperties redisProperties,
        RedisCoordinationProperties coordinationProperties, String keyPrefix) {
        Config config = createConfiguration(connectionDetails, redisProperties, coordinationProperties, keyPrefix);
        try {
            return Redisson.create(config);
        } catch (RuntimeException exception) {
            throw new ErrorException(RedisErrorCode.CONNECTION_UNAVAILABLE, "Redis协调连接创建失败", exception);
        }
    }

    Config createConfiguration(DataRedisConnectionDetails connectionDetails, DataRedisProperties redisProperties,
        RedisCoordinationProperties coordinationProperties, String keyPrefix) {
        coordinationProperties.validateEnabledConfiguration();
        validateStandaloneMode(connectionDetails);

        Config config = new Config();
        config.setCodec(StringCodec.INSTANCE);
        config.setLazyInitialization(false);
        config.setThreads(coordinationProperties.getThreads());
        config.setNettyThreads(coordinationProperties.getNettyThreads());
        config.setNameMapper(new RedisKeyPrefixNameMapper(keyPrefix));

        SingleServerConfig serverConfig =
            configureSingleServer(config, connectionDetails, redisProperties, coordinationProperties);
        applyClientSettings(config, serverConfig, connectionDetails, redisProperties);
        applySsl(config, connectionDetails.getSslBundle());
        return config;
    }

    private SingleServerConfig configureSingleServer(Config config, DataRedisConnectionDetails connectionDetails,
        DataRedisProperties redisProperties, RedisCoordinationProperties coordinationProperties) {
        DataRedisConnectionDetails.Standalone standalone = connectionDetails.getStandalone();
        String host = requireHost(standalone.getHost());
        int port = requirePort(standalone.getPort());
        int database = requireDatabase(standalone.getDatabase());
        String scheme = usesSsl(connectionDetails, redisProperties) ? "rediss" : "redis";

        return config.useSingleServer().setAddress(scheme + "://" + formatHost(host) + ":" + port).setDatabase(database)
            .setConnectionMinimumIdleSize(coordinationProperties.getConnectionMinimumIdleSize())
            .setConnectionPoolSize(coordinationProperties.getConnectionPoolSize())
            .setSubscriptionConnectionMinimumIdleSize(coordinationProperties.getSubscriptionConnectionMinimumIdleSize())
            .setSubscriptionConnectionPoolSize(coordinationProperties.getSubscriptionConnectionPoolSize());
    }

    private void applyClientSettings(Config config, SingleServerConfig serverConfig,
        DataRedisConnectionDetails connectionDetails, DataRedisProperties redisProperties) {
        setIfPresent(config::setUsername, connectionDetails.getUsername());
        setIfPresent(config::setPassword, connectionDetails.getPassword());
        setIfPresent(serverConfig::setClientName, redisProperties.getClientName());
        setRequiredTimeout("spring.data.redis.connect-timeout", redisProperties.getConnectTimeout(),
            serverConfig::setConnectTimeout);
        setRequiredTimeout("spring.data.redis.timeout", redisProperties.getTimeout(), serverConfig::setTimeout);
    }

    private void applySsl(Config config, SslBundle sslBundle) {
        if (sslBundle == null) {
            return;
        }
        config.setSslKeyManagerFactory(sslBundle.getManagers().getKeyManagerFactory());
        config.setSslTrustManagerFactory(sslBundle.getManagers().getTrustManagerFactory());
        if (sslBundle.getOptions().getCiphers() != null) {
            config.setSslCiphers(sslBundle.getOptions().getCiphers());
        }
        if (sslBundle.getOptions().getEnabledProtocols() != null) {
            config.setSslProtocols(sslBundle.getOptions().getEnabledProtocols());
        }
    }

    private boolean usesSsl(DataRedisConnectionDetails connectionDetails, DataRedisProperties redisProperties) {
        if (connectionDetails.getSslBundle() != null) {
            return true;
        }
        String redisUrl = redisProperties.getUrl();
        return redisUrl != null && redisUrl.regionMatches(true, 0, "rediss://", 0, "rediss://".length());
    }

    private void validateStandaloneMode(DataRedisConnectionDetails connectionDetails) {
        boolean alternativeMode = connectionDetails.getCluster() != null || connectionDetails.getSentinel() != null
            || connectionDetails.getMasterReplica() != null;
        if (alternativeMode || connectionDetails.getStandalone() == null) {
            throw new FailException(RedisErrorCode.DEPLOYMENT_MODE_UNSUPPORTED, "Redisson协调能力当前只支持Redis单机模式");
        }
    }

    private String requireHost(String host) {
        if (host == null || host.isBlank()) {
            throw configurationError("spring.data.redis.host不能为空");
        }
        return host.trim();
    }

    private int requirePort(int port) {
        if (port < 1 || port > 65535) {
            throw configurationError("spring.data.redis.port必须在1到65535之间");
        }
        return port;
    }

    private int requireDatabase(int database) {
        if (database < 0) {
            throw configurationError("spring.data.redis.database不能小于0");
        }
        return database;
    }

    private String formatHost(String host) {
        if (host.indexOf(':') >= 0 && !host.startsWith("[") && !host.endsWith("]")) {
            return "[" + host + "]";
        }
        return host;
    }

    private void setIfPresent(Consumer<String> setter, String value) {
        if (value != null && !value.isBlank()) {
            setter.accept(value);
        }
    }

    private void setRequiredTimeout(String property, Duration duration, Consumer<Integer> setter) {
        if (duration == null) {
            throw configurationError(property + "必须显式配置");
        }
        long milliseconds = duration.toMillis();
        if (milliseconds < 1 || milliseconds > MAXIMUM_TIMEOUT_MILLIS) {
            throw configurationError(property + "必须在1毫秒到" + MAXIMUM_TIMEOUT_MILLIS + "毫秒之间");
        }
        setter.accept((int)milliseconds);
    }

    private FailException configurationError(String message) {
        return new FailException(RedisErrorCode.CONFIGURATION_INVALID, message);
    }
}
