package com.paytech.foundation.redis.auto.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.Proxy;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;
import java.util.concurrent.Future;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.redisson.config.NameMapper;
import org.springframework.aop.framework.Advised;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Controller;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockAutoConfiguration;
import com.paytech.foundation.redis.core.coordination.FoundationRedissonClientDescriptor;
import com.paytech.foundation.redis.core.lock.ResourceLockAdvisor;

class ResourceLockConsumerContractAutoTest {

    @Test
    void rejectsEveryKnownConsumerSideProxyBypass() {
        for (Class<?> invalid : new Class<?>[] {SelfCallingProcessor.class, BypassingCaller.class,
            PrivateProcessor.class, FinalMethodProcessor.class, ControllerProcessor.class,
            RepositoryProcessor.class, UnsafeMapper.class, TransactionalProcessor.class,
            BusinessLockWrapper.class, LazyTypeProcessor.class, LazyMethodProcessor.class,
            FlowProcessor.class, FutureProcessor.class, CompletionStageProcessor.class,
            StreamProcessor.class,
            KotlinSuspendProcessor.class, KotlinFlowProcessor.class, MissingAncestorAnnotationProcessor.class,
            InterfaceTransactionalProcessor.class, AncestorTransactionalProcessor.class,
            InheritedSelfCallingProcessor.class}) {
            assertThatThrownBy(() -> ResourceLockConsumerGate.verify(invalid, ValidProcessor.class))
                .isInstanceOf(AssertionError.class);
        }
    }

    @Test
    void rejectsNestedResourceLockCallsAcrossInjectedBeansAndMethodReferences() {
        assertThatThrownBy(() -> ResourceLockConsumerGate.verify(NestedLockCaller.class,
            NestedLockTarget.class)).isInstanceOf(AssertionError.class).hasMessageContaining("嵌套");
        assertThatThrownBy(() -> ResourceLockConsumerGate.verify(MethodReferenceLockCaller.class,
            NestedLockTarget.class)).isInstanceOf(AssertionError.class).hasMessageContaining("嵌套");
    }

    @Test
    void allowsAPublicProcessorThatStartsItsTransactionInsideTheAnnotatedMethod() {
        assertThatCode(() -> ResourceLockConsumerGate.verify(ValidProcessor.class,
            InheritedLockProcessor.class, ExplicitSingletonProcessor.class)).doesNotThrowAnyException();
    }

