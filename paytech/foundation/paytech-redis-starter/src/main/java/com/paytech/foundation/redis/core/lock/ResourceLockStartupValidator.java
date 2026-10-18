package com.paytech.foundation.redis.core.lock;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;

import org.redisson.api.RedissonClient;
import org.springframework.aop.Advisor;
import org.springframework.aop.interceptor.AsyncExecutionInterceptor;
import org.springframework.aop.PointcutAdvisor;
import org.springframework.aop.framework.Advised;
import org.springframework.aop.support.AopUtils;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.BeanFactory;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.core.autoconfigure.RedisCoordinationProperties;
import com.paytech.foundation.redis.core.autoconfigure.RedisNamespaceProperties;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.coordination.FoundationRedissonClientDescriptor;
import com.paytech.foundation.redis.core.coordination.FoundationRedissonClientDescriptor.DeploymentMode;
import com.paytech.foundation.redis.core.error.RedisErrorCode;

/** 在全部单例和代理就绪后编译、验证并冻结资源锁元数据。 */
public final class ResourceLockStartupValidator implements SmartInitializingSingleton {

    private final ConfigurableListableBeanFactory beanFactory;
    private final ResourceLockMetadataCompiler compiler;
    private final ResourceLockBeanDefinitionValidator beanDefinitionValidator;
    private final ResourceLockLateBeanValidator lateBeanValidator;
    private final ResourceLockMetadataRegistry registry;
    private final ResourceLockKeyResolver keyResolver;
    private final ResourceLockOperations operations;
    private final ResourceLockObservation observation;
    private final ResourceLockExecutor executor;
    private final ResourceLockMethodInterceptor interceptor;
    private final ResourceLockAdvisor advisor;
    private final RedisCoordinationProperties coordinationProperties;
    private final RedisNamespaceProperties namespaceProperties;
    private final RedisResourceLockProperties lockProperties;
    private final Environment environment;

    public ResourceLockStartupValidator(ConfigurableListableBeanFactory beanFactory,
        ResourceLockMetadataCompiler compiler, ResourceLockBeanDefinitionValidator beanDefinitionValidator,
        ResourceLockLateBeanValidator lateBeanValidator, ResourceLockMetadataRegistry registry,
        ResourceLockKeyResolver keyResolver, ResourceLockOperations operations, ResourceLockObservation observation,
        ResourceLockExecutor executor, ResourceLockMethodInterceptor interceptor, ResourceLockAdvisor advisor,
        RedisCoordinationProperties coordinationProperties, RedisNamespaceProperties namespaceProperties,
        RedisResourceLockProperties lockProperties, Environment environment) {
        this.beanFactory = beanFactory;
        this.compiler = compiler;
        this.beanDefinitionValidator = beanDefinitionValidator;
        this.lateBeanValidator = lateBeanValidator;
        this.registry = registry;
        this.keyResolver = keyResolver;
        this.operations = operations;
        this.observation = observation;
        this.executor = executor;
        this.interceptor = interceptor;
        this.advisor = advisor;
        this.coordinationProperties = coordinationProperties;
        this.namespaceProperties = namespaceProperties;
        this.lockProperties = lockProperties;
        this.environment = environment;
    }

    @Override
    public void afterSingletonsInstantiated() {
        try {
            compileAndFreeze();
        } catch (FailException failure) {
            throw new BeanCreationException("resourceLockStartupValidator", failure);
        }
    }

