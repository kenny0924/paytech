package com.paytech.foundation.redis.core.lock;

/** Resource Lock 执行器与 Redisson 之间的内部最小端口。 */
public interface ResourceLockOperations {

    LockHandle lock(String logicalKey);

    interface LockHandle {
        boolean tryLock(long waitNanos, long leaseNanos) throws InterruptedException;

        boolean isHeldByCurrentThread();

        void unlock();
    }
}
