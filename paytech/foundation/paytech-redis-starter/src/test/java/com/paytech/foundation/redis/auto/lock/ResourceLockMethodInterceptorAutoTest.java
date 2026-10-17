package com.paytech.foundation.redis.auto.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.lang.reflect.AccessibleObject;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Supplier;
import java.util.concurrent.atomic.AtomicInteger;

import org.aopalliance.intercept.MethodInvocation;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.lock.ResourceLockExecutor;
import com.paytech.foundation.redis.core.lock.ResourceLockKeyResolver;
import com.paytech.foundation.redis.core.lock.ResourceLockMetadataCompiler;
import com.paytech.foundation.redis.core.lock.ResourceLockMetadataRegistry;
import com.paytech.foundation.redis.core.lock.ResourceLockMethodInterceptor;
import com.paytech.foundation.redis.core.lock.ResourceLockObservation;
import com.paytech.foundation.redis.test.fake.InMemoryResourceLockOperations;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ResourceLockMethodInterceptorAutoTest {

    @AfterEach
    void clearTransactionState() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void resolvesCompiledMetadataBeforeProceedingThroughTheExecutor() throws Throwable {
        Method method = Target.class.getDeclaredMethod("invoke", String.class);
        ResourceLockMetadataRegistry registry = registry(method);
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        AtomicInteger proceeds = new AtomicInteger();
        ResourceLockMethodInterceptor interceptor = interceptor(registry, operations);

        Object result = interceptor.invoke(new Invocation(method, new Object[] {"account-1"}, proceeds, "done", null));

        assertThat(result).isEqualTo("done");
        assertThat(proceeds).hasValue(1);
        assertThat(operations.events()).containsExactly("lock:operations:account:account-1",
            "acquire:operations:account:account-1", "release:operations:account:account-1");
    }

    @Test
    void rejectsInvalidRuntimeValueBeforeRedisOrBusiness() throws Exception {
        Method method = Target.class.getDeclaredMethod("invoke", String.class);
        ResourceLockMetadataRegistry registry = registry(method);
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        AtomicInteger proceeds = new AtomicInteger();

        assertThatThrownBy(() -> interceptor(registry, operations)
            .invoke(new Invocation(method, new Object[] {"secret invalid"}, proceeds, null, null)))
                .isInstanceOf(InvalidParameterException.class).hasMessageNotContaining("secret invalid");
        assertThat(proceeds).hasValue(0);
        assertThat(operations.events()).isEmpty();
    }

    @Test
    void propagatesTheExactBusinessThrowable() throws Exception {
        Method method = Target.class.getDeclaredMethod("invoke", String.class);
        RuntimeException failure = new RuntimeException("business");
        ResourceLockMethodInterceptor interceptor = interceptor(registry(method), new InMemoryResourceLockOperations());

        assertThatThrownBy(() -> interceptor.invoke(new Invocation(method, new Object[] {"account-1"},
            new AtomicInteger(), null, failure))).isSameAs(failure);
    }

    @Test
    void rejectsEntryFromAnAlreadyActiveTransactionBeforeResolvingOrAccessingRedis() throws Exception {
        Method method = Target.class.getDeclaredMethod("invoke", String.class);
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        AtomicInteger proceeds = new AtomicInteger();
        TransactionSynchronizationManager.setActualTransactionActive(true);

        assertThatThrownBy(() -> interceptor(registry(method), operations)
            .invoke(new Invocation(method, new Object[] {"secret-account-42"}, proceeds, null, null)))
                .isInstanceOf(FailException.class).hasMessageContaining("事务开始前")
                .hasMessageNotContaining("secret-account-42");
        assertThat(proceeds).hasValue(0);
        assertThat(operations.events()).isEmpty();
    }

    @Test
    void rejectsKnownAsynchronousRuntimeResultBeforeReleasingTheLock() throws Exception {
        Method method = Target.class.getDeclaredMethod("dynamicResult", String.class);
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        AtomicInteger proceeds = new AtomicInteger();

        assertThatThrownBy(() -> interceptor(registry(method), operations)
            .invoke(new Invocation(method, new Object[] {"secret-account-42"}, proceeds,
                CompletableFuture.completedFuture("done"), null)))
                .isInstanceOf(FailException.class).hasMessageContaining("同步")
                .hasMessageNotContaining("secret-account-42").hasMessageNotContaining("done");
        assertThat(proceeds).hasValue(1);
        assertThat(operations.events()).containsExactly("lock:operations:account:secret-account-42",
            "acquire:operations:account:secret-account-42", "release:operations:account:secret-account-42");
    }

    @Test
    void rejectsADeferredFunctionalResultHiddenBehindObject() throws Exception {
        Method method = Target.class.getDeclaredMethod("dynamicResult", String.class);
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        AtomicInteger proceeds = new AtomicInteger();
        Supplier<String> deferred = () -> "side-effect";

        assertThatThrownBy(() -> interceptor(registry(method), operations)
            .invoke(new Invocation(method, new Object[] {"account-42"}, proceeds, deferred, null)))
                .isInstanceOf(FailException.class).hasMessageContaining("同步")
                .hasMessageNotContaining("side-effect");
        assertThat(proceeds).hasValue(1);
        assertThat(operations.events()).containsExactly("lock:operations:account:account-42",
            "acquire:operations:account:account-42", "release:operations:account:account-42");
    }

    private ResourceLockMetadataRegistry registry(Method method) {
        ResourceLockMetadataRegistry registry = new ResourceLockMetadataRegistry();
        registry.freeze(Map.of(method, new ResourceLockMetadataCompiler().compile(Target.class, method)));
        return registry;
    }

    private ResourceLockMethodInterceptor interceptor(ResourceLockMetadataRegistry registry,
        InMemoryResourceLockOperations operations) {
        return new ResourceLockMethodInterceptor(registry, new ResourceLockKeyResolver(),
            new ResourceLockExecutor(operations, new ResourceLockObservation(new SimpleMeterRegistry()), 500L, 1_000L,
                System::nanoTime));
    }

    static class Target {
        @ResourceLock(prefix = "operations:account", key = "#accountId")
        public String invoke(String accountId) {
            return accountId;
        }

        @ResourceLock(prefix = "operations:account", key = "#accountId")
        public Object dynamicResult(String accountId) {
            return "done";
        }
    }

    private record Invocation(Method method, Object[] arguments, AtomicInteger proceeds, Object result,
                              Throwable failure) implements MethodInvocation {

        @Override
        public Method getMethod() {
            return method;
        }

        @Override
        public Object[] getArguments() {
            return arguments;
        }

        @Override
        public Object proceed() throws Throwable {
            proceeds.incrementAndGet();
            if (failure != null) {
                throw failure;
            }
            return result;
        }

        @Override
        public Object getThis() {
            return null;
        }

        @Override
        public AccessibleObject getStaticPart() {
            return method;
        }
    }
}
