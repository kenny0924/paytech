package com.paytech.gateway.test.redis;

import java.util.Objects;
import java.util.UUID;

/** 一次 Gateway 自动测试运行拥有的 Redis 命名空间。 */
public final class RedisTestNamespace {
    private static final String PREFIX = "gateway:test:v1:";

    private final String guardPrefix;
    private final String namespace;
    private final String sentinelKey;

    private RedisTestNamespace(UUID runId) {
        UUID validatedRunId = Objects.requireNonNull(runId, "Redis 测试运行 ID 不可为空");
        namespace = PREFIX + validatedRunId;
        guardPrefix = namespace + ":guard:";
        sentinelKey = namespace + ":sentinel:" + validatedRunId;
        validateLiteral(namespace);
        validateLiteral(guardPrefix);
        validateLiteral(sentinelKey);
    }

    public static RedisTestNamespace from(UUID runId) {
        return new RedisTestNamespace(runId);
    }

    private static void validateLiteral(String value) {
        if (value.indexOf('*') >= 0 || value.indexOf('?') >= 0 || value.indexOf('[') >= 0 || value.indexOf(']') >= 0
            || value.indexOf('\\') >= 0) {
            throw new IllegalArgumentException("Redis 测试命名空间不可包含 Glob 字符");
        }
    }

    public String value() {
        return namespace;
    }

    public String guardPrefix() {
        return guardPrefix;
    }

    public String guardScanPattern() {
        return guardPrefix + "*";
    }

    public String sentinelKey() {
        return sentinelKey;
    }
}
