package com.paytech.foundation.redis.core.lock;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Set;

import org.springframework.core.BridgeMethodResolver;
import org.springframework.aop.support.AopUtils;

import com.paytech.foundation.redis.api.ResourceLock;

/** 统一具体方法、bridge 方法及其类型层次的静态解析。 */
final class ResourceLockMethodIntrospector {

    private ResourceLockMethodIntrospector() {
    }

    static Method canonical(Method method, Class<?> targetClass) {
        Method specific = AopUtils.getMostSpecificMethod(method, targetClass);
        return BridgeMethodResolver.findBridgedMethod(specific);
    }

    static Set<Method> declarations(Class<?> type) {
        var methods = new LinkedHashSet<Method>();
        var interfaces = new LinkedHashSet<Class<?>>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            methods.addAll(Set.of(current.getDeclaredMethods()));
            collectInterfaces(current, interfaces);
        }
        for (Class<?> contract : interfaces) {
            methods.addAll(Set.of(contract.getDeclaredMethods()));
        }
        return methods;
    }

    static Set<Method> resourceLockMethods(Class<?> targetClass) {
        var methods = new LinkedHashSet<Method>();
        for (Method declaration : declarations(targetClass)) {
            if (declaration.getDeclaredAnnotationsByType(ResourceLock.class).length == 0) {
                continue;
            }
            Method canonical = canonical(declaration, targetClass);
            if (canonical.getDeclaredAnnotationsByType(ResourceLock.class).length > 0) {
                methods.add(canonical);
            }
        }
        return methods;
    }

    static boolean hasResourceLocks(Class<?> type) {
        for (Method method : declarations(type)) {
            if (method.getDeclaredAnnotationsByType(ResourceLock.class).length > 0) {
                return true;
            }
        }
        return false;
    }

    static Set<Class<?>> typeHierarchy(Class<?> type) {
        var types = new LinkedHashSet<Class<?>>();
        for (Class<?> current = type; current != null && current != Object.class; current = current.getSuperclass()) {
            types.add(current);
            collectInterfaces(current, types);
        }
        return types;
    }

    private static void collectInterfaces(Class<?> type, Set<Class<?>> result) {
        for (Class<?> contract : type.getInterfaces()) {
            if (result.add(contract)) {
                collectInterfaces(contract, result);
            }
        }
    }
}
