package com.paytech.foundation.redis.auto.integration;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

import java.io.BufferedReader;
import java.io.OutputStreamWriter;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.redis.api.ResourceLock;
import com.paytech.foundation.redis.core.autoconfigure.RedisAutoConfiguration;
import com.paytech.foundation.redis.core.autoconfigure.RedisKeyNamespaceAutoConfiguration;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockAutoConfiguration;
import com.paytech.foundation.redis.core.lock.RedissonResourceLockOperations;
import com.paytech.foundation.redis.core.lock.ResolvedResourceLock;
import com.paytech.foundation.redis.core.lock.ResourceLockExecutor;
import com.paytech.foundation.redis.core.lock.ResourceLockObservation;
import com.paytech.foundation.redis.test.infrastructure.RedisLocalEnvironmentPrecondition;
import com.paytech.foundation.redis.test.infrastructure.RedisTestNamespace;
import com.paytech.foundation.redis.test.infrastructure.ResourceLockProcessProbe;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class ResourceLockRedisIntegrationAutoTest {

    private static String password;

    @BeforeAll
    static void verifyLocalRedis() {
        password = RedisLocalEnvironmentPrecondition.requirePasswordAndVerify();
    }

    @Test
    void appliesTheRealProxyAndPreservesBusinessFailure() {
        RedisTestNamespace namespace = RedisTestNamespace.unique("proxy");
        runner(namespace, "200ms", "3s").run(context -> {
            LockCommands commands = context.getBean(LockCommands.class);
            RuntimeException failure = new RuntimeException("business");
            try {
                assertThat(commands.simple("account-1")).isEqualTo("account-1");
                assertThatThrownBy(() -> commands.fail("account-2", failure)).isSameAs(failure);
            } finally {
                namespace.clean(context.getBean(RedisConnectionFactory.class));
            }
        });
    }

    @Test
    void serializesTheSameKeyAndAllowsDifferentKeys() {
        RedisTestNamespace namespace = RedisTestNamespace.unique("threads");
        runner(namespace, "150ms", "5s").run(context -> {
            LockCommands commands = context.getBean(LockCommands.class);
            ExecutorService workers = Executors.newFixedThreadPool(2);
            Gate first = new Gate();
            Gate different = new Gate();
            try {
                Future<String> owner = workers.submit(() -> commands.blocking("same", first));
                first.awaitEntered();
                assertThatThrownBy(() -> commands.simple("same")).isInstanceOf(ConcurrentRequestException.class);

                Future<String> parallel = workers.submit(() -> commands.blocking("different", different));
                different.awaitEntered();
                different.release();
                assertThat(parallel.get(2, SECONDS)).isEqualTo("different");
                first.release();
                assertThat(owner.get(2, SECONDS)).isEqualTo("same");
            } finally {
                first.release();
                different.release();
                workers.shutdownNow();
                namespace.clean(context.getBean(RedisConnectionFactory.class));
            }
        });
    }

    @Test
    void coordinatesTwoIndependentApplicationContextsAndTwoJvms() {
        RedisTestNamespace namespace = RedisTestNamespace.unique("processes");
        runner(namespace, "150ms", "5s").run(firstContext -> runner(namespace, "150ms", "5s").run(secondContext -> {
            ExecutorService worker = Executors.newSingleThreadExecutor();
            Gate gate = new Gate();
            Process child = null;
            try {
                Future<String> owner = worker.submit(() -> firstContext.getBean(LockCommands.class).blocking("shared", gate));
                gate.awaitEntered();
                assertThatThrownBy(() -> secondContext.getBean(LockCommands.class).simple("shared"))
                    .isInstanceOf(ConcurrentRequestException.class);
                gate.release();
                assertThat(owner.get(2, SECONDS)).isEqualTo("shared");

                child = startProbe(namespace, "resource:account:cross-jvm");
                assertThat(readUntil(child, "READY")).isEqualTo("READY");
                RLock parentLock = firstContext.getBean(RedissonClient.class).getLock("resource:account:cross-jvm");
                assertThat(parentLock.tryLock(0, 1, SECONDS)).isFalse();
                writeLine(child, "RELEASE");
                assertThat(readUntil(child, "RELEASED")).isEqualTo("RELEASED");
                assertThat(child.waitFor(3, SECONDS)).isTrue();
                assertThat(child.exitValue()).isZero();
            } finally {
                gate.release();
                worker.shutdownNow();
                stopOwnedProcess(child);
                namespace.clean(firstContext.getBean(RedisConnectionFactory.class));
            }
        }));
    }

    @Test
    void sortsMultipleDeclarationsDeduplicatesAliasesAndReleasesPartialAcquisition() {
        RedisTestNamespace namespace = RedisTestNamespace.unique("multi");
        runner(namespace, "500ms", "5s").run(context -> {
            LockCommands commands = context.getBean(LockCommands.class);
            RedissonClient client = context.getBean(RedissonClient.class);
            RedisConnectionFactory connections = context.getBean(RedisConnectionFactory.class);
            StringRedisTemplate raw = new StringRedisTemplate(connections);
            try {
                assertThat(commands.duplicate("same", "same")).isOne();
                assertSortedWhileFirstKeyIsContended(commands, client, raw, namespace, true);
                assertSortedWhileFirstKeyIsContended(commands, client, raw, namespace, false);

                try (DirectHold ignored = hold(client, "z-resource:blocked", 5_000)) {
                    assertThatThrownBy(() -> commands.multiReverse("free", "blocked"))
                        .isInstanceOf(ConcurrentRequestException.class);
                    RLock releasedFirst = client.getLock("a-resource:free");
                    assertThat(releasedFirst.tryLock(0, 1, SECONDS)).isTrue();
                    releasedFirst.unlock();
                }
            } finally {
                namespace.clean(connections);
            }
        });
    }

    @Test
    void appliesOneTotalWaitBudgetAcrossRealRedisLocks() {
        RedisTestNamespace namespace = RedisTestNamespace.unique("budget");
        runner(namespace, "600ms", "5s").run(context -> {
            RedissonClient client = context.getBean(RedissonClient.class);
            ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
            try (DirectHold first = hold(client, "a-resource:first", 5_000);
                 DirectHold second = hold(client, "z-resource:second", 5_000)) {
                scheduler.schedule(first::release, 350, MILLISECONDS);
                long startedAt = System.nanoTime();
                assertThatThrownBy(() -> context.getBean(LockCommands.class).multiReverse("first", "second"))
                    .isInstanceOf(ConcurrentRequestException.class);
                long elapsedMillis = MILLISECONDS.convert(System.nanoTime() - startedAt,
                    java.util.concurrent.TimeUnit.NANOSECONDS);
                assertThat(elapsedMillis).isBetween(450L, 850L);
            } finally {
                scheduler.shutdownNow();
                namespace.clean(context.getBean(RedisConnectionFactory.class));
            }
        });
    }

    @Test
    void doesNotReleaseTheSecondOwnerAfterTheFirstLeaseExpires() {
        RedisTestNamespace namespace = RedisTestNamespace.unique("lease");
        runner(namespace, "100ms", "300ms").run(context -> {
            LockCommands commands = context.getBean(LockCommands.class);
            StringRedisTemplate raw = new StringRedisTemplate(context.getBean(RedisConnectionFactory.class));
            ExecutorService workers = Executors.newFixedThreadPool(2);
            Gate first = new Gate();
            Gate second = new Gate();
            try {
                Future<String> expiredOwner = workers.submit(() -> commands.blocking("lease", first));
                first.awaitEntered();
                await().atMost(Duration.ofSeconds(2))
                    .untilAsserted(() -> assertThat(raw.hasKey(namespace.physical("resource:account:lease"))).isFalse());
                Future<String> currentOwner = workers.submit(() -> commands.blocking("lease", second));
                second.awaitEntered();
                first.release();
                assertThat(expiredOwner.get(2, SECONDS)).isEqualTo("lease");
                assertThat(raw.hasKey(namespace.physical("resource:account:lease"))).isTrue();
                second.release();
                assertThat(currentOwner.get(2, SECONDS)).isEqualTo("lease");
            } finally {
                first.release();
                second.release();
                workers.shutdownNow();
                namespace.clean(context.getBean(RedisConnectionFactory.class));
            }
        });
    }

    @Test
    void restoresInterruptAndFailsClosedForUnavailableOrUnauthorizedRedis() throws Exception {
        RedisTestNamespace namespace = RedisTestNamespace.unique("failure");
        runner(namespace, "5s", "5s").run(context -> {
            LockCommands commands = context.getBean(LockCommands.class);
            AtomicReference<Throwable> failure = new AtomicReference<>();
            AtomicBoolean interrupted = new AtomicBoolean();
            try (DirectHold ignored = hold(context.getBean(RedissonClient.class), "resource:account:interrupt", 5_000)) {
                Thread contender = new Thread(() -> {
                    try {
                        commands.simple("interrupt");
                    } catch (Throwable thrown) {
                        failure.set(thrown);
                        interrupted.set(Thread.currentThread().isInterrupted());
                    }
                }, "resource-lock-interrupt-test");
                contender.start();
                await().atMost(Duration.ofSeconds(2)).until(() -> contender.getState() != Thread.State.RUNNABLE);
                contender.interrupt();
                contender.join(2_000);
                assertThat(contender.isAlive()).isFalse();
                assertThat(failure.get()).isInstanceOf(ConcurrentRequestException.class)
                    .hasCauseInstanceOf(InterruptedException.class);
                assertThat(interrupted).isTrue();
            } catch (Exception exception) {
                throw new RuntimeException(exception);
            } finally {
                namespace.clean(context.getBean(RedisConnectionFactory.class));
            }
        });

        int unusedPort;
        try (ServerSocket socket = new ServerSocket(0)) {
            unusedPort = socket.getLocalPort();
        }
        assertUnavailable(ResourceLockProcessProbe.createClient(namespace.value(), password, true, unusedPort));
        assertUnavailable(ResourceLockProcessProbe.createClient(namespace.value(), password + "-wrong", true,
            RedisLocalEnvironmentPrecondition.PORT));
    }

    @Test
    void realRedisReleaseFailureDoesNotReplaceBusinessSuccessOrFailure() throws Throwable {
        RedisTestNamespace namespace = RedisTestNamespace.unique("release-failure");
        runner(namespace, "100ms", "500ms").run(context -> {
            SimpleMeterRegistry meters = new SimpleMeterRegistry();
            RuntimeException businessFailure = new RuntimeException("business");
            try {
                RedissonClient successClient = ResourceLockProcessProbe.createClient(namespace.value(), password,
                    false, RedisLocalEnvironmentPrecondition.PORT);
                ResourceLockExecutor successExecutor = executor(successClient, meters);
                assertThat(successExecutor.execute(List.of(new ResolvedResourceLock("release-test", "success")),
                    () -> {
                        successClient.shutdown();
                        return "done";
                    })).isEqualTo("done");

                RedissonClient failureClient = ResourceLockProcessProbe.createClient(namespace.value(), password,
                    false, RedisLocalEnvironmentPrecondition.PORT);
                ResourceLockExecutor failureExecutor = executor(failureClient, meters);
                assertThatThrownBy(() -> failureExecutor.execute(
                    List.of(new ResolvedResourceLock("release-test", "failure")), () -> {
                        failureClient.shutdown();
                        throw businessFailure;
                    })).isSameAs(businessFailure);

                assertThat(meters.get("paytech.resource.lock")
                    .tags("prefix", "release-test", "status", "release-failed").counter().count())
                        .isEqualTo(2.0d);
            } finally {
                await().atMost(Duration.ofSeconds(2)).untilAsserted(() -> {
                    StringRedisTemplate raw = new StringRedisTemplate(
                        context.getBean(RedisConnectionFactory.class));
                    assertThat(raw.hasKey(namespace.physical("success"))).isFalse();
                    assertThat(raw.hasKey(namespace.physical("failure"))).isFalse();
                });
                namespace.clean(context.getBean(RedisConnectionFactory.class));
                meters.close();
            }
        });
    }

    private void assertSortedWhileFirstKeyIsContended(LockCommands commands, RedissonClient client,
        StringRedisTemplate raw, RedisTestNamespace namespace, boolean reverse) {
        ExecutorService worker = Executors.newSingleThreadExecutor();
        try (DirectHold ignored = hold(client, "a-resource:first", 5_000)) {
            Future<String> invocation = worker.submit(() -> reverse ? commands.multiReverse("first", "second")
                : commands.multiForward("first", "second"));
            await().during(Duration.ofMillis(150)).atMost(Duration.ofMillis(400)).untilAsserted(() ->
                assertThat(raw.hasKey(namespace.physical("z-resource:second"))).isFalse());
            assertThatThrownBy(() -> invocation.get(2, SECONDS)).hasCauseInstanceOf(ConcurrentRequestException.class);
        } catch (Exception failure) {
            throw new RuntimeException(failure);
        } finally {
            worker.shutdownNow();
        }
    }

    private DirectHold hold(RedissonClient client, String logicalKey, long leaseMillis) {
        DirectHold hold = new DirectHold(client, logicalKey, leaseMillis);
        hold.awaitAcquired();
        return hold;
    }

    private void assertUnavailable(RedissonClient client) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("redissonClient", client);
        ResourceLockExecutor executor = new ResourceLockExecutor(
            new RedissonResourceLockOperations(beanFactory.getBeanProvider(RedissonClient.class)),
            new ResourceLockObservation(null), 100_000_000L, 1_000_000_000L, System::nanoTime);
        try {
            assertThatThrownBy(() -> executor.execute(List.of(new ResolvedResourceLock("failure-test", "key")),
                () -> null)).isInstanceOf(ServiceUnavailableException.class).hasCauseInstanceOf(RuntimeException.class);
        } finally {
            client.shutdown();
        }
    }

    private ResourceLockExecutor executor(RedissonClient client, SimpleMeterRegistry meters) {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("redissonClient", client);
        return new ResourceLockExecutor(
            new RedissonResourceLockOperations(beanFactory.getBeanProvider(RedissonClient.class)),
            new ResourceLockObservation(meters), 100_000_000L, 500_000_000L, System::nanoTime);
    }

    private ApplicationContextRunner runner(RedisTestNamespace namespace, String wait, String lease) {
        return new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(DataRedisAutoConfiguration.class,
                RedisKeyNamespaceAutoConfiguration.class, RedisAutoConfiguration.class,
                RedisResourceLockAutoConfiguration.class))
            .withUserConfiguration(LockTestConfiguration.class)
            .withPropertyValues("spring.application.name=" + namespace.value(),
                "spring.data.redis.host=" + RedisLocalEnvironmentPrecondition.HOST,
                "spring.data.redis.port=" + RedisLocalEnvironmentPrecondition.PORT,
                "spring.data.redis.password=" + password, "spring.data.redis.connect-timeout=2s",
                "spring.data.redis.timeout=2s", "redis.coordination.enabled=true",
                "redis.coordination.threads=2", "redis.coordination.netty-threads=2",
                "redis.coordination.connection-minimum-idle-size=1", "redis.coordination.connection-pool-size=4",
                "redis.coordination.subscription-connection-minimum-idle-size=1",
                "redis.coordination.subscription-connection-pool-size=2",
                "redis.coordination.resource-lock.wait-time=" + wait,
                "redis.coordination.resource-lock.lease-time=" + lease);
    }

    private Process startProbe(RedisTestNamespace namespace, String logicalKey) throws Exception {
        String java = Path.of(System.getProperty("java.home"), "bin", "java").toString();
        return new ProcessBuilder(java, "-cp", System.getProperty("java.class.path"),
            ResourceLockProcessProbe.class.getName(), namespace.value(), logicalKey).redirectErrorStream(true).start();
    }

    private String readUntil(Process process, String marker) throws Exception {
        BufferedReader reader = process.inputReader(StandardCharsets.UTF_8);
        ExecutorService readerThread = Executors.newSingleThreadExecutor();
        try {
            Future<String> result = readerThread.submit(() -> {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.equals(marker)) {
                        return line;
                    }
                }
                return null;
            });
            return result.get(5, SECONDS);
        } finally {
            readerThread.shutdownNow();
        }
    }

    private void writeLine(Process process, String line) throws Exception {
        OutputStreamWriter writer = new OutputStreamWriter(process.getOutputStream(), StandardCharsets.UTF_8);
        writer.write(line + System.lineSeparator());
        writer.flush();
    }

    private void stopOwnedProcess(Process process) {
        if (process != null && process.isAlive()) {
            process.destroy();
            try {
                if (!process.waitFor(2, SECONDS)) {
                    process.destroyForcibly();
                }
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                process.destroyForcibly();
            }
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class LockTestConfiguration {
        @Bean
        LockCommands lockCommands(RedissonClient redissonClient) {
            return new LockCommands(redissonClient);
        }
    }

    static class LockCommands {
        private final RedissonClient redissonClient;

        LockCommands(RedissonClient redissonClient) {
            this.redissonClient = redissonClient;
        }

        @ResourceLock(prefix = "resource:account", key = "#resourceId")
        public String simple(String resourceId) {
            return resourceId;
        }

        @ResourceLock(prefix = "resource:account", key = "#resourceId")
        public String fail(String resourceId, RuntimeException failure) {
            throw failure;
        }

        @ResourceLock(prefix = "resource:account", key = "#resourceId")
        public String blocking(String resourceId, Gate gate) throws InterruptedException {
            gate.entered.countDown();
            gate.release.await();
            return resourceId;
        }

        @ResourceLock(prefix = "resource:account", key = "#first")
        @ResourceLock(prefix = "resource:account", key = "#second")
        public int duplicate(String first, String second) {
            return redissonClient.getLock("resource:account:" + first).getHoldCount();
        }

        @ResourceLock(prefix = "z-resource", key = "#second")
        @ResourceLock(prefix = "a-resource", key = "#first")
        public String multiReverse(String first, String second) {
            return first + second;
        }

        @ResourceLock(prefix = "a-resource", key = "#first")
        @ResourceLock(prefix = "z-resource", key = "#second")
        public String multiForward(String first, String second) {
            return first + second;
        }
    }

    static final class Gate {
        private final CountDownLatch entered = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);

        void awaitEntered() {
            try {
                assertThat(entered.await(2, SECONDS)).isTrue();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting command entry", failure);
            }
        }

        void release() {
            release.countDown();
        }
    }

    static final class DirectHold implements AutoCloseable {
        private final RLock lock;
        private final CountDownLatch acquired = new CountDownLatch(1);
        private final CountDownLatch release = new CountDownLatch(1);
        private final ExecutorService owner = Executors.newSingleThreadExecutor();
        private final Future<?> task;

        DirectHold(RedissonClient client, String logicalKey, long leaseMillis) {
            lock = client.getLock(logicalKey);
            task = owner.submit(() -> {
                boolean locked = lock.tryLock(0, leaseMillis, MILLISECONDS);
                if (!locked) {
                    throw new AssertionError("Direct test owner could not acquire lock");
                }
                acquired.countDown();
                try {
                    release.await();
                } finally {
                    if (lock.isHeldByCurrentThread()) {
                        lock.unlock();
                    }
                }
                return null;
            });
        }

        void awaitAcquired() {
            try {
                assertThat(acquired.await(2, SECONDS)).isTrue();
            } catch (InterruptedException failure) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while awaiting direct lock", failure);
            }
        }

        void release() {
            release.countDown();
        }

        @Override
        public void close() {
            release();
            try {
                task.get(2, SECONDS);
            } catch (Exception failure) {
                throw new AssertionError("Direct lock owner failed", failure);
            } finally {
                owner.shutdownNow();
            }
        }
    }
}
