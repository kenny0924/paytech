package com.paytech.foundation.redis.auto.lock;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.aopalliance.intercept.MethodInterceptor;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.beans.factory.BeanCreationException;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.mock.env.MockEnvironment;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.autoconfigure.RedisCoordinationProperties;
import com.paytech.foundation.redis.core.autoconfigure.RedisNamespaceProperties;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.lock.ResourceLockBeanDefinitionValidator;
import com.paytech.foundation.redis.core.lock.ResourceLockMetadataCompiler;
import com.paytech.foundation.redis.core.lock.ResourceLockMetadataRegistry;
import com.paytech.foundation.redis.core.lock.ResourceLockKeyResolver;
import com.paytech.foundation.redis.core.lock.ResourceLockMethodInterceptor;
import com.paytech.foundation.redis.core.lock.ResourceLockObservation;
import com.paytech.foundation.redis.core.lock.ResourceLockOperations;
import com.paytech.foundation.redis.core.lock.ResourceLockExecutor;
import com.paytech.foundation.redis.core.lock.ResourceLockAdvisor;
import com.paytech.foundation.redis.core.lock.ResourceLockStartupValidator;

class ResourceLockStartupValidatorAutoTest {

    @Test
    void rejectsAnUnrelatedProxyThatDoesNotContainTheResourceLockAdvisor() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ProxyFactory proxyFactory = new ProxyFactory(new AnnotatedTarget());
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvice((MethodInterceptor)invocation -> invocation.proceed());
        AnnotatedTarget unrelatedProxy = (AnnotatedTarget)proxyFactory.getProxy();
        beanFactory.registerBeanDefinition("annotatedTarget",
            new RootBeanDefinition(AnnotatedTarget.class, () -> unrelatedProxy));
        beanFactory.registerSingleton("redissonClient", redissonMarker());
        beanFactory.preInstantiateSingletons();

        RedisCoordinationProperties coordination = new RedisCoordinationProperties();
        coordination.setEnabled(true);
        RedisResourceLockProperties lockProperties = new RedisResourceLockProperties();
        lockProperties.setWaitTime(java.time.Duration.ZERO);
        lockProperties.setLeaseTime(java.time.Duration.ofSeconds(1));
        ResourceLockMetadataRegistry registry = new ResourceLockMetadataRegistry();
        ResourceLockKeyResolver keyResolver = new ResourceLockKeyResolver();
        ResourceLockOperations operations = logicalKey -> null;
        ResourceLockObservation observation = new ResourceLockObservation(null);
        ResourceLockExecutor executor = new ResourceLockExecutor(operations, observation, 0L, 1L,
            System::nanoTime);
        ResourceLockMethodInterceptor interceptor = new ResourceLockMethodInterceptor(registry, keyResolver,
            executor);
        ResourceLockStartupValidator validator = new ResourceLockStartupValidator(beanFactory,
            new ResourceLockMetadataCompiler(), new ResourceLockBeanDefinitionValidator(),
            new com.paytech.foundation.redis.core.lock.ResourceLockLateBeanValidator(), registry, keyResolver,
            operations, observation, executor, interceptor, new ResourceLockAdvisor(interceptor), coordination,
            new RedisNamespaceProperties(), lockProperties,
            new MockEnvironment().withProperty("spring.application.name", "resource-lock-test"));

        assertThatThrownBy(validator::afterSingletonsInstantiated).isInstanceOf(BeanCreationException.class)
            .hasRootCauseInstanceOf(FailException.class).rootCause().hasMessageContaining("ResourceLockAdvisor");
    }

    @Test
    void rejectsACounterfeitResourceLockAdvisorAttachedToTheActualProxy() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        ResourceLockMetadataRegistry registry = new ResourceLockMetadataRegistry();
        ResourceLockKeyResolver keyResolver = new ResourceLockKeyResolver();
        ResourceLockOperations operations = logicalKey -> null;
        ResourceLockObservation observation = new ResourceLockObservation(null);
        ResourceLockExecutor executor = new ResourceLockExecutor(operations, observation, 0L, 1L,
            System::nanoTime);
        ResourceLockMethodInterceptor interceptor = new ResourceLockMethodInterceptor(registry, keyResolver,
            executor);
        ResourceLockAdvisor foundationAdvisor = new ResourceLockAdvisor(interceptor);

        ProxyFactory proxyFactory = new ProxyFactory(new AnnotatedTarget());
        proxyFactory.setProxyTargetClass(true);
        proxyFactory.addAdvisor(new ResourceLockAdvisor(interceptor));
        AnnotatedTarget counterfeitProxy = (AnnotatedTarget)proxyFactory.getProxy();
        beanFactory.registerBeanDefinition("annotatedTarget",
            new RootBeanDefinition(AnnotatedTarget.class, () -> counterfeitProxy));
        beanFactory.preInstantiateSingletons();

        ResourceLockStartupValidator validator = validator(beanFactory, registry, keyResolver, operations,
            observation, executor, interceptor, foundationAdvisor);

        assertThatThrownBy(validator::afterSingletonsInstantiated).isInstanceOf(BeanCreationException.class)
            .hasRootCauseInstanceOf(FailException.class).rootCause().hasMessageContaining("Foundation Advisor");
    }

    private ResourceLockStartupValidator validator(DefaultListableBeanFactory beanFactory,
        ResourceLockMetadataRegistry registry, ResourceLockKeyResolver keyResolver, ResourceLockOperations operations,
        ResourceLockObservation observation, ResourceLockExecutor executor, ResourceLockMethodInterceptor interceptor,
        ResourceLockAdvisor advisor) {
        RedisCoordinationProperties coordination = new RedisCoordinationProperties();
        coordination.setEnabled(true);
        RedisResourceLockProperties lockProperties = new RedisResourceLockProperties();
        lockProperties.setWaitTime(java.time.Duration.ZERO);
        lockProperties.setLeaseTime(java.time.Duration.ofSeconds(1));
        return new ResourceLockStartupValidator(beanFactory,
            new ResourceLockMetadataCompiler(), new ResourceLockBeanDefinitionValidator(),
            new com.paytech.foundation.redis.core.lock.ResourceLockLateBeanValidator(), registry, keyResolver,
            operations, observation, executor, interceptor, advisor, coordination, new RedisNamespaceProperties(),
            lockProperties, new MockEnvironment().withProperty("spring.application.name", "resource-lock-test"));
    }

    private RedissonClient redissonMarker() {
        return (RedissonClient)Proxy.newProxyInstance(RedissonClient.class.getClassLoader(),
            new Class<?>[] {RedissonClient.class}, (proxy, method, arguments) -> null);
    }

    static class AnnotatedTarget {
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }
}
