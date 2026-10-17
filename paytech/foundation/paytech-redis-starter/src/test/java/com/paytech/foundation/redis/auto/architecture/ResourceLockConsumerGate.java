package com.paytech.foundation.redis.auto.architecture;

import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.stream.BaseStream;

import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.support.AopUtils;
import org.springframework.asm.ClassReader;
import org.springframework.asm.ClassVisitor;
import org.springframework.asm.Handle;
import org.springframework.asm.MethodVisitor;
import org.springframework.asm.Opcodes;
import org.springframework.asm.Type;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.core.BridgeMethodResolver;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.lock.ResourceLockOperations;

/** Test-only template consumers can adapt to prevent proxy bypasses. */
final class ResourceLockConsumerGate {

    private ResourceLockConsumerGate() {}

    static void verify(Class<?>... types) throws IOException {
        Map<String, Set<String>> lockedMethods = lockedMethods(types);
        for (Class<?> type : types) {
            verifyType(type, lockedMethods);
        }
    }

    private static Map<String, Set<String>> lockedMethods(Class<?>[] types) {
        var result = new HashMap<String, Set<String>>();
        for (Class<?> type : types) {
            var methods = new HashSet<String>();
            for (Method declaration : declarations(type)) {
                if (declaration.getDeclaredAnnotationsByType(ResourceLock.class).length > 0) {
                    Method method = canonical(declaration, type);
                    if (method.getDeclaredAnnotationsByType(ResourceLock.class).length > 0) {
                        methods.add(method.getName() + Type.getMethodDescriptor(method));
                    }
                }
            }
            if (!methods.isEmpty()) {
                result.put(Type.getInternalName(type), Set.copyOf(methods));
            }
        }
        return result;
    }

    private static void verifyType(Class<?> type, Map<String, Set<String>> lockedMethods) throws IOException {
        rejectBusinessLockWrapper(type);
        var verified = new HashSet<Method>();
        for (Method declaration : declarations(type)) {
            if (declaration.getDeclaredAnnotationsByType(ResourceLock.class).length == 0) {
                continue;
            }
            Method method = canonical(declaration, type);
            if (method.getDeclaringClass().isInterface()
                || method.getDeclaredAnnotationsByType(ResourceLock.class).length == 0) {
                throw invalid(type, "ResourceLock必须在具体override方法重复声明: " + method.getName());
            }
            if (verified.add(method)) {
                verifyAnnotatedMethod(type, method);
            }
        }
        scanCalls(type, lockedMethods);
    }

    private static void rejectBusinessLockWrapper(Class<?> type) {
        for (var field : type.getDeclaredFields()) {
            Class<?> fieldType = field.getType();
            if (fieldType == RedissonClient.class || fieldType == RLock.class
                || fieldType == ResourceLockOperations.class) {
                throw invalid(type, "业务侧不得自建同义Redis锁包装");
            }
        }
    }

