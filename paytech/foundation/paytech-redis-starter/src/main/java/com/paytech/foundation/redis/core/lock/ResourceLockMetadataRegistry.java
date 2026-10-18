package com.paytech.foundation.redis.core.lock;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.Map;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/** 启动完成后冻结的资源锁方法元数据注册表。 */
public final class ResourceLockMetadataRegistry {

    private volatile Map<Method, ResourceLockMethodMetadata> metadata = Map.of();
    private boolean frozen;

    public synchronized void freeze(Map<Method, ResourceLockMethodMetadata> compiled) {
        if (frozen) {
            throw new FailException(RedisErrorCode.CONFIGURATION_INVALID, "resource-lock元数据已经冻结");
        }
        metadata = Map.copyOf(new LinkedHashMap<>(compiled));
        frozen = true;
    }

    public ResourceLockMethodMetadata required(Method method) {
        ResourceLockMethodMetadata result = metadata.get(method);
        if (result == null) {
            throw new FailException(RedisErrorCode.CONFIGURATION_INVALID,
                "resource-lock方法缺少启动期编译元数据: " + method.toGenericString());
        }
        return result;
    }

    synchronized boolean isFrozen() {
        return frozen;
    }
}