    private void compileAndFreeze() {
        var compiled = new LinkedHashMap<Method, ResourceLockMethodMetadata>();
        var beanNames = new LinkedHashSet<String>(Arrays.asList(beanFactory.getBeanDefinitionNames()));
        beanNames.addAll(Arrays.asList(beanFactory.getSingletonNames()));
        for (String beanName : beanNames) {
            boolean factoryBean = beanFactory.isFactoryBean(beanName);
            if (factoryBean) {
                rejectResourceLockOnFactoryBean(beanName);
            }
            boolean initializedResourceLockTarget = lateBeanValidator.wasResourceLockTargetInitialized(beanName);
            Class<?> declaredType = beanFactory.getType(beanName, false);
            if (declaredType == null) {
                continue;
            }
            if (factoryBean && !initializedResourceLockTarget) {
                // containsSingleton(beanName) only proves the FactoryBean itself exists; never force a lazy product.
                rejectVisibleDeferredFactoryProduct(declaredType);
                continue;
            }
            if (!beanFactory.containsSingleton(beanName) && !initializedResourceLockTarget) {
                // lazy/scoped ResourceLock 目标由定义期校验或迟创建 BeanPostProcessor fail-closed，不能为扫描强制实例化。
                continue;
            }
            Object bean = beanFactory.getBean(beanName);
            Class<?> targetClass = AopUtils.getTargetClass(bean);
            beanDefinitionValidator.validateBeanLifecycle(beanFactory, beanName, targetClass);
            beanDefinitionValidator.validateTargetType(targetClass);
            for (Method method : ResourceLockMethodIntrospector.resourceLockMethods(targetClass)) {
                validateAdvisorChain(bean, targetClass, method);
                compiled.put(method, compiler.compile(targetClass, method));
            }
        }
        if (!compiled.isEmpty()) {
            String keyPrefix = namespaceProperties.resolveKeyPrefix(
                environment.getProperty("spring.application.name"));
            validatePrefixes(compiled, keyPrefix);
            validateExecutionChain();
            validateInfrastructure(keyPrefix);
        }
        registry.freeze(compiled);
    }

    private void validateExecutionChain() {
        requireUniqueFoundationBean("resourceLockMetadataCompiler", ResourceLockMetadataCompiler.class,
            ResourceLockMetadataCompiler.class, compiler);
        requireUniqueFoundationBean("resourceLockBeanDefinitionValidator", ResourceLockBeanDefinitionValidator.class,
            ResourceLockBeanDefinitionValidator.class, beanDefinitionValidator);
        requireUniqueFoundationBean("resourceLockLateBeanValidator", ResourceLockLateBeanValidator.class,
            ResourceLockLateBeanValidator.class, lateBeanValidator);
        requireUniqueFoundationBean("resourceLockMetadataRegistry", ResourceLockMetadataRegistry.class,
            ResourceLockMetadataRegistry.class, registry);
        requireUniqueFoundationBean("resourceLockKeyResolver", ResourceLockKeyResolver.class,
            ResourceLockKeyResolver.class, keyResolver);
        requireUniqueFoundationBean("resourceLockOperations", ResourceLockOperations.class,
            RedissonResourceLockOperations.class, operations);
        requireUniqueFoundationBean("resourceLockObservation", ResourceLockObservation.class,
            ResourceLockObservation.class, observation);
        requireUniqueFoundationBean("resourceLockExecutor", ResourceLockExecutor.class,
            ResourceLockExecutor.class, executor);
        requireUniqueFoundationBean("resourceLockMethodInterceptor", ResourceLockMethodInterceptor.class,
            ResourceLockMethodInterceptor.class, interceptor);
        requireUniqueFoundationBean("resourceLockAdvisor", ResourceLockAdvisor.class,
            ResourceLockAdvisor.class, advisor);
        if (!executor.uses(operations, observation)
            || !interceptor.uses(registry, keyResolver, executor)
            || advisor.getAdvice() != interceptor) {
            throw configurationError("ResourceLock内部执行链对象身份不一致");
        }
    }

    private <T> void requireUniqueFoundationBean(String beanName, Class<T> contract, Class<?> implementation,
        T expected) {
        String[] beanNames = beanFactory.getBeanNamesForType(contract, false, false);
        if (beanNames.length != 1 || !beanName.equals(beanNames[0])) {
            throw configurationError(contract.getSimpleName() + "执行链必须由唯一Foundation Bean提供");
        }
        Object actual = beanFactory.getBean(beanName);
        if (actual != expected || actual.getClass() != implementation) {
            throw configurationError(contract.getSimpleName() + "执行链不是Foundation固定实现");
        }
    }

