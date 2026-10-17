package com.paytech.foundation.redis.test.fake;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.paytech.foundation.redis.core.lock.ResourceLockOperations;

public final class InMemoryResourceLockOperations implements ResourceLockOperations {

    private final List<String> events;
    private final List<Long> waits = new ArrayList<>();
    private final List<Long> leases = new ArrayList<>();
    private final Map<String, Boolean> acquireResults = new HashMap<>();
    private final Map<String, RuntimeException> acquireFailures = new HashMap<>();
    private final Map<String, InterruptedException> interruptions = new HashMap<>();
    private final Map<String, Boolean> ownership = new HashMap<>();
    private final Map<String, RuntimeException> ownershipFailures = new HashMap<>();
    private final Map<String, RuntimeException> releaseFailures = new HashMap<>();

    public InMemoryResourceLockOperations() {
        this(new ArrayList<>());
    }

    public InMemoryResourceLockOperations(List<String> events) {
        this.events = events;
    }

    @Override
    public LockHandle lock(String logicalKey) {
        events.add("lock:" + logicalKey);
        return new LockHandle() {
            @Override
            public boolean tryLock(long waitNanos, long leaseNanos) throws InterruptedException {
                events.add("acquire:" + logicalKey);
                waits.add(waitNanos);
                leases.add(leaseNanos);
                InterruptedException interruption = interruptions.get(logicalKey);
                if (interruption != null) {
                    throw interruption;
                }
                RuntimeException failure = acquireFailures.get(logicalKey);
                if (failure != null) {
                    throw failure;
                }
                boolean acquired = acquireResults.getOrDefault(logicalKey, true);
                ownership.put(logicalKey, acquired);
                return acquired;
            }

            @Override
            public boolean isHeldByCurrentThread() {
                RuntimeException failure = ownershipFailures.get(logicalKey);
                if (failure != null) {
                    throw failure;
                }
                return ownership.getOrDefault(logicalKey, false);
            }

            @Override
            public void unlock() {
                events.add("release:" + logicalKey);
                RuntimeException failure = releaseFailures.get(logicalKey);
                if (failure != null) {
                    throw failure;
                }
                ownership.put(logicalKey, false);
            }
        };
    }

    public void contend(String logicalKey) {
        acquireResults.put(logicalKey, false);
    }

    public void failAcquire(String logicalKey, RuntimeException failure) {
        acquireFailures.put(logicalKey, failure);
    }

    public void interruptAcquire(String logicalKey, InterruptedException failure) {
        interruptions.put(logicalKey, failure);
    }

    public void loseOwnership(String logicalKey) {
        ownership.put(logicalKey, false);
    }

    public void failOwnershipCheck(String logicalKey, RuntimeException failure) {
        ownershipFailures.put(logicalKey, failure);
    }

    public void failRelease(String logicalKey, RuntimeException failure) {
        releaseFailures.put(logicalKey, failure);
    }

    public List<String> events() {
        return List.copyOf(events);
    }

    public List<Long> waits() {
        return List.copyOf(waits);
    }

    public List<Long> leases() {
        return List.copyOf(leases);
    }
}
