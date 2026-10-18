package com.paytech.foundation.redis.core.lock;

import java.util.ArrayList;
import java.util.List;
import java.util.function.LongSupplier;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.redis.core.autoconfigure.RedisResourceLockProperties;

/** 在一个总等待预算内获取全部锁，并在业务终态后逆序释放。 */
public final class ResourceLockExecutor {

    private static final String CONTENTION_MESSAGE = "该资源正在处理其他请求，请稍后重试";

    private final ResourceLockOperations operations;
    private final ResourceLockObservation observation;
    private final LongSupplier waitNanos;
    private final LongSupplier leaseNanos;
    private final LongSupplier nanoTime;

    public ResourceLockExecutor(ResourceLockOperations operations, ResourceLockObservation observation,
        long waitNanos, long leaseNanos, LongSupplier nanoTime) {
        this.operations = operations;
        this.observation = observation;
        this.waitNanos = () -> waitNanos;
        this.leaseNanos = () -> leaseNanos;
        this.nanoTime = nanoTime;
    }

    public ResourceLockExecutor(ResourceLockOperations operations, ResourceLockObservation observation,
        RedisResourceLockProperties properties) {
        this.operations = operations;
        this.observation = observation;
        waitNanos = properties::requiredWaitNanos;
        leaseNanos = properties::requiredLeaseNanos;
        nanoTime = System::nanoTime;
    }

    public Object execute(List<ResolvedResourceLock> locks, Invocation invocation) throws Throwable {
        long startedAt = nanoTime.getAsLong();
        long invocationWaitNanos = waitNanos.getAsLong();
        long invocationLeaseNanos = leaseNanos.getAsLong();
        var acquired = new ArrayList<AcquiredLock>(locks.size());
        try {
            for (ResolvedResourceLock lock : locks) {
                acquire(lock, remaining(startedAt, invocationWaitNanos), invocationLeaseNanos, acquired);
            }
            return invocation.proceed();
        } finally {
            release(acquired);
        }
    }

    boolean uses(ResourceLockOperations expectedOperations, ResourceLockObservation expectedObservation) {
        return operations == expectedOperations && observation == expectedObservation;
    }

    private void acquire(ResolvedResourceLock resource, long remainingWait, long invocationLeaseNanos,
        List<AcquiredLock> acquired) {
        ResourceLockOperations.LockHandle handle;
        try {
            handle = operations.lock(resource.logicalKey());
            if (!handle.tryLock(remainingWait, invocationLeaseNanos)) {
                observation.contended(resource.prefix());
                throw new ConcurrentRequestException(CONTENTION_MESSAGE);
            }
            acquired.add(new AcquiredLock(resource.prefix(), handle));
            observation.acquired(resource.prefix());
        } catch (InterruptedException failure) {
            Thread.currentThread().interrupt();
            observation.interrupted(resource.prefix());
            throw new ConcurrentRequestException(CONTENTION_MESSAGE, failure);
        } catch (ConcurrentRequestException failure) {
            throw failure;
        } catch (RuntimeException failure) {
            if (failure.getCause() instanceof InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                observation.interrupted(resource.prefix());
                throw new ConcurrentRequestException(CONTENTION_MESSAGE, interrupted);
            }
            observation.unavailable(resource.prefix());
            throw new ServiceUnavailableException(failure);
        }
    }

    private void release(List<AcquiredLock> acquired) {
        for (int index = acquired.size() - 1; index >= 0; index--) {
            AcquiredLock lock = acquired.get(index);
            try {
                if (lock.handle().isHeldByCurrentThread()) {
                    lock.handle().unlock();
                } else {
                    observation.ownershipLost(lock.prefix());
                }
            } catch (RuntimeException failure) {
                observation.releaseFailed(lock.prefix(), failure);
            }
        }
    }

    private long remaining(long startedAt, long totalWait) {
        long elapsed = nanoTime.getAsLong() - startedAt;
        if (elapsed <= 0) {
            return totalWait;
        }
        return elapsed >= totalWait ? 0 : totalWait - elapsed;
    }

    @FunctionalInterface
    public interface Invocation {
        Object proceed() throws Throwable;
    }

    private record AcquiredLock(String prefix, ResourceLockOperations.LockHandle handle) {}
}
