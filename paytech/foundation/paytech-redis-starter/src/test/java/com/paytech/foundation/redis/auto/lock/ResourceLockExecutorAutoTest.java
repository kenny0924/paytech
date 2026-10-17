package com.paytech.foundation.redis.auto.lock;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.LongSupplier;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.redis.core.lock.ResolvedResourceLock;
import com.paytech.foundation.redis.core.lock.ResourceLockExecutor;
import com.paytech.foundation.redis.core.lock.ResourceLockObservation;
import com.paytech.foundation.redis.test.fake.InMemoryResourceLockOperations;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(OutputCaptureExtension.class)
class ResourceLockExecutorAutoTest {

    private static final long WAIT_NANOS = 500_000_000L;
    private static final long LEASE_NANOS = 30_000_000_000L;

    @AfterEach
    void clearInterrupt() {
        Thread.interrupted();
    }

    @Test
    void acquiresInOrderAndReleasesInReverseAroundTheBusinessResult() throws Throwable {
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        ResourceLockExecutor executor = executor(operations);

        Object result = executor.execute(locks("a", "b"), () -> "done");

        assertThat(result).isEqualTo("done");
        assertThat(operations.events()).containsExactly("lock:a", "acquire:a", "lock:b", "acquire:b", "release:b",
            "release:a");
        assertThat(operations.leases()).containsExactly(LEASE_NANOS, LEASE_NANOS);
    }

    @Test
    void releasesPartialAcquisitionAndNeverRunsBusinessOnContention() {
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        operations.contend("b");
        AtomicInteger businessCalls = new AtomicInteger();

        assertThatThrownBy(() -> executor(operations).execute(locks("a", "b"), () -> {
            businessCalls.incrementAndGet();
            return null;
        })).isInstanceOf(ConcurrentRequestException.class)
            .hasMessage("该资源正在处理其他请求，请稍后重试");
        assertThat(businessCalls).hasValue(0);
        assertThat(operations.events()).containsExactly("lock:a", "acquire:a", "lock:b", "acquire:b", "release:a");
    }

    @Test
    void givesAllLocksOneTotalWaitBudgetAndStillTriesWithZeroRemaining() throws Throwable {
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        LongSupplier clock = new SequenceClock(100L, 100L, 300L, 700L, 700L);
        ResourceLockExecutor executor = new ResourceLockExecutor(operations, observation(), 500L, 1_000L, clock);

        executor.execute(locks("a", "b", "c"), () -> null);

        assertThat(operations.waits()).containsExactly(500L, 300L, 0L);
    }

    @Test
    void keepsTheFullBudgetAcrossNanoTimeWraparound() throws Throwable {
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        LongSupplier clock = new SequenceClock(Long.MAX_VALUE - 100L, Long.MAX_VALUE - 100L,
            Long.MIN_VALUE + 100L);
        ResourceLockExecutor executor = new ResourceLockExecutor(operations, observation(), 500L, 1_000L, clock);

        executor.execute(locks("a", "b"), () -> null);

        assertThat(operations.waits()).containsExactly(500L, 299L);
    }

    @Test
    void restoresInterruptAndMapsDirectOrWrappedInterruptionToConflict() {
        InMemoryResourceLockOperations directOperations = new InMemoryResourceLockOperations();
        InterruptedException directCause = new InterruptedException("direct-interrupted");
        directOperations.interruptAcquire("a", directCause);

        assertThatThrownBy(() -> executor(directOperations).execute(locks("a"), () -> null))
            .isInstanceOf(ConcurrentRequestException.class).hasCause(directCause);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
        Thread.interrupted();

        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        InterruptedException cause = new InterruptedException("interrupted");
        operations.failAcquire("a", new RuntimeException(cause));

        assertThatThrownBy(() -> executor(operations).execute(locks("a"), () -> null))
            .isInstanceOf(ConcurrentRequestException.class).hasCause(cause);
        assertThat(Thread.currentThread().isInterrupted()).isTrue();
    }

    @Test
    void mapsOtherRedisFailuresToServiceUnavailableWithCause() {
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        IllegalStateException cause = new IllegalStateException("redis-down");
        operations.failAcquire("a", cause);

        assertThatThrownBy(() -> executor(operations).execute(locks("a"), () -> null))
            .isInstanceOf(ServiceUnavailableException.class).hasCause(cause);
    }

    @Test
    void preservesBusinessThrowableAndResultWhenReleaseFailsOrLeaseExpired() throws Throwable {
        InMemoryResourceLockOperations releaseFailure = new InMemoryResourceLockOperations();
        releaseFailure.failRelease("a", new IllegalMonitorStateException("lost"));
        assertThat(executor(releaseFailure).execute(locks("a"), () -> "committed")).isEqualTo("committed");

        InMemoryResourceLockOperations businessFailure = new InMemoryResourceLockOperations();
        businessFailure.failRelease("a", new IllegalStateException("redis-down"));
        RuntimeException original = new RuntimeException("business");
        assertThatThrownBy(() -> executor(businessFailure).execute(locks("a"), () -> {
            throw original;
        })).isSameAs(original);

        InMemoryResourceLockOperations expired = new InMemoryResourceLockOperations();
        executor(expired).execute(locks("a"), () -> {
            expired.loseOwnership("a");
            return null;
        });
        assertThat(expired.events()).doesNotContain("release:a");
    }

