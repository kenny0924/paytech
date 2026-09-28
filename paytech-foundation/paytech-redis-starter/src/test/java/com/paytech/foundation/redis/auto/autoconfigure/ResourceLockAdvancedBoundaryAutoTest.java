package com.paytech.foundation.redis.auto.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Method;
import java.util.concurrent.Flow;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.springframework.aop.Advisor;
import org.springframework.aop.interceptor.AsyncExecutionInterceptor;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.aop.support.StaticMethodMatcherPointcut;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Role;
import org.springframework.context.annotation.Scope;
import org.springframework.core.Ordered;
import org.springframework.beans.factory.FactoryBean;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.transaction.TransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.interceptor.MatchAlwaysTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockAutoConfiguration;
import com.paytech.foundation.redis.core.coordination.FoundationRedissonClientDescriptor;
import com.paytech.foundation.redis.core.lock.ResourceLockExecutor;
import com.paytech.foundation.redis.core.lock.ResourceLockKeyResolver;
import com.paytech.foundation.redis.core.lock.ResourceLockMetadataRegistry;
import com.paytech.foundation.redis.core.lock.ResourceLockMethodInterceptor;
import com.paytech.foundation.redis.test.fake.InMemoryRedissonClient;

import kotlin.coroutines.Continuation;

class ResourceLockAdvancedBoundaryAutoTest {

    @BeforeEach
    void resetLazyCounters() {
        LazyHiddenConfiguration.creations.set(0);
        LazyHiddenConfiguration.businessCalls.set(0);
        VisibleLazyConfiguration.creations.set(0);
        NonEagerFactoryConfiguration.creations.set(0);
        DeferredConcreteFactoryConfiguration.creations.set(0);
    }

    @Test
    void rejectsOverriddenAncestorAnnotationWhenConcreteMethodDoesNotRepeatIt() {
        assertStartupFailure(OverriddenAncestorConfiguration.class, "具体实现方法");
    }

