package com.paytech.foundation.redis.core.lock;

import java.beans.IntrospectionException;
import java.beans.Introspector;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.reflect.Parameter;
import java.lang.reflect.RecordComponent;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;
import java.util.Spliterator;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.BaseStream;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/** 把具体实现方法上的资源锁声明编译为运行期无需解释的元数据。 */
public final class ResourceLockMetadataCompiler {

    private static final Pattern PREFIX =
        Pattern.compile("[a-z][a-z0-9-]*(?::[a-z][a-z0-9-]*)*");
    private static final Pattern EXPRESSION =
        Pattern.compile("#([A-Za-z_$][A-Za-z0-9_$]*)(?:\\.([A-Za-z_$][A-Za-z0-9_$]*))?");
    private static final Set<Class<?>> SUPPORTED_TYPES = Set.of(String.class, byte.class, Byte.class, short.class,
        Short.class, int.class, Integer.class, long.class, Long.class, UUID.class);
    private static final Set<Class<?>> DEFERRED_JDK_CONTRACTS = Set.of(Runnable.class, Callable.class, Iterable.class,
        Iterator.class, Spliterator.class, Enumeration.class);

    public ResourceLockMethodMetadata compile(Class<?> beanType, Method method) {
        method = ResourceLockMethodIntrospector.canonical(method, beanType);
        ResourceLock[] annotations = method.getDeclaredAnnotationsByType(ResourceLock.class);
        if (annotations.length == 0) {
            return new ResourceLockMethodMetadata(method, java.util.List.of());
        }
        validateMethod(beanType, method, annotations[0]);
        var bindings = new ArrayList<ResourceLockBinding>(annotations.length);
        var declarations = new HashSet<DeclarationIdentity>();
        for (int index = 0; index < annotations.length; index++) {
            ResourceLock annotation = annotations[index];
            int annotationIndex = index + 1;
            DeclarationIdentity identity = declarationIdentity(beanType, method, annotation, annotationIndex);
            if (!declarations.add(identity)) {
                throw invalid(beanType, method, annotation, annotationIndex, "重复资源锁声明");
            }
            bindings.add(compileBinding(beanType, method, annotation, annotationIndex, identity));
        }
        return new ResourceLockMethodMetadata(method, bindings);
    }

    private ResourceLockBinding compileBinding(Class<?> beanType, Method method, ResourceLock annotation,
        int annotationIndex, DeclarationIdentity identity) {
        String expression = annotation.key();
        if (identity.parameterIndex() < 0) {
            return new ResourceLockBinding(annotationIndex, annotation.prefix(), expression, String.class,
                arguments -> null);
        }
        Parameter[] parameters = method.getParameters();
        int parameterIndex = identity.parameterIndex();
        String property = identity.property();
        if (property == null) {
            Class<?> type = parameters[parameterIndex].getType();
            validateType(beanType, method, annotation, annotationIndex, type);
            return new ResourceLockBinding(annotationIndex, annotation.prefix(), expression, type,
                arguments -> arguments[parameterIndex]);
        }
        Method accessor = findAccessor(parameters[parameterIndex].getType(), property);
        if (accessor == null) {
            throw invalid(beanType, method, annotation, annotationIndex, "一层属性没有公开只读访问器");
        }
        validateType(beanType, method, annotation, annotationIndex, accessor.getReturnType());
        return new ResourceLockBinding(annotationIndex, annotation.prefix(), expression, accessor.getReturnType(),
            arguments -> invoke(accessor, arguments[parameterIndex]));
    }

    DeclarationIdentity declarationIdentity(Class<?> beanType, Method method, ResourceLock annotation,
        int annotationIndex) {
        validatePrefix(beanType, method, annotation, annotationIndex);
        String expression = annotation.key();
        if (expression.isEmpty()) {
            return new DeclarationIdentity(annotation.prefix(), -1, null);
        }
        Matcher matcher = EXPRESSION.matcher(expression);
        if (!matcher.matches()) {
            throw invalid(beanType, method, annotation, annotationIndex, "表达式不符合受限语法");
        }
        Parameter[] parameters = method.getParameters();
        for (Parameter parameter : parameters) {
            if (!parameter.isNamePresent()) {
                throw invalid(beanType, method, annotation, annotationIndex, "缺少Java参数名元数据");
            }
        }
        int parameterIndex = findParameter(parameters, matcher.group(1));
        if (parameterIndex < 0) {
            throw invalid(beanType, method, annotation, annotationIndex, "表达式参数不存在");
        }
        return new DeclarationIdentity(annotation.prefix(), parameterIndex, matcher.group(2));
    }