    private void validatePrefixes(LinkedHashMap<Method, ResourceLockMethodMetadata> compiled, String keyPrefix) {
        for (ResourceLockMethodMetadata metadata : compiled.values()) {
            for (ResourceLockBinding binding : metadata.bindings()) {
                if (binding.prefix().equals(keyPrefix) || binding.prefix().startsWith(keyPrefix + ":")) {
                    throw configurationError("ResourceLock prefix不得重复包含服务Namespace: method="
                        + metadata.method().toGenericString() + " annotation[" + binding.annotationIndex() + "]");
                }
            }
        }
    }

    private void rejectResourceLockOnFactoryBean(String beanName) {
        Object singleton = beanFactory.getSingleton(beanName);
        Class<?> factoryType = singleton == null
            ? beanFactory.getType(BeanFactory.FACTORY_BEAN_PREFIX + beanName, false)
            : AopUtils.getTargetClass(singleton);
        if (factoryType == null || !ResourceLockMethodIntrospector.hasResourceLocks(factoryType)) {
            return;
        }
        Method method = ResourceLockMethodIntrospector.resourceLockMethods(factoryType).stream().findFirst()
            .orElseGet(() -> ResourceLockMethodIntrospector.declarations(factoryType).stream()
                .filter(candidate -> candidate.getDeclaredAnnotationsByType(
                    com.paytech.foundation.redis.api.ResourceLock.class).length > 0)
                .findFirst().orElseThrow());
        throw invalid(factoryType, method, "FactoryBean自身不能声明ResourceLock");
    }

    private void rejectVisibleDeferredFactoryProduct(Class<?> declaredType) {
        if (!ResourceLockMethodIntrospector.hasResourceLocks(declaredType)) {
            return;
        }
        beanDefinitionValidator.validateTargetType(declaredType);
        Method method = ResourceLockMethodIntrospector.resourceLockMethods(declaredType).stream().findFirst()
            .orElseGet(() -> ResourceLockMethodIntrospector.declarations(declaredType).stream()
                .filter(candidate -> candidate.getDeclaredAnnotationsByType(
                    com.paytech.foundation.redis.api.ResourceLock.class).length > 0)
                .findFirst().orElseThrow());
        throw invalid(declaredType, method,
            "FactoryBean product必须在Readiness前作为非lazy singleton初始化并完成代理校验");
    }

    private void validateAdvisorChain(Object bean, Class<?> targetClass, Method method) {
        if (!AopUtils.isAopProxy(bean) || !(bean instanceof Advised advised)) {
            throw invalid(targetClass, method, "Bean代理不包含适用的ResourceLockAdvisor");
        }
        if (AopUtils.isJdkDynamicProxy(bean) && !isExposedByProxyInterface(bean, targetClass, method)) {
            throw invalid(targetClass, method, "JDK代理接口未暴露ResourceLock方法");
        }
        Advisor[] advisors = advised.getAdvisors();
        int resourceLockIndex = -1;
        for (int index = 0; index < advisors.length; index++) {
            Advisor advisor = advisors[index];
            boolean applicable = applies(advisor, method, targetClass);
            if (advisor.getAdvice() instanceof AsyncExecutionInterceptor && applicable) {
                throw invalid(targetClass, method, "ResourceLock方法不能应用Async Advisor");
            }
            if (advisor.getAdvice() instanceof ResourceLockMethodInterceptor && applicable) {
                if (advisor != this.advisor || advisor.getAdvice() != interceptor
                    || !(advisor instanceof ResourceLockAdvisor)) {
                    throw invalid(targetClass, method,
                        "Bean实际代理链包含复用ResourceLock执行器的非Foundation Advisor");
                }
                if (resourceLockIndex >= 0) {
                    throw invalid(targetClass, method,
                        "Bean实际代理链重复包含Foundation ResourceLockAdvisor");
                }
                resourceLockIndex = index;
            } else if (advisor instanceof ResourceLockAdvisor && applicable) {
                throw invalid(targetClass, method,
                    "Bean实际代理链包含非Foundation Advisor");
            }
        }
        if (resourceLockIndex < 0) {
            throw invalid(targetClass, method, "Bean代理不包含适用的ResourceLockAdvisor");
        }
        for (int index = 0; index < resourceLockIndex; index++) {
            Advisor advisor = advisors[index];
            if (advisor.getAdvice() instanceof TransactionInterceptor && applies(advisor, method, targetClass)) {
                throw invalid(targetClass, method,
                    "ResourceLockAdvisor必须位于所有适用TransactionInterceptor外层，代理顺序无效");
            }
        }
    }