    @Test
    void provesTheValidConsumerBeanActuallyContainsTheResourceLockAdvisor() {
        new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(RedisResourceLockAutoConfiguration.class))
            .withUserConfiguration(ValidContextConfiguration.class)
            .withPropertyValues("spring.application.name=resource-lock-consumer-test",
                "redis.coordination.enabled=true", "redis.coordination.resource-lock.wait-time=0ms",
                "redis.coordination.resource-lock.lease-time=1s")
            .run(context -> {
                assertThat(context).hasNotFailed();
                assertThat(((Advised)context.getBean(ValidSimpleProcessor.class)).getAdvisors())
                    .anyMatch(ResourceLockAdvisor.class::isInstance);
            });
    }

    static class SelfCallingProcessor {
        public void bypass() {
            this.locked();
        }

        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    static class BypassingCaller {
        public void bypass() {
            new ValidProcessor(null).locked();
        }
    }

    static class ValidProcessor {
        private final TransactionTemplate transactions;

        ValidProcessor(TransactionTemplate transactions) {
            this.transactions = transactions;
        }

        @ResourceLock(prefix = "test:resource")
        public void locked() {
            transactions.executeWithoutResult(status -> {});
        }
    }

    static class PrivateProcessor {
        @ResourceLock(prefix = "test:resource")
        private void locked() {}
    }

    static class FinalMethodProcessor {
        @ResourceLock(prefix = "test:resource")
        public final void locked() {}
    }

    @Controller
    static class ControllerProcessor {
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    @Repository
    static class RepositoryProcessor {
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    static class UnsafeMapper {
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    static class TransactionalProcessor {
        @Transactional
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    static class BusinessLockWrapper {
        private RedissonClient redissonClient;
    }

    @Lazy
    static class LazyTypeProcessor {
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    static class LazyMethodProcessor {
        @Lazy
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    static class FlowProcessor {
        @ResourceLock(prefix = "test:resource")
        public Flow.Publisher<String> locked() {
            return subscriber -> { };
        }
    }

    static class FutureProcessor {
        @ResourceLock(prefix = "test:resource")
        public Future<String> locked() {
            return CompletableFuture.completedFuture("done");
        }
    }

    static class CompletionStageProcessor {
        @ResourceLock(prefix = "test:resource")
        public CompletionStage<String> locked() {
            return CompletableFuture.completedFuture("done");
        }
    }

    static class StreamProcessor {
        @ResourceLock(prefix = "test:resource")
        public Stream<String> locked() {
            return Stream.of("done");
        }
    }

    static class NestedLockTarget {
        @ResourceLock(prefix = "test:a-resource")
        public void locked() {}
    }

    static class NestedLockCaller {
        private final NestedLockTarget target;

        NestedLockCaller(NestedLockTarget target) {
            this.target = target;
        }

        @ResourceLock(prefix = "test:z-resource")
        public void locked() {
            target.locked();
        }
    }

    static class MethodReferenceLockCaller {
        private final NestedLockTarget target;

        MethodReferenceLockCaller(NestedLockTarget target) {
            this.target = target;
        }

        @ResourceLock(prefix = "test:z-resource")
        public void locked() {
            Runnable invocation = target::locked;
            invocation.run();
        }
    }

    static class KotlinSuspendProcessor {
        @ResourceLock(prefix = "test:resource")
        public Object locked(kotlin.coroutines.Continuation<String> continuation) {
            return "done";
        }
    }

    static class KotlinFlowProcessor {
        @ResourceLock(prefix = "test:resource")
        public kotlinx.coroutines.flow.Flow<String> locked() {
            return new kotlinx.coroutines.flow.Flow<>() { };
        }
    }

    static class LockAncestor {
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    static class MissingAncestorAnnotationProcessor extends LockAncestor {
        @Override
        public void locked() {}
    }

    static class InheritedLockProcessor extends LockAncestor {
    }

    static class InheritedSelfCallingProcessor extends LockAncestor {
        public void bypass() {
            locked();
        }
    }

    @Scope("singleton")
    static class ExplicitSingletonProcessor {
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    interface TransactionalContract {
        @Transactional
        void locked();
    }

    static class InterfaceTransactionalProcessor implements TransactionalContract {
        @Override
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    static class TransactionalAncestor {
        @Transactional
        public void locked() {}
    }

    static class AncestorTransactionalProcessor extends TransactionalAncestor {
        @Override
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }

    @Configuration(proxyBeanMethods = false)
    static class ValidContextConfiguration {
        @Bean
        ValidSimpleProcessor validSimpleProcessor() {
            return new ValidSimpleProcessor();
        }

        @Bean
        RedissonClient redissonClient() {
            Config config = new Config().setNameMapper(new NameMapper() {
                @Override
                public String map(String name) {
                    return "resource-lock-consumer-test:" + name;
                }

                @Override
                public String unmap(String name) {
                    return name.startsWith("resource-lock-consumer-test:")
                        ? name.substring("resource-lock-consumer-test:".length()) : name;
                }
            });
            return (RedissonClient)Proxy.newProxyInstance(RedissonClient.class.getClassLoader(),
                new Class<?>[] {RedissonClient.class}, (proxy, method, arguments) -> {
                    if (method.getName().equals("getConfig")) {
                        return config;
                    }
                    if (method.getName().equals("shutdown")) {
                        return null;
                    }
                    throw new UnsupportedOperationException("marker-only");
                });
        }

        @Bean
        FoundationRedissonClientDescriptor foundationRedissonClientDescriptor(RedissonClient redissonClient) {
            return new FoundationRedissonClientDescriptor(redissonClient, "resource-lock-consumer-test");
        }
    }

    static class ValidSimpleProcessor {
        @ResourceLock(prefix = "test:resource")
        public void locked() {}
    }
}
