package com.paytech.foundation.redis.auto.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import reactor.core.publisher.Mono;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.NameMapper;
import org.springframework.aop.support.AopUtils;
import org.springframework.aop.framework.Advised;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.core.Ordered;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockAutoConfiguration;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;
import com.paytech.foundation.redis.core.coordination.FoundationRedissonClientDescriptor;
import com.paytech.foundation.redis.core.lock.ResourceLockAdvisor;
import com.paytech.foundation.redis.core.lock.ResourceLockExecutor;
import com.paytech.foundation.redis.core.lock.ResourceLockObservation;
import com.paytech.foundation.redis.core.lock.ResourceLockOperations;

class RedisResourceLockAutoConfigurationAutoTest {

    /** 资源锁 Advisor 创建不能提前初始化 Registry；第一次真实观察时再取 Bean。 */
    @Test
    void defersMetricsUntilObservationAndPreservesRegisteredCounters() {
        var created = new java.util.concurrent.atomic.AtomicInteger();
        runner(NoLockConfiguration.class)
            .withBean(io.micrometer.core.instrument.simple.SimpleMeterRegistry.class, () -> {
                created.incrementAndGet();
                return new io.micrometer.core.instrument.simple.SimpleMeterRegistry();
            }, definition -> definition.setLazyInit(true))
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(created.get()).isZero();
                context.getBean(ResourceLockObservation.class).acquired("rule-set");
                assertThat(created.get()).isEqualTo(1);
                assertThat(context.getBean(io.micrometer.core.instrument.simple.SimpleMeterRegistry.class)
                    .get("paytech.resource.lock").tags("prefix", "rule-set", "status", "acquired")
                    .counter().count()).isEqualTo(1.0);
            });
    }

    @Test
    void leavesServicesWithoutAnnotationsDisconnectedAndUnconfigured() {
        runner(NoLockConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(context).doesNotHaveBean(RedissonClient.class);
            assertThat(context.getBean(NoLockService.class).plain()).isEqualTo("plain");
        });
    }

    @Test
    void bindsExplicitServiceLevelWaitAndLeaseWithoutDefaults() {
        runner(NoLockConfiguration.class).withPropertyValues("redis.coordination.resource-lock.wait-time=500ms",
            "redis.coordination.resource-lock.lease-time=30s").run(context -> {
                RedisResourceLockProperties properties = context.getBean(RedisResourceLockProperties.class);
                assertThat(properties.getWaitTime()).isEqualTo(Duration.ofMillis(500));
                assertThat(properties.getLeaseTime()).isEqualTo(Duration.ofSeconds(30));
            });
    }

    @Test
    void proxiesAValidConcreteAnnotatedMethod() {
        validRunner(ValidLockConfiguration.class).run(context -> {
            assertThat(context).hasNotFailed();
            assertThat(AopUtils.isAopProxy(context.getBean(ValidLockService.class))).isTrue();
            assertThat(((Advised)context.getBean(ValidLockService.class)).getAdvisors())
                .anyMatch(ResourceLockAdvisor.class::isInstance);
            assertThat(context.getBean(ResourceLockAdvisor.class).getOrder())
                .isEqualTo(Ordered.HIGHEST_PRECEDENCE + 100);
        });
    }

    @Test
    void validatesTheRestrictedKeyExpressionBeforeReadiness() {
        assertStartupFailure(validRunner(InvalidExpressionConfiguration.class), "表达式不符合受限语法");
    }

    @Test
    void acceptsTheBoundaryWaitAndLeaseDurations() {
        withRedisson(runner(ValidLockConfiguration.class)).withPropertyValues("redis.coordination.enabled=true",
            "redis.coordination.resource-lock.wait-time=0ms",
            "redis.coordination.resource-lock.lease-time=1ms").run(context -> assertThat(context).hasNotFailed());
    }

    @Test
    void rejectsSubMillisecondLeaseBecauseRedissonRoundsItToZeroMilliseconds() {
        assertStartupFailure(validRunner(ValidLockConfiguration.class)
            .withPropertyValues("redis.coordination.resource-lock.lease-time=1ns"), "lease-time");
    }

    @Test
    void rejectsACustomRedissonClientWithoutTheConfiguredServiceNamespace() {
        assertStartupFailure(withProvenancedRedisson(baseValidRunner(ValidLockConfiguration.class),
            unnamespacedRedissonMarker()),
            "NameMapper");
    }

    @Test
    void rejectsAnUnprovenancedCustomRedissonClientEvenWhenItsNamespaceLooksCorrect() {
        assertStartupFailure(baseValidRunner(ValidLockConfiguration.class)
            .withBean(RedissonClient.class, RedisResourceLockAutoConfigurationAutoTest::redissonMarker),
            "Foundation");
    }

    @Test
    void rejectsAFoundationDescriptorForAnotherClientOrNamespace() {
        RedissonClient actual = redissonMarker();
        RedissonClient other = redissonMarker();
        assertStartupFailure(baseValidRunner(ValidLockConfiguration.class)
            .withBean(RedissonClient.class, () -> actual)
            .withBean(FoundationRedissonClientDescriptor.class,
                () -> new FoundationRedissonClientDescriptor(other, "resource-lock-test")),
            "连接描述");
        assertStartupFailure(baseValidRunner(ValidLockConfiguration.class)
            .withBean(RedissonClient.class, () -> actual)
            .withBean(FoundationRedissonClientDescriptor.class,
                () -> new FoundationRedissonClientDescriptor(actual, "another-service")),
            "连接描述");
    }

    @Test
    void rejectsAnAdditionalPrototypeRedissonClientBeforeReadiness() {
        assertStartupFailure(validRunner(ValidLockConfiguration.class)
            .withBean("prototypeRedissonClient", RedissonClient.class,
                RedisResourceLockAutoConfigurationAutoTest::redissonMarker,
                definition -> definition.setScope("prototype")),
            "唯一RedissonClient");
    }

    @Test
    void rejectsPrimaryOverridesOfTheInternalResourceLockExecutionChain() {
        assertStartupFailure(validRunner(ValidLockConfiguration.class)
            .withBean("replacementResourceLockOperations", ResourceLockOperations.class,
                RedisResourceLockAutoConfigurationAutoTest::noOpOperations,
                definition -> definition.setPrimary(true)),
            "ResourceLockOperations");

        ResourceLockExecutor replacement = new ResourceLockExecutor(noOpOperations(),
            new ResourceLockObservation(null), 0L, 1_000_000L, System::nanoTime);
        assertStartupFailure(validRunner(ValidLockConfiguration.class)
            .withBean("replacementResourceLockExecutor", ResourceLockExecutor.class, () -> replacement,
                definition -> definition.setPrimary(true)),
            "执行链");
    }

    @Test
    void rejectsAConditionalNameMapperBeforeItCanUseAnUnnamespacedBusinessKey() {
        withProvenancedRedisson(baseValidRunner(ValidLockConfiguration.class), conditionalRedissonMarker())
            .run(context -> {
                assertThat(context).hasNotFailed();
                org.assertj.core.api.Assertions.assertThatThrownBy(
                    () -> context.getBean(ValidLockService.class).locked())
                    .isInstanceOf(ServiceUnavailableException.class)
                    .hasRootCauseMessage("RedissonClient NameMapper does not apply the configured service namespace");
            });
    }

    @Test
    void requiresAResolvableServiceNamespaceWhenAnnotationsExist() {
        withRedisson(new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisResourceLockAutoConfiguration.class))
            .withUserConfiguration(ValidLockConfiguration.class)
            .withPropertyValues("redis.coordination.enabled=true",
                "redis.coordination.resource-lock.wait-time=500ms",
                "redis.coordination.resource-lock.lease-time=30s"))
                    .run(context -> {
                        assertThat(context).hasFailed();
                        assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                            .rootCause().hasMessageContaining("spring.application.name");
                    });
    }

    @Test
    void requiresCoordinationRedissonWaitAndLeaseOnlyWhenAnnotationsExist() {
        assertStartupFailure(runner(ValidLockConfiguration.class), "coordination");
        assertStartupFailure(baseValidRunner(ValidLockConfiguration.class), "RedissonClient");
        assertStartupFailure(withRedisson(runner(ValidLockConfiguration.class))
            .withPropertyValues("redis.coordination.enabled=true", "redis.coordination.resource-lock.lease-time=30s"),
            "wait-time");
        assertStartupFailure(withRedisson(runner(ValidLockConfiguration.class))
            .withPropertyValues("redis.coordination.enabled=true", "redis.coordination.resource-lock.wait-time=500ms"),
            "lease-time");
    }

    @Test
    void rejectsInvalidWaitAndLeaseDurations() {
        assertStartupFailure(validRunner(ValidLockConfiguration.class)
            .withPropertyValues("redis.coordination.resource-lock.wait-time=-1ms"), "wait-time");
        assertStartupFailure(validRunner(ValidLockConfiguration.class)
            .withPropertyValues("redis.coordination.resource-lock.lease-time=0ms"), "lease-time");
        assertStartupFailure(validRunner(ValidLockConfiguration.class)
            .withPropertyValues("redis.coordination.resource-lock.lease-time=106752d"), "lease-time");
    }

    @Test
    void rejectsAPrefixThatRepeatsTheServiceNamespace() {
        assertStartupFailure(validRunner(DuplicateNamespaceConfiguration.class), "Namespace");
    }

    @Test
    void rejectsUnproxyableInterfaceOnlyTransactionalDelayedAndWrongLayerMethods() {
        assertStartupFailure(validRunner(FinalLockConfiguration.class), "final");
        assertStartupFailure(validRunner(FinalMethodConfiguration.class), "final");
        assertStartupFailure(validRunner(StaticMethodConfiguration.class), "static");
        assertStartupFailure(validRunner(PrivateMethodConfiguration.class), "public");
        assertStartupFailure(validRunner(InterfaceOnlyConfiguration.class), "具体实现方法");
        assertStartupFailure(validRunner(TransactionalConfiguration.class), "Transactional");
        assertStartupFailure(validRunner(TransactionalClassConfiguration.class), "Transactional");
        assertStartupFailure(validRunner(DelayedConfiguration.class), "同步");
        assertStartupFailure(validRunner(FutureConfiguration.class), "同步");
        assertStartupFailure(validRunner(PublisherConfiguration.class), "同步");
        assertStartupFailure(validRunner(StreamConfiguration.class), "同步");
        assertStartupFailure(validRunner(ControllerConfiguration.class), "Controller");
        assertStartupFailure(validRunner(RepositoryConfiguration.class), "Repository");
        assertStartupFailure(validRunner(MapperConfiguration.class), "Mapper");
        assertStartupFailure(validRunner(InterfaceReturnedTransactionalConfiguration.class), "Transactional");
        assertStartupFailure(validRunner(InterfaceReturnedRepositoryConfiguration.class), "Repository");
        assertStartupFailure(validRunner(InterfaceReturnedHiddenMethodConfiguration.class), "public");
    }

    private ApplicationContextRunner runner(Class<?> configuration) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisResourceLockAutoConfiguration.class))
            .withUserConfiguration(configuration).withPropertyValues("spring.application.name=resource-lock-test");
    }

    private ApplicationContextRunner validRunner(Class<?> configuration) {
        return withRedisson(baseValidRunner(configuration));
    }

    private ApplicationContextRunner baseValidRunner(Class<?> configuration) {
        return runner(configuration).withPropertyValues("redis.coordination.enabled=true",
            "redis.coordination.resource-lock.wait-time=500ms", "redis.coordination.resource-lock.lease-time=30s");
    }

    private ApplicationContextRunner withRedisson(ApplicationContextRunner runner) {
        return withProvenancedRedisson(runner, redissonMarker());
    }

    private ApplicationContextRunner withProvenancedRedisson(ApplicationContextRunner runner,
        RedissonClient client) {
        return runner.withBean(RedissonClient.class, () -> client)
            .withBean(FoundationRedissonClientDescriptor.class,
                () -> new FoundationRedissonClientDescriptor(client, "resource-lock-test"));
    }

    private void assertStartupFailure(ApplicationContextRunner runner, String message) {
        runner.run(context -> {
            assertThat(context).hasFailed();
            assertThat(context.getStartupFailure()).hasRootCauseInstanceOf(FailException.class)
                .rootCause().hasMessageContaining(message);
        });
    }

    private static RedissonClient redissonMarker() {
        return redissonMarker(new NameMapper() {
            @Override
            public String map(String name) {
                return "resource-lock-test:" + name;
            }

            @Override
            public String unmap(String name) {
                return name.startsWith("resource-lock-test:")
                    ? name.substring("resource-lock-test:".length()) : name;
            }
        });
    }

    private static RedissonClient unnamespacedRedissonMarker() {
        return redissonMarker(NameMapper.direct());
    }

    private static RedissonClient conditionalRedissonMarker() {
        return redissonMarker(new NameMapper() {
            @Override
            public String map(String name) {
                return name.startsWith("resource-lock-") ? "resource-lock-test:" + name : name;
            }

            @Override
            public String unmap(String name) {
                return name.startsWith("resource-lock-test:")
                    ? name.substring("resource-lock-test:".length()) : name;
            }
        });
    }

    private static RedissonClient redissonMarker(NameMapper nameMapper) {
        Config config = new Config().setNameMapper(nameMapper);
        return (RedissonClient)Proxy.newProxyInstance(RedissonClient.class.getClassLoader(),
            new Class<?>[] {RedissonClient.class}, (proxy, method, arguments) -> {
                if (method.getName().equals("getConfig")) {
                    return config;
                }
                if (method.getName().equals("toString")) {
                    return "redisson-marker";
                }
                throw new UnsupportedOperationException("marker-only");
            });
    }

    private static ResourceLockOperations noOpOperations() {
        return logicalKey -> new ResourceLockOperations.LockHandle() {
            @Override
            public boolean tryLock(long waitNanos, long leaseNanos) {
                return true;
            }

            @Override
            public boolean isHeldByCurrentThread() {
                return true;
            }

            @Override
            public void unlock() {
            }
        };
    }

    @Configuration(proxyBeanMethods = false)
    static class NoLockConfiguration {
        @Bean
        NoLockService noLockService() {
            return new NoLockService();
        }

        @Bean
        @Lazy
        Object unrelatedLazyBean() {
            throw new AssertionError("unrelated lazy bean must not be instantiated by resource lock scanning");
        }
    }

    static class NoLockService {
        String plain() {
            return "plain";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class ValidLockConfiguration {
        @Bean
        ValidLockService validLockService() {
            return new ValidLockService();
        }
    }

    static class ValidLockService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public String locked() {
            return "locked";
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class InvalidExpressionConfiguration {
        @Bean
        InvalidExpressionLockService invalidExpressionLockService() {
            return new InvalidExpressionLockService();
        }
    }

    static class InvalidExpressionLockService {
        @ResourceLock(prefix = "operations:account", key = "#request.loginIdentifier.toLowerCase()")
        public String locked(LoginRequest request) {
            return request.loginIdentifier();
        }
    }

    record LoginRequest(String loginIdentifier) {}

    @Configuration(proxyBeanMethods = false)
    static class DuplicateNamespaceConfiguration {
        @Bean
        DuplicateNamespaceService duplicateNamespaceService() {
            return new DuplicateNamespaceService();
        }
    }

    static class DuplicateNamespaceService {
        @ResourceLock(prefix = "resource-lock-test:account", key = "#accountId")
        public String locked(String accountId) {
            return accountId;
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FinalLockConfiguration {
        @Bean
        FinalLockService finalLockService() {
            return new FinalLockService();
        }
    }

    static final class FinalLockService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void locked() {}
    }

    static class FinalMethodService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public final void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class FinalMethodConfiguration {
        @Bean
        FinalMethodService finalMethodService() {
            return new FinalMethodService();
        }
    }

    static class StaticMethodService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public static void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class StaticMethodConfiguration {
        @Bean
        StaticMethodService staticMethodService() {
            return new StaticMethodService();
        }
    }

    static class PrivateMethodService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        private void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class PrivateMethodConfiguration {
        @Bean
        PrivateMethodService privateMethodService() {
            return new PrivateMethodService();
        }
    }

    interface InterfaceContract {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        void locked();
    }

    static class InterfaceOnlyService implements InterfaceContract {
        @Override
        public void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class InterfaceOnlyConfiguration {
        @Bean
        InterfaceOnlyService interfaceOnlyService() {
            return new InterfaceOnlyService();
        }
    }

    static class TransactionalService {
        @Transactional
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void locked() {}
    }

    @Transactional
    static class TransactionalClassService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class TransactionalClassConfiguration {
        @Bean
        TransactionalClassService transactionalClassService() {
            return new TransactionalClassService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TransactionalConfiguration {
        @Bean
        TransactionalService transactionalService() {
            return new TransactionalService();
        }
    }

    static class DelayedService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public CompletionStage<String> locked() {
            return CompletableFuture.completedFuture("late");
        }
    }

    static class FutureService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public Future<String> locked() {
            return CompletableFuture.completedFuture("late");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class FutureConfiguration {
        @Bean
        FutureService futureService() {
            return new FutureService();
        }
    }

    static class PublisherService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public Mono<String> locked() {
            return Mono.just("late");
        }
    }

    static class StreamService {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public Stream<String> locked() {
            return Stream.of("late");
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class PublisherConfiguration {
        @Bean
        PublisherService publisherService() {
            return new PublisherService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StreamConfiguration {
        @Bean
        StreamService streamService() {
            return new StreamService();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class DelayedConfiguration {
        @Bean
        DelayedService delayedService() {
            return new DelayedService();
        }
    }

    @Controller
    static class AnnotatedController {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class ControllerConfiguration {
        @Bean
        AnnotatedController annotatedController() {
            return new AnnotatedController();
        }
    }

    @Repository
    static class AnnotatedRepository {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class RepositoryConfiguration {
        @Bean
        AnnotatedRepository annotatedRepository() {
            return new AnnotatedRepository();
        }
    }

    static class AnnotatedMapper {
        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class MapperConfiguration {
        @Bean
        AnnotatedMapper annotatedMapper() {
            return new AnnotatedMapper();
        }
    }

    interface ActualTargetContract {
        void plain();
    }

    @Transactional
    static class InterfaceReturnedTransactionalService implements ActualTargetContract {
        @Override
        public void plain() {}

        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class InterfaceReturnedTransactionalConfiguration {
        @Bean
        ActualTargetContract actualTargetContract() {
            return new InterfaceReturnedTransactionalService();
        }
    }

    @Repository
    static class InterfaceReturnedRepositoryService implements ActualTargetContract {
        @Override
        public void plain() {}

        @ResourceLock(prefix = "gateway-configuration:catalog")
        public void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class InterfaceReturnedRepositoryConfiguration {
        @Bean
        ActualTargetContract actualTargetContract() {
            return new InterfaceReturnedRepositoryService();
        }
    }

    static class InterfaceReturnedHiddenMethodService implements ActualTargetContract {
        @Override
        public void plain() {}

        @ResourceLock(prefix = "gateway-configuration:catalog")
        void hiddenLock() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class InterfaceReturnedHiddenMethodConfiguration {
        @Bean
        ActualTargetContract actualTargetContract() {
            return new InterfaceReturnedHiddenMethodService();
        }
    }
}
