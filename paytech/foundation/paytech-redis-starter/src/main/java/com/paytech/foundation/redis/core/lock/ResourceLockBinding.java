package com.paytech.foundation.redis.core.lock;

/** 单个资源锁 Annotation 的已编译元数据。 */
public record ResourceLockBinding(int annotationIndex, String prefix, String expression, Class<?> valueType,
                                  ResourceLockValueAccessor accessor) {}