    @Test
    void proxiesAnInheritedConcreteMethodThatWasNotOverridden() {
        validRunner(InheritedConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(InheritedChild.class).locked()).isEqualTo("parent");
            assertThat(context.getBean(InMemoryRedissonClient.class).operations().events()).containsExactly(
                "lock:operations:catalog", "acquire:operations:catalog", "release:operations:catalog");
        });
    }

    @Test
    void rejectsConcreteOverrideOfAnnotatedAbstractParentWithoutRepeatedAnnotation() {
        assertStartupFailure(AbstractOverrideConfiguration.class, "具体实现方法");
    }

    @Test
    void rejectsAnOverrideThatRepeatsOnlyPartOfTheAncestorLockSet() {
        assertStartupFailure(PartialRepeatedLocksConfiguration.class, "完整复写");
    }

    @Test
    void acceptsAnEquivalentAncestorLockWhenTheOverrideRenamesItsParameter() {
        validRunner(RenamedParameterOverrideConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(RenamedParameterOverride.class).locked("account-7"))
                .isEqualTo("account-7");
            assertThat(context.getBean(InMemoryRedissonClient.class).operations().events()).containsExactly(
                "lock:operations:account:account-7", "acquire:operations:account:account-7",
                "release:operations:account:account-7");
        });
    }

    @Test
    void acceptsAnInterfaceReturnedBeanWhenTheConcreteOverrideRepeatsTheLock() {
        validRunner(InterfaceReturnedRepeatedLockConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(AopUtils.isJdkDynamicProxy(context.getBean(RepeatedLockContract.class))).isTrue();
            assertThat(context.getBean(RepeatedLockContract.class).locked("account-8")).isEqualTo("account-8");
            assertThat(context.getBean(InMemoryRedissonClient.class).operations().events()).containsExactly(
                "lock:operations:account:account-8", "acquire:operations:account:account-8",
                "release:operations:account:account-8");
        });
    }

    @Test
    void rejectsTwoAncestorLocksWhoseSameParameterNameRefersToDifferentPositions() {
        assertStartupFailure(AmbiguousInterfaceParameterConfiguration.class, "完整复写");
    }

    @Test
    void rejectsAsyncVoidEvenThoughItsDeclaredReturnTypeLooksSynchronous() {
        assertStartupFailure(AsyncVoidConfiguration.class, "Async");
    }

    @Test
    void rejectsAnApplicableAsyncAdvisorEvenWhenItIsInsideTheResourceLockAdvisor() {
        assertStartupFailure(InnerAsyncAdvisorConfiguration.class, "Async Advisor");
    }

    @Test
    void rejectsSpringTransactionalDeclaredOnTheInterfaceContract() {
        assertStartupFailure(InterfaceTransactionalConfiguration.class, "Transactional");
    }

    @Test
    void rejectsAResourceLockAdvisorPlacedInsideAnApplicableTransactionInterceptor() {
        assertStartupFailure(WrongAdvisorOrderConfiguration.class, "顺序");
    }

    @Test
    void rejectsAnotherAdvisorThatReusesTheFoundationResourceLockInterceptor() {
        assertStartupFailure(DuplicateInterceptorAdvisorConfiguration.class, "Foundation Advisor");
    }

    @Test
    void rejectsAnotherAdvisorThatCreatesACounterfeitResourceLockInterceptor() {
        assertStartupFailure(CounterfeitInterceptorAdvisorConfiguration.class, "Foundation Advisor");
    }

    @Test
    void rejectsFlowPublisherAndKotlinContinuationSignatures() {
        assertStartupFailure(FlowPublisherConfiguration.class, "同步");
        assertStartupFailure(KotlinContinuationConfiguration.class, "同步");
        assertStartupFailure(KotlinFlowConfiguration.class, "同步");
    }

    @Test
    @SuppressWarnings("unchecked")
    void canonicalizesBridgeMethodsOnARealSpringProxy() {
        validRunner(GenericBridgeConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(AopUtils.isJdkDynamicProxy(context.getBean(GenericContract.class))).isTrue();
            assertThat(context.getBean(GenericContract.class).locked("account-7")).isEqualTo("account-7");
            assertThat(context.getBean(InMemoryRedissonClient.class).operations().events()).containsExactly(
                "lock:operations:account:account-7", "acquire:operations:account:account-7",
                "release:operations:account:account-7");
        });
    }

    @Test
    void doesNotInstantiateHiddenLazyTargetAtStartupButRejectsItBeforeBusinessExecution() {
        validRunner(LazyHiddenConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(LazyHiddenConfiguration.creations).hasValue(0);

            org.assertj.core.api.Assertions.assertThatThrownBy(() -> context.getBean(LazyContract.class))
                .isInstanceOf(Exception.class).hasRootCauseInstanceOf(FailException.class)
                .rootCause().hasMessageContaining("lazy");
            assertThat(LazyHiddenConfiguration.creations).hasValue(1);
            assertThat(LazyHiddenConfiguration.businessCalls).hasValue(0);
        });
    }

    @Test
    void rejectsStaticallyVisibleLazyAndScopedTargetsWithoutInstantiatingThem() {
        assertStartupFailure(VisibleLazyConfiguration.class, "lazy");
        assertThat(VisibleLazyConfiguration.creations).hasValue(0);
        assertStartupFailure(ScopedConfiguration.class, "scoped");
    }

    @Test
    void acceptsAnExplicitSingletonScope() {
        validRunner(ExplicitSingletonConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context.getBean(DeferredLockService.class).locked()).isEqualTo("locked");
        });
    }

    @Test
    void rejectsAnAnnotatedRawSingletonRegisteredWithoutBeanDefinitionOrProxying() {
        validRunner(ExplicitSingletonConfiguration.class)
            .withInitializer(context -> context.getBeanFactory().registerSingleton("manualLockedService",
                new ManualLockedService()))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                    .rootCause().hasMessageContaining("ResourceLockAdvisor");
            });
    }

    @Test
    void rejectsAnAnnotatedRawFactoryBeanRegisteredWithoutProxying() {
        validRunner(ExplicitSingletonConfiguration.class)
            .withInitializer(context -> context.getBeanFactory().registerSingleton("manualLockedFactory",
                new ManualLockedFactory()))
            .run(context -> {
                assertThat(context).hasFailed();
                assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                    .rootCause().hasMessageContaining("FactoryBean");
            });
    }

    @Test
    void rejectsAPrototypeWhoseDeclaredInterfaceHidesItsAnnotatedImplementation() {
        assertStartupFailure(HiddenPrototypeConfiguration.class, "scoped");
    }

    @Test
    void rejectsAConcreteOnlyLockMethodThatAJdkProxyCannotExpose() {
        assertStartupFailure(JdkHiddenMethodConfiguration.class, "代理接口");
    }

    @Test
    void doesNotInstantiateAnUnrelatedNonEagerFactoryBeanProduct() {
        validRunner(NonEagerFactoryConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(NonEagerFactoryConfiguration.creations).hasValue(0);
        });
    }

    @Test
    void rejectsAnAnnotatedNonSingletonFactoryBeanProductDuringRefresh() {
        assertStartupFailure(NonSingletonLockedFactoryConfiguration.class, "scoped");
    }

    @Test
    void rejectsAStaticallyVisibleAnnotatedFactoryProductWithoutCreatingIt() {
        assertStartupFailure(DeferredConcreteFactoryConfiguration.class, "FactoryBean product");
        assertThat(DeferredConcreteFactoryConfiguration.creations).hasValue(0);
    }

    @Test
    void doesNotForceUnrelatedApplicationAdvisorsFromJdkToCglibOrRequireRedis() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisResourceLockAutoConfiguration.class))
            .withUserConfiguration(NoLockJdkAdvisorConfiguration.class)
            .withPropertyValues("spring.application.name=resource-lock-test")
            .run(context -> {
                assertThat(context).hasNotFailed();
                Object bean = context.getBean(PlainContract.class);
                assertThat(AopUtils.isAopProxy(bean)).as("bean type %s", bean.getClass()).isTrue();
                assertThat(AopUtils.isJdkDynamicProxy(bean)).as("bean type %s", bean.getClass()).isTrue();
                assertThat(((PlainContract)bean).plain()).isEqualTo("plain");
                assertThat(context).doesNotHaveBean(RedissonClient.class);
            });
    }

    private ApplicationContextRunner validRunner(Class<?> configuration) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisResourceLockAutoConfiguration.class))
            .withUserConfiguration(TestInfrastructureConfiguration.class, configuration)
            .withPropertyValues("spring.application.name=resource-lock-test", "redis.coordination.enabled=true",
                "redis.coordination.resource-lock.wait-time=500ms",
                "redis.coordination.resource-lock.lease-time=30s");
    }

    private void assertStartupFailure(Class<?> configuration, String message) {
        validRunner(configuration).run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                .rootCause().hasMessageContaining(message);
        });
    }

    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructureConfiguration {
        @Bean
        InMemoryRedissonClient inMemoryRedissonClient() {
            return new InMemoryRedissonClient("resource-lock-test");
        }

        @Bean
        RedissonClient redissonClient(InMemoryRedissonClient inMemoryRedissonClient) {
            return inMemoryRedissonClient.client();
        }

        @Bean
        FoundationRedissonClientDescriptor foundationRedissonClientDescriptor(RedissonClient redissonClient) {
            return new FoundationRedissonClientDescriptor(redissonClient, "resource-lock-test");
        }

    }

    static class AnnotatedParent {
        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            return "parent";
        }
    }

    static class OverriddenChild extends AnnotatedParent {
        @Override
        public String locked() {
            return "child";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class OverriddenAncestorConfiguration {
        @Bean
        OverriddenChild overriddenChild() {
            return new OverriddenChild();
        }
    }

    static class InheritedChild extends AnnotatedParent {
    }

    interface RepeatedLockContract {
        @ResourceLock(prefix = "operations:account", key = "#accountId")
        String locked(String accountId);
    }

    static class InterfaceReturnedRepeatedLockService implements RepeatedLockContract {
        @Override
        @ResourceLock(prefix = "operations:account", key = "#accountId")
        public String locked(String accountId) {
            return accountId;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InterfaceReturnedRepeatedLockConfiguration {
        @Bean
        RepeatedLockContract repeatedLockContract() {
            return new InterfaceReturnedRepeatedLockService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InheritedConfiguration {
        @Bean
        InheritedChild inheritedChild() {
            return new InheritedChild();
        }
    }

    abstract static class AnnotatedAbstractParent {
        @ResourceLock(prefix = "operations:catalog")
        public abstract String locked();
    }

    static class AbstractOverrideChild extends AnnotatedAbstractParent {
        @Override
        public String locked() {
            return "child";
        }
    }

    static class MultipleLockParent {
        @ResourceLock(prefix = "operations:account", key = "#accountId")
        @ResourceLock(prefix = "operations:catalog")
        public String locked(String accountId) {
            return accountId;
        }
    }

    static class PartialRepeatedLocksChild extends MultipleLockParent {
        @Override
        @ResourceLock(prefix = "operations:account", key = "#accountId")
        public String locked(String accountId) {
            return accountId;
        }
    }

    static class RenamedParameterParent {
        @ResourceLock(prefix = "operations:account", key = "#id")
        public String locked(String id) {
            return id;
        }
    }

    static class RenamedParameterOverride extends RenamedParameterParent {
        @Override
        @ResourceLock(prefix = "operations:account", key = "#accountId")
        public String locked(String accountId) {
            return accountId;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class RenamedParameterOverrideConfiguration {
        @Bean
        RenamedParameterOverride renamedParameterOverride() {
            return new RenamedParameterOverride();
        }
    }

    interface FirstParameterLockContract {
        @ResourceLock(prefix = "operations:account", key = "#id")
        String locked(String id, String ignored);
    }

    interface SecondParameterLockContract {
        @ResourceLock(prefix = "operations:account", key = "#id")
        String locked(String ignored, String id);
    }

    static class AmbiguousInterfaceParameterService
        implements FirstParameterLockContract, SecondParameterLockContract {
        @Override
        @ResourceLock(prefix = "operations:account", key = "#id")
        public String locked(String id, String ignored) {
            return id;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AmbiguousInterfaceParameterConfiguration {
        @Bean
        AmbiguousInterfaceParameterService ambiguousInterfaceParameterService() {
            return new AmbiguousInterfaceParameterService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PartialRepeatedLocksConfiguration {
        @Bean
        PartialRepeatedLocksChild partialRepeatedLocksChild() {
            return new PartialRepeatedLocksChild();
        }
    }

    interface AsyncContract {
        @Async
        void locked();
    }

    static class AsyncVoidService implements AsyncContract {
        @Override
        @ResourceLock(prefix = "operations:catalog")
        public void locked() {
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAsync
    static class AsyncVoidConfiguration {
        @Bean
        AsyncVoidService asyncVoidService() {
            return new AsyncVoidService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class AbstractOverrideConfiguration {
        @Bean
        AbstractOverrideChild abstractOverrideChild() {
            return new AbstractOverrideChild();
        }
    }

    interface TransactionalContract {
        @Transactional
        String locked();
    }

    static class InterfaceTransactionalService implements TransactionalContract {
        @Override
        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            return "locked";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InterfaceTransactionalConfiguration {
        @Bean
        InterfaceTransactionalService interfaceTransactionalService() {
            return new InterfaceTransactionalService();
        }
    }

    static class WrongAdvisorOrderService {
        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            return "locked";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InnerAsyncAdvisorConfiguration {
        @Bean
        WrongAdvisorOrderService wrongAdvisorOrderService() {
            return new WrongAdvisorOrderService();
        }

        @Bean
        @Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
        Advisor innerAsyncAdvisor() {
            StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
                @Override
                public boolean matches(Method method, Class<?> targetClass) {
                    return method.getName().equals("locked");
                }
            };
            DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut,
                new AsyncExecutionInterceptor(Runnable::run));
            advisor.setOrder(com.paytech.foundation.redis.core.lock.ResourceLockAdvisor.ORDER + 1);
            return advisor;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class WrongAdvisorOrderConfiguration {
        @Bean
        WrongAdvisorOrderService wrongAdvisorOrderService() {
            return new WrongAdvisorOrderService();
        }

        @Bean
        TransactionManager transactionManager() {
            return new TransactionManager() {};
        }

        @Bean
        @Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
        Advisor outerTransactionAdvisor(TransactionManager transactionManager) {
            StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
                @Override
                public boolean matches(Method method, Class<?> targetClass) {
                    return method.getName().equals("locked");
                }
            };
            DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut,
                new TransactionInterceptor(transactionManager, new MatchAlwaysTransactionAttributeSource()));
            advisor.setOrder(Ordered.HIGHEST_PRECEDENCE);
            return advisor;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DuplicateInterceptorAdvisorConfiguration {
        @Bean
        WrongAdvisorOrderService wrongAdvisorOrderService() {
            return new WrongAdvisorOrderService();
        }

        @Bean
        @Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
        Advisor duplicateResourceLockInterceptorAdvisor(ResourceLockMethodInterceptor interceptor) {
            StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
                @Override
                public boolean matches(Method method, Class<?> targetClass) {
                    return method.getName().equals("locked");
                }
            };
            DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, interceptor);
            advisor.setOrder(com.paytech.foundation.redis.core.lock.ResourceLockAdvisor.ORDER + 1);
            return advisor;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class CounterfeitInterceptorAdvisorConfiguration {
        @Bean
        WrongAdvisorOrderService wrongAdvisorOrderService() {
            return new WrongAdvisorOrderService();
        }

        @Bean
        @Role(org.springframework.beans.factory.config.BeanDefinition.ROLE_INFRASTRUCTURE)
        Advisor counterfeitResourceLockInterceptorAdvisor(ResourceLockMetadataRegistry registry,
            ResourceLockKeyResolver keyResolver, ResourceLockExecutor executor) {
            StaticMethodMatcherPointcut pointcut = new StaticMethodMatcherPointcut() {
                @Override
                public boolean matches(Method method, Class<?> targetClass) {
                    return method.getName().equals("locked");
                }
            };
            var counterfeit = new ResourceLockMethodInterceptor(registry, keyResolver, executor);
            DefaultPointcutAdvisor advisor = new DefaultPointcutAdvisor(pointcut, counterfeit);
            advisor.setOrder(com.paytech.foundation.redis.core.lock.ResourceLockAdvisor.ORDER + 1);
            return advisor;
        }
    }

    static class FlowPublisherService {
        @ResourceLock(prefix = "operations:catalog")
        public Flow.Publisher<String> locked() {
            return subscriber -> { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FlowPublisherConfiguration {
        @Bean
        FlowPublisherService flowPublisherService() {
            return new FlowPublisherService();
        }
    }

    static class KotlinContinuationService {
        @ResourceLock(prefix = "operations:catalog")
        public Object locked(Continuation<String> continuation) {
            return "locked";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class KotlinContinuationConfiguration {
        @Bean
        KotlinContinuationService kotlinContinuationService() {
            return new KotlinContinuationService();
        }
    }

    static class KotlinFlowService {
        @ResourceLock(prefix = "operations:catalog")
        public kotlinx.coroutines.flow.Flow<String> locked() {
            return new kotlinx.coroutines.flow.Flow<>() { };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class KotlinFlowConfiguration {
        @Bean
        KotlinFlowService kotlinFlowService() {
            return new KotlinFlowService();
        }
    }

    interface GenericContract<T> {
        T locked(T value);
    }

    static class GenericBridgeService implements GenericContract<String> {
        @Override
        @ResourceLock(prefix = "operations:account", key = "#value")
        public String locked(String value) {
            return value;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class GenericBridgeConfiguration {
        @Bean
        GenericContract<String> genericContract() {
            return new GenericBridgeService();
        }
    }

    interface LazyContract {
        String locked();
    }

    static class LazyAnnotatedService implements LazyContract {
        @Override
        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            LazyHiddenConfiguration.businessCalls.incrementAndGet();
            return "locked";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LazyHiddenConfiguration {
        static final AtomicInteger creations = new AtomicInteger();
        static final AtomicInteger businessCalls = new AtomicInteger();

        @Bean
        @Lazy
        LazyContract lazyContract() {
            creations.incrementAndGet();
            return new LazyAnnotatedService();
        }
    }

    static class DeferredLockService {
        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            return "locked";
        }
    }

    static class ManualLockedService {
        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            return "locked";
        }
    }

    static final class ManualLockedFactory implements FactoryBean<PlainFactoryProduct> {
        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            return "locked";
        }

        @Override
        public PlainFactoryProduct getObject() {
            throw new AssertionError("FactoryBean product must not be created by lock scanning");
        }

        @Override
        public Class<?> getObjectType() {
            return PlainFactoryProduct.class;
        }
    }

    interface HiddenPrototypeContract {
        String plain();
    }

    static class HiddenPrototypeService implements HiddenPrototypeContract {
        @Override
        public String plain() {
            return "plain";
        }

        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            return "locked";
        }
    }

    static class PrototypeConsumer {
        PrototypeConsumer(HiddenPrototypeContract ignored) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class HiddenPrototypeConfiguration {
        @Bean
        @Scope("prototype")
        HiddenPrototypeContract hiddenPrototypeContract() {
            return new HiddenPrototypeService();
        }

        @Bean
        PrototypeConsumer prototypeConsumer(HiddenPrototypeContract hiddenPrototypeContract) {
            return new PrototypeConsumer(hiddenPrototypeContract);
        }
    }

    interface JdkVisibleContract {
        String plain();
    }

    static class JdkHiddenMethodService implements JdkVisibleContract {
        @Override
        public String plain() {
            return "plain";
        }

        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            return "locked";
        }
    }

    static final class PlainFactoryProduct {
    }

    @Configuration(proxyBeanMethods = false)
    static class NonEagerFactoryConfiguration {
        static final AtomicInteger creations = new AtomicInteger();

        @Bean
        FactoryBean<PlainFactoryProduct> plainFactoryProduct() {
            return new FactoryBean<>() {
                @Override
                public PlainFactoryProduct getObject() {
                    creations.incrementAndGet();
                    throw new AssertionError("non-eager FactoryBean product must not be created by lock scanning");
                }

                @Override
                public Class<?> getObjectType() {
                    return PlainFactoryProduct.class;
                }
            };
        }
    }

    interface FactoryLockedContract {
        String plain();
    }

    static class FactoryLockedService implements FactoryLockedContract {
        @Override
        public String plain() {
            return "plain";
        }

        @ResourceLock(prefix = "operations:catalog")
        public String locked() {
            return "locked";
        }
    }

    static class FactoryProductConsumer {
        FactoryProductConsumer(FactoryLockedContract ignored) {
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class NonSingletonLockedFactoryConfiguration {
        @Bean
        FactoryBean<FactoryLockedContract> factoryLockedContract() {
            return new FactoryBean<>() {
                @Override
                public FactoryLockedContract getObject() {
                    return new FactoryLockedService();
                }

                @Override
                public Class<?> getObjectType() {
                    return FactoryLockedContract.class;
                }

                @Override
                public boolean isSingleton() {
                    return false;
                }
            };
        }

        @Bean
        FactoryProductConsumer factoryProductConsumer(FactoryLockedContract product) {
            return new FactoryProductConsumer(product);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DeferredConcreteFactoryConfiguration {
        static final AtomicInteger creations = new AtomicInteger();

        @Bean
        FactoryBean<DeferredLockService> deferredLockFactory() {
            return new FactoryBean<>() {
                @Override
                public DeferredLockService getObject() {
                    creations.incrementAndGet();
                    return new DeferredLockService();
                }

                @Override
                public Class<?> getObjectType() {
                    return DeferredLockService.class;
                }
            };
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class JdkHiddenMethodConfiguration {
        @Bean
        JdkVisibleContract jdkVisibleContract() {
            return new JdkHiddenMethodService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class VisibleLazyConfiguration {
        static final AtomicInteger creations = new AtomicInteger();

        @Bean
        @Lazy
        DeferredLockService deferredLockService() {
            creations.incrementAndGet();
            return new DeferredLockService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ScopedConfiguration {
        @Bean
        @Scope("prototype")
        DeferredLockService deferredLockService() {
            return new DeferredLockService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ExplicitSingletonConfiguration {
        @Bean
        @Scope("singleton")
        DeferredLockService deferredLockService() {
            return new DeferredLockService();
        }
    }

    interface PlainContract {
        String plain();
    }

    static class PlainService implements PlainContract {
        @Override
        @Transactional
        public String plain() {
            return "plain";
        }
    }

    @Configuration(proxyBeanMethods = false)
    @EnableTransactionManagement(proxyTargetClass = false)
    static class NoLockJdkAdvisorConfiguration {
        @Bean
        PlainContract plainContract() {
            return new PlainService();
        }

        @Bean
        SimpleTransactionManager transactionManager() {
            return new SimpleTransactionManager();
        }
    }

    static class SimpleTransactionManager extends AbstractPlatformTransactionManager {
        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
        }
    }
}
