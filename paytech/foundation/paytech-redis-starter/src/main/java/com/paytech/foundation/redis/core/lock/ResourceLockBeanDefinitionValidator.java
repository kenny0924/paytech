package com.paytech.foundation.redis.core.lock;

import java.lang.reflect.Method;
import java.util.LinkedHashSet;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.transaction.annotation.Transactional;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/** 在代理实例化前拒绝确定不可代理或位于错误层次的声明。 */
public final class ResourceLockBeanDefinitionValidator implements BeanFactoryPostProcessor {

    private final ResourceLockMetadataCompiler compiler = new ResourceLockMetadataCompiler();

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        try {
            for (String beanName : beanFactory.getBeanDefinitionNames()) {
                Class<?> beanType = beanFactory.getType(beanName, false);
                if (beanType != null) {
                    validateBeanDefinitionLifecycle(beanFactory, beanName, beanType);
                    // @Bean 可按接口暴露；具体实现会在 BeanPostProcessor 和启动冻结阶段校验。
                    if (!beanType.isInterface()) {
                        validateTargetType(beanType);
                    }
                }
            }
        } catch (FailException failure) {
            throw new BeanCreationException("resourceLockBeanDefinitionValidator", failure);
        }
    }

    void validateTargetType(Class<?> beanType) {
        var validated = new LinkedHashSet<Method>();
        for (Method declaration : ResourceLockMethodIntrospector.declarations(beanType)) {
            ResourceLock[] annotations = declaration.getDeclaredAnnotationsByType(ResourceLock.class);
            if (annotations.length == 0) {
                continue;
            }
            Method method = ResourceLockMethodIntrospector.canonical(declaration, beanType);
            if (method.getDeclaringClass().isInterface()
                || method.getDeclaredAnnotationsByType(ResourceLock.class).length == 0) {
                throw invalid(beanType, method, "ResourceLock必须声明在具体实现方法");
            }
            if (!validated.add(method)) {
                continue;
            }
            validateInheritedLockSet(beanType, method);
            rejectWrongLayer(beanType, method);
            if (hasSpringTransaction(beanType, method)) {
                throw invalid(beanType, method, "ResourceLock方法不能同时声明@Transactional");
            }
            if (hasSpringAsync(beanType, method)) {
                throw invalid(beanType, method, "ResourceLock方法不能同时声明@Async");
            }
            compiler.compile(beanType, method);
        }
    }

    void validateBeanLifecycle(ConfigurableListableBeanFactory beanFactory, String beanName,
        Class<?> beanType) {
        if (!ResourceLockMethodIntrospector.hasResourceLocks(beanType)) {
            return;
        }
        if (beanFactory.isFactoryBean(beanName) && !beanFactory.isSingleton(beanName)) {
            Method method = firstResourceLockMethod(beanType);
            throw invalid(beanType, method, "ResourceLock目标不得使用scoped生命周期");
        }
        validateBeanDefinitionLifecycle(beanFactory, beanName, beanType);
    }

    private void validateBeanDefinitionLifecycle(ConfigurableListableBeanFactory beanFactory, String beanName,
        Class<?> beanType) {
        if (!ResourceLockMethodIntrospector.hasResourceLocks(beanType)) {
            return;
        }
        if (!beanFactory.containsBeanDefinition(beanName)) {
            return;
        }
        var definition = beanFactory.getBeanDefinition(beanName);
        Method method = firstResourceLockMethod(beanType);
        if (definition.isLazyInit()) {
            throw invalid(beanType, method, "ResourceLock目标不得声明lazy");
        }
        if (!definition.isSingleton()) {
            throw invalid(beanType, method, "ResourceLock目标不得使用scoped生命周期");
        }
    }

    private Method firstResourceLockMethod(Class<?> beanType) {
        return ResourceLockMethodIntrospector.resourceLockMethods(beanType).stream().findFirst()
            .orElseGet(() -> ResourceLockMethodIntrospector.declarations(beanType).stream()
                .filter(candidate -> candidate.getDeclaredAnnotationsByType(ResourceLock.class).length > 0)
                .findFirst().orElseThrow());
    }

    private void validateInheritedLockSet(Class<?> beanType, Method method) {
        var concrete = declarations(beanType, method, method.getDeclaredAnnotationsByType(ResourceLock.class),
            false);
        var inherited = new LinkedHashSet<ResourceLockMetadataCompiler.DeclarationIdentity>();
        for (Method declaration : ResourceLockMethodIntrospector.declarations(beanType)) {
            if (!declaration.equals(method)
                && ResourceLockMethodIntrospector.canonical(declaration, beanType).equals(method)) {
                inherited.addAll(declarations(beanType, declaration,
                    declaration.getDeclaredAnnotationsByType(ResourceLock.class), true));
            }
        }
        if (!concrete.containsAll(inherited)) {
            throw invalid(beanType, method, "override必须完整复写祖先ResourceLock声明");
        }
    }

    private LinkedHashSet<ResourceLockMetadataCompiler.DeclarationIdentity> declarations(Class<?> beanType,
        Method method, ResourceLock[] annotations, boolean rejectDuplicates) {
        var declarations = new LinkedHashSet<ResourceLockMetadataCompiler.DeclarationIdentity>();
        for (int index = 0; index < annotations.length; index++) {
            var identity = compiler.declarationIdentity(beanType, method, annotations[index], index + 1);
            if (!declarations.add(identity) && rejectDuplicates) {
                throw invalid(beanType, method, "祖先ResourceLock包含重复资源锁声明");
            }
        }
        return declarations;
    }

    private void rejectWrongLayer(Class<?> beanType, Method method) {
        if (AnnotatedElementUtils.hasAnnotation(beanType, Controller.class)) {
            throw invalid(beanType, method, "Controller不能声明ResourceLock");
        }
        if (AnnotatedElementUtils.hasAnnotation(beanType, Repository.class)) {
            throw invalid(beanType, method, "Repository不能声明ResourceLock");
        }
        if (beanType.getSimpleName().endsWith("Mapper")) {
            throw invalid(beanType, method, "Mapper不能声明ResourceLock");
        }
    }

    private boolean hasSpringTransaction(Class<?> beanType, Method method) {
        for (Class<?> type : ResourceLockMethodIntrospector.typeHierarchy(beanType)) {
            if (AnnotatedElementUtils.hasAnnotation(type, Transactional.class)) {
                return true;
            }
        }
        for (Method declaration : ResourceLockMethodIntrospector.declarations(beanType)) {
            if (ResourceLockMethodIntrospector.canonical(declaration, beanType).equals(method)
                && AnnotatedElementUtils.hasAnnotation(declaration, Transactional.class)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasSpringAsync(Class<?> beanType, Method method) {
        for (Class<?> type : ResourceLockMethodIntrospector.typeHierarchy(beanType)) {
            if (AnnotatedElementUtils.hasAnnotation(type, Async.class)) {
                return true;
            }
        }
        for (Method declaration : ResourceLockMethodIntrospector.declarations(beanType)) {
            if (ResourceLockMethodIntrospector.canonical(declaration, beanType).equals(method)
                && AnnotatedElementUtils.hasAnnotation(declaration, Async.class)) {
                return true;
            }
        }
        return false;
    }

    private FailException invalid(Class<?> beanType, Method method, String reason) {
        return new FailException(RedisErrorCode.CONFIGURATION_INVALID,
            "resource-lock bean=" + beanType.getName() + " method=" + method.toGenericString()
                + " annotation[1] " + reason);
    }
}
