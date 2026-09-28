package com.paytech.foundation.redis.core.lock;

import java.lang.reflect.Method;

import org.springframework.aop.support.StaticMethodMatcherPointcut;

import com.paytech.foundation.redis.api.ResourceLock;

/** 只匹配具体目标方法直接声明的资源锁。 */
public final class ResourceLockPointcut extends StaticMethodMatcherPointcut {

    @Override
    public boolean matches(Method method, Class<?> targetClass) {
        Method specific = ResourceLockMethodIntrospector.canonical(method, targetClass);
        return specific.getDeclaredAnnotationsByType(ResourceLock.class).length > 0;
    }
}