    private void validateMethod(Class<?> beanType, Method method, ResourceLock annotation) {
        int modifiers = method.getModifiers();
        if (!Modifier.isPublic(modifiers)) {
            throw invalid(beanType, method, annotation, 1, "方法必须是public");
        }
        if (Modifier.isStatic(modifiers)) {
            throw invalid(beanType, method, annotation, 1, "方法不能是static");
        }
        if (Modifier.isFinal(modifiers)) {
            throw invalid(beanType, method, annotation, 1, "方法不能是final");
        }
        if (Modifier.isFinal(beanType.getModifiers())) {
            throw invalid(beanType, method, annotation, 1, "目标类不能是final");
        }
        if (isDelayedType(method.getReturnType())) {
            throw invalid(beanType, method, annotation, 1, "返回类型必须是同步结果");
        }
        for (Class<?> parameterType : method.getParameterTypes()) {
            if (implementsNamedInterface(parameterType, "kotlin.coroutines.Continuation")) {
                throw invalid(beanType, method, annotation, 1, "Kotlin suspend方法不是同步结果");
            }
        }
    }

    static boolean isDelayedType(Class<?> type) {
        return CompletionStage.class.isAssignableFrom(type) || Future.class.isAssignableFrom(type)
            || Flow.Publisher.class.isAssignableFrom(type)
            || BaseStream.class.isAssignableFrom(type)
            || DEFERRED_JDK_CONTRACTS.stream().anyMatch(contract -> contract.isAssignableFrom(type))
            || implementsInterfaceInPackage(type, "java.util.function")
            || implementsNamedInterface(type, "org.reactivestreams.Publisher")
            || implementsNamedInterface(type, "kotlinx.coroutines.flow.Flow");
    }

    private static boolean implementsInterfaceInPackage(Class<?> type, String packageName) {
        if (type.isInterface() && type.getPackageName().equals(packageName)) {
            return true;
        }
        for (Class<?> implemented : type.getInterfaces()) {
            if (implementsInterfaceInPackage(implemented, packageName)) {
                return true;
            }
        }
        Class<?> parent = type.getSuperclass();
        return parent != null && implementsInterfaceInPackage(parent, packageName);
    }

    private static boolean implementsNamedInterface(Class<?> type, String name) {
        if (type.getName().equals(name)) {
            return true;
        }
        for (Class<?> implemented : type.getInterfaces()) {
            if (implementsNamedInterface(implemented, name)) {
                return true;
            }
        }
        Class<?> parent = type.getSuperclass();
        return parent != null && implementsNamedInterface(parent, name);
    }

    private void validatePrefix(Class<?> beanType, Method method, ResourceLock annotation, int annotationIndex) {
        String prefix = annotation.prefix();
        if (prefix.length() > 128 || !PREFIX.matcher(prefix).matches() || !prefix.chars().allMatch(value -> value < 128)) {
            throw invalid(beanType, method, annotation, annotationIndex, "prefix格式无效");
        }
    }

    private void validateType(Class<?> beanType, Method method, ResourceLock annotation, int annotationIndex,
        Class<?> type) {
        if (!SUPPORTED_TYPES.contains(type)) {
            throw invalid(beanType, method, annotation, annotationIndex, "表达式静态类型不受支持");
        }
    }

    private int findParameter(Parameter[] parameters, String name) {
        for (int index = 0; index < parameters.length; index++) {
            if (parameters[index].getName().equals(name)) {
                return index;
            }
        }
        return -1;
    }

    private Method findAccessor(Class<?> type, String property) {
        if (type.isRecord()) {
            for (RecordComponent component : type.getRecordComponents()) {
                if (component.getName().equals(property)) {
                    return publicNoArg(component.getAccessor());
                }
            }
        }
        try {
            for (var descriptor : Introspector.getBeanInfo(type).getPropertyDescriptors()) {
                if (descriptor.getName().equals(property)) {
                    return publicNoArg(descriptor.getReadMethod());
                }
            }
            return null;
        } catch (IntrospectionException exception) {
            return null;
        }
    }

    private Method publicNoArg(Method method) {
        if (method == null || !Modifier.isPublic(method.getModifiers()) || method.getParameterCount() != 0) {
            return null;
        }
        try {
            return method.trySetAccessible() ? method : null;
        } catch (SecurityException failure) {
            return null;
        }
    }

    private Object invoke(Method accessor, Object target) throws Throwable {
        if (target == null) {
            return null;
        }
        try {
            return accessor.invoke(target);
        } catch (InvocationTargetException exception) {
            throw exception.getCause();
        }
    }

    private FailException invalid(Class<?> beanType, Method method, ResourceLock annotation, int annotationIndex,
        String reason) {
        String message = "resource-lock bean=" + beanType.getName() + " method=" + method.toGenericString()
            + " annotation[" + annotationIndex + "] prefix=" + annotation.prefix() + " expression="
            + annotation.key() + " " + reason;
        return new FailException(RedisErrorCode.CONFIGURATION_INVALID, message);
    }

    record DeclarationIdentity(String prefix, int parameterIndex, String property) {}
}
