package com.paytech.foundation.redis.core.lock;

import java.util.Objects;

/** 已规范化且可交给 Redisson 的内部逻辑资源锁。 */
public final class ResolvedResourceLock {

    private final String prefix;
    private final String logicalKey;

    public ResolvedResourceLock(String prefix, String logicalKey) {
        this.prefix = Objects.requireNonNull(prefix, "prefix must not be null");
        this.logicalKey = Objects.requireNonNull(logicalKey, "logicalKey must not be null");
    }

    public String prefix() {
        return prefix;
    }

    public String logicalKey() {
        return logicalKey;
    }
}