    @Test
    void recordsOwnershipLossWithoutUnlockingOrChangingTheBusinessResult() throws Throwable {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        ResourceLockExecutor executor = new ResourceLockExecutor(operations, new ResourceLockObservation(meters),
            WAIT_NANOS, LEASE_NANOS, System::nanoTime);

        assertThat(executor.execute(locks("secret-runtime-key"), () -> {
            operations.loseOwnership("secret-runtime-key");
            return "committed";
        })).isEqualTo("committed");

        assertThat(operations.events()).doesNotContain("release:secret-runtime-key");
        assertThat(meters.get("paytech.resource.lock").tag("prefix", "test-prefix")
            .tag("status", "ownership-lost").counter().count()).isEqualTo(1.0d);
        assertThat(meters.getMeters()).allSatisfy(meter -> assertThat(meter.getId().getTags())
            .extracting(tag -> tag.getValue()).doesNotContain("secret-runtime-key"));
    }

    @Test
    void preservesCheckedThrowableAndErrorExactly() {
        CheckedFailure checked = new CheckedFailure();
        AssertionError error = new AssertionError("business-error");

        assertThatThrownBy(() -> executor(new InMemoryResourceLockOperations()).execute(locks("a"), () -> {
            throw checked;
        })).isSameAs(checked);
        assertThatThrownBy(() -> executor(new InMemoryResourceLockOperations()).execute(locks("a"), () -> {
            throw error;
        })).isSameAs(error);
    }

    @Test
    void ownershipCheckFailureDoesNotOverrideTheBusinessResult() throws Throwable {
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        operations.failOwnershipCheck("a", new IllegalStateException("redis-down"));

        assertThat(executor(operations).execute(locks("a"), () -> "committed")).isEqualTo("committed");
    }

    @Test
    void observationFailureDuringReleaseNeverOverridesTheBusinessResultOrLeaksDetails(CapturedOutput output)
        throws Throwable {
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        operations.failRelease("secret-runtime-key", new IllegalStateException("secret-runtime-key"));
        SimpleMeterRegistry throwingMeters = new SimpleMeterRegistry() {
            @Override
            public io.micrometer.core.instrument.Counter counter(String name, String... tags) {
                if (java.util.Arrays.asList(tags).contains("release-failed")) {
                    throw new IllegalStateException("secret-runtime-key");
                }
                return super.counter(name, tags);
            }
        };
        ResourceLockExecutor executor = new ResourceLockExecutor(operations,
            new ResourceLockObservation(throwingMeters), WAIT_NANOS, LEASE_NANOS, System::nanoTime);

        assertThat(executor.execute(locks("secret-runtime-key"), () -> "committed")).isEqualTo("committed");
        assertThat(output).doesNotContain("secret-runtime-key");
    }

    @Test
    void recordsOnlyPrefixAndFixedLowCardinalityStatus() {
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        InMemoryResourceLockOperations operations = new InMemoryResourceLockOperations();
        ResourceLockObservation observation = new ResourceLockObservation(meters);
        ResourceLockExecutor executor = new ResourceLockExecutor(operations, observation, WAIT_NANOS, LEASE_NANOS,
            System::nanoTime);

        assertThatThrownBy(() -> executor.execute(List.of(new ResolvedResourceLock("operations:account",
            "operations:account:secret-42")), () -> {
                throw new RuntimeException("business");
            })).isInstanceOf(RuntimeException.class).hasMessage("business");

        assertThat(meters.getMeters()).allSatisfy(meter -> {
            assertThat(meter.getId().getTags()).extracting(tag -> tag.getValue()).doesNotContain("secret-42");
        });
    }

    private ResourceLockExecutor executor(InMemoryResourceLockOperations operations) {
        return new ResourceLockExecutor(operations, observation(), WAIT_NANOS, LEASE_NANOS, System::nanoTime);
    }

    private ResourceLockObservation observation() {
        return new ResourceLockObservation(new SimpleMeterRegistry());
    }

    private List<ResolvedResourceLock> locks(String... keys) {
        return java.util.Arrays.stream(keys).map(key -> new ResolvedResourceLock("test-prefix", key)).toList();
    }

    private static final class SequenceClock implements LongSupplier {
        private final long[] values;
        private int index;

        private SequenceClock(long... values) {
            this.values = values;
        }

        @Override
        public long getAsLong() {
            return values[Math.min(index++, values.length - 1)];
        }
    }

    private static final class CheckedFailure extends Exception {}
}
