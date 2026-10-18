package com.paytech.foundation.redis.core.lock;

/** 启动期编译的受限参数读取器。 */
@FunctionalInterface
public interface ResourceLockValueAccessor {

    Object read(Object[] arguments) throws Throwable;
}