    private boolean isExposedByProxyInterface(Object bean, Class<?> targetClass, Method method) {
        for (Class<?> contract : bean.getClass().getInterfaces()) {
            for (Method candidate : contract.getMethods()) {
                if (ResourceLockMethodIntrospector.canonical(candidate, targetClass).equals(method)) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean applies(Advisor advisor, Method method, Class<?> targetClass) {
        if (!(advisor instanceof PointcutAdvisor pointcutAdvisor)) {
            return true;
        }
        var pointcut = pointcutAdvisor.getPointcut();
        return pointcut.getClassFilter().matches(targetClass)
            && pointcut.getMethodMatcher().matches(method, targetClass);
    }

    private void validateInfrastructure(String keyPrefix) {
        if (!coordinationProperties.isEnabled()) {
            throw configurationError("redis.coordination.enabled必须为true");
        }
        String[] redissonBeans = beanFactory.getBeanNamesForType(RedissonClient.class, true, false);
        if (redissonBeans.length != 1) {
            throw configurationError("ResourceLock要求唯一RedissonClient Bean");
        }
        RedissonClient client = beanFactory.getBean(redissonBeans[0], RedissonClient.class);
        validateFoundationClient(client, keyPrefix);
        validateNameMapper(client, keyPrefix);
        lockProperties.validateRequiredConfiguration();
        ((RedissonResourceLockOperations)operations).bindValidatedClient(client);
    }

    private void validateFoundationClient(RedissonClient client, String keyPrefix) {
        String[] descriptors = beanFactory.getBeanNamesForType(FoundationRedissonClientDescriptor.class,
            false, false);
        if (descriptors.length != 1) {
            throw configurationError("ResourceLock要求使用Foundation基于Spring Data Redis配置创建的RedissonClient");
        }
        FoundationRedissonClientDescriptor descriptor = beanFactory.getBean(descriptors[0],
            FoundationRedissonClientDescriptor.class);
        if (!descriptor.describes(client) || descriptor.deploymentMode() != DeploymentMode.SINGLE
            || !descriptor.keyPrefix().equals(keyPrefix)) {
            throw configurationError("ResourceLock的RedissonClient与Foundation连接描述不一致");
        }
    }

    private void validateNameMapper(RedissonClient client, String keyPrefix) {
        String probe = "resource-lock-namespace-probe";
        try {
            if (!RedissonResourceLockOperations.hasExpectedNameMapping(client, keyPrefix, probe)) {
                throw configurationError("RedissonClient NameMapper必须应用已配置的服务Key Namespace");
            }
        } catch (FailException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            throw configurationError("RedissonClient NameMapper无法验证服务Key Namespace");
        }
    }

    private FailException invalid(Class<?> beanType, Method method, String reason) {
        return new FailException(RedisErrorCode.CONFIGURATION_INVALID,
            "resource-lock bean=" + beanType.getName() + " method=" + method.toGenericString()
                + " annotation[1] " + reason);
    }

    private FailException configurationError(String message) {
        return new FailException(RedisErrorCode.CONFIGURATION_INVALID, message);
    }
}