    private static void verifyAnnotatedMethod(Class<?> type, Method method) {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers) || Modifier.isStatic(modifiers) || Modifier.isFinal(modifiers)
            || Modifier.isFinal(type.getModifiers())) {
            throw invalid(type, "ResourceLock方法或类型不可代理: " + method.getName());
        }
        if (AnnotatedElementUtils.hasAnnotation(type, Controller.class)
            || AnnotatedElementUtils.hasAnnotation(type, Repository.class)
            || type.getSimpleName().endsWith("Mapper")) {
            throw invalid(type, "ResourceLock位于禁止层: " + method.getName());
        }
        if (AnnotatedElementUtils.hasAnnotation(type, Lazy.class)
            || AnnotatedElementUtils.hasAnnotation(method, Lazy.class)
            || hasNonSingletonScope(type) || hasNonSingletonScope(method)) {
            throw invalid(type, "ResourceLock目标不能是lazy/scoped: " + method.getName());
        }
        if (hasSpringTransaction(type, method)) {
            throw invalid(type, "ResourceLock不能与Transactional位于同一方法或类型");
        }
        if (isDelayedType(method.getReturnType()) || hasKotlinContinuation(method)) {
            throw invalid(type, "ResourceLock方法必须同步完成: " + method.getName());
        }
    }

    private static boolean hasNonSingletonScope(java.lang.reflect.AnnotatedElement element) {
        Scope scope = AnnotatedElementUtils.findMergedAnnotation(element, Scope.class);
        return scope != null && !scope.value().isEmpty() && !scope.value().equals("singleton");
    }

    private static boolean hasSpringTransaction(Class<?> type, Method method) {
        for (Class<?> candidate : typeHierarchy(type)) {
            if (AnnotatedElementUtils.hasAnnotation(candidate, Transactional.class)) {
                return true;
            }
        }
        for (Method declaration : declarations(type)) {
            if (canonical(declaration, type).equals(method)
                && AnnotatedElementUtils.hasAnnotation(declaration, Transactional.class)) {
                return true;
            }
        }
        return false;
    }

    private static boolean isDelayedType(Class<?> type) {
        return CompletionStage.class.isAssignableFrom(type) || Future.class.isAssignableFrom(type)
            || Flow.Publisher.class.isAssignableFrom(type)
            || BaseStream.class.isAssignableFrom(type)
            || implementsNamedInterface(type, "org.reactivestreams.Publisher")
            || implementsNamedInterface(type, "kotlinx.coroutines.flow.Flow");
    }

    private static boolean hasKotlinContinuation(Method method) {
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (implementsNamedInterface(parameterType, "kotlin.coroutines.Continuation")) {
                return true;
            }
        }
        return false;
    }

    private static boolean implementsNamedInterface(Class<?> type, String name) {
        if (type.getName().equals(name)) {
            return true;
        }
        for (Class<?> contract : type.getInterfaces()) {
            if (implementsNamedInterface(contract, name)) {
                return true;
            }
        }
        Class<?> parent = type.getSuperclass();
        return parent != null && implementsNamedInterface(parent, name);
    }

    private static Method canonical(Method method, Class<?> type) {
        return BridgeMethodResolver.findBridgedMethod(AopUtils.getMostSpecificMethod(method, type));
    }

    private static Set<Method> declarations(Class<?> type) {
        var methods = new HashSet<Method>();
        for (Class<?> candidate : typeHierarchy(type)) {
            methods.addAll(Set.of(candidate.getDeclaredMethods()));
        }
        return methods;
    }

    private static Set<Class<?>> typeHierarchy(Class<?> type) {
        var result = new HashSet<Class<?>>();
        collectTypes(type, result);
        return result;
    }

    private static void collectTypes(Class<?> type, Set<Class<?>> result) {
        if (type == null || type == Object.class || !result.add(type)) {
            return;
        }
        for (Class<?> contract : type.getInterfaces()) {
            collectTypes(contract, result);
        }
        collectTypes(type.getSuperclass(), result);
    }

    private static void scanCalls(Class<?> type, Map<String, Set<String>> lockedMethods) throws IOException {
        String callerOwner = Type.getInternalName(type);
        try (InputStream bytecode = type.getResourceAsStream("/" + callerOwner + ".class")) {
            if (bytecode == null) {
                throw invalid(type, "无法读取classfile");
            }
            new ClassReader(bytecode).accept(new ClassVisitor(Opcodes.ASM9) {
                @Override
                public MethodVisitor visitMethod(int access, String callerName, String callerDescriptor,
                    String signature, String[] exceptions) {
                    boolean callerLocked = lockedMethods.getOrDefault(callerOwner, Set.of())
                        .contains(callerName + callerDescriptor);
                    return new MethodVisitor(Opcodes.ASM9) {
                        private final Set<String> newlyCreatedOwners = new HashSet<>();

                        @Override
                        public void visitTypeInsn(int opcode, String createdType) {
                            if (opcode == Opcodes.NEW) {
                                newlyCreatedOwners.add(createdType);
                            }
                        }

                        @Override
                        public void visitMethodInsn(int opcode, String owner, String calleeName,
                            String calleeDescriptor, boolean isInterface) {
                            rejectLockCall(owner, calleeName, calleeDescriptor);
                        }

                        @Override
                        public void visitInvokeDynamicInsn(String name, String descriptor,
                            Handle bootstrapMethodHandle, Object... bootstrapMethodArguments) {
                            for (Object argument : bootstrapMethodArguments) {
                                if (argument instanceof Handle target) {
                                    rejectLockCall(target.getOwner(), target.getName(), target.getDesc());
                                }
                            }
                        }

                        private void rejectLockCall(String owner, String calleeName, String calleeDescriptor) {
                            String callee = calleeName + calleeDescriptor;
                            if (!lockedMethods.getOrDefault(owner, Set.of()).contains(callee)) {
                                return;
                            }
                            if (callerLocked) {
                                throw invalid(type, "带锁方法嵌套调用ResourceLock: " + callerName + " -> "
                                    + calleeName);
                            }
                            if (owner.equals(callerOwner)) {
                                throw invalid(type, "this自调用绕过ResourceLock: " + callerName + " -> "
                                    + calleeName);
                            }
                            if (newlyCreatedOwners.contains(owner)) {
                                throw invalid(type, "直接new实现类绕过ResourceLock: " + callerName + " -> "
                                    + calleeName);
                            }
                        }
                    };
                }
            }, ClassReader.SKIP_DEBUG | ClassReader.SKIP_FRAMES);
        }
    }

    private static AssertionError invalid(Class<?> type, String reason) {
        return new AssertionError(type.getName() + ": " + reason);
    }
}
