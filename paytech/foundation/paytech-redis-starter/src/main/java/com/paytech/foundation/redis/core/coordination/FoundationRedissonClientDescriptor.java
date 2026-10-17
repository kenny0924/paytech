package com.paytech.foundation.redis.core.coordination;

import java.util.Objects;

import org.redisson.api.RedissonClient;

/**
 * 证明 RedissonClient 由 Foundation 依据 Spring Data Redis 配置创建的内部描述。
 *
 * <p>
 * 描述不保存地址、凭据或 SSL 材料；对象身份和 Namespace 足以证明客户端经过了 Foundation
 * 的单机模式与连接参数校验路径。
 */
public final class FoundationRedissonClientDescriptor {

    public enum DeploymentMode {
        SINGLE
    }

    private final RedissonClient client;
    private final String keyPrefix;

    public FoundationRedissonClientDescriptor(RedissonClient client, String keyPrefix) {
        this.client = Objects.requireNonNull(client, "client");
        this.keyPrefix = Objects.requireNonNull(keyPrefix, "keyPrefix");
    }

    public boolean describes(RedissonClient candidate) {
        return client == candidate;
    }

    public DeploymentMode deploymentMode() {
        return DeploymentMode.SINGLE;
    }

    public String keyPrefix() {
        return keyPrefix;
    }
}
