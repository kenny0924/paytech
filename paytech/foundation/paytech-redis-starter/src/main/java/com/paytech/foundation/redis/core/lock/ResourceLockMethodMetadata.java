package com.paytech.foundation.redis.core.lock;

import java.lang.reflect.Method;
import java.util.List;

/** 一个具体实现方法的不可变资源锁元数据。 */
public record ResourceLockMethodMetadata(Method method, List<ResourceLockBinding> bindings) {

    public ResourceLockMethodMetadata {
        bindings = List.copyOf(bindings);
    }
}
