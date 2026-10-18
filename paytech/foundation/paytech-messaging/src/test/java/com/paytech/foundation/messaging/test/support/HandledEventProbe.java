package com.paytech.foundation.messaging.test.support;

import java.time.Duration;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;

/**
 * 观察测试 Handler 的调用：调用顺序、并发度与处理线程；可让指定聚合的 Handler 抛出异常或放慢处理。
 */
@TestComponent
public final class HandledEventProbe implements Clear {

    private final AtomicInteger inFlight = new AtomicInteger();
    private final AtomicInteger maxInFlight = new AtomicInteger();
    private final List<String> invocations = new CopyOnWriteArrayList<>();
    private final Set<String> threads = ConcurrentHashMap.newKeySet();
    private final Set<String> failingAggregates = ConcurrentHashMap.newKeySet();
    private final Set<String> rejectingAggregates = ConcurrentHashMap.newKeySet();
    private final List<Boolean> transactionActive = new CopyOnWriteArrayList<>();
    private volatile Duration handlingDelay = Duration.ZERO;

    /**
     * Handler 开始处理。
     *
     * @param aggregateId 聚合
     * @param version 版本
     */
    public void enter(String aggregateId, long version) {
        int current = inFlight.incrementAndGet();
        maxInFlight.accumulateAndGet(current, Math::max);
        threads.add(Thread.currentThread().getName());
        invocations.add(aggregateId + "@" + version);
    }

    /** Handler 结束处理。 */
    public void exit() {
        inFlight.decrementAndGet();
    }

    /**
     * 当前配置的处理延时。
     *
     * @return 延时
     */
    public Duration handlingDelay() {
        return handlingDelay;
    }

    /**
     * 设置处理延时。
     *
     * @param delay 延时
     */
    public void slowDown(Duration delay) {
        this.handlingDelay = delay;
    }

    /**
     * 让该聚合的处理抛出异常。
     *
     * @param aggregateId 聚合
     */
    public void failAggregate(String aggregateId) {
        failingAggregates.add(aggregateId);
    }

    /**
     * 是否应失败。
     *
     * @param aggregateId 聚合
     * @return 是返回 true
     */
    public boolean shouldFail(String aggregateId) {
        return failingAggregates.contains(aggregateId);
    }

    /**
     * 让该聚合的处理抛出 {@code DomainEventRejectedException}。
     *
     * @param aggregateId 聚合
     */
    public void rejectAggregate(String aggregateId) {
        rejectingAggregates.add(aggregateId);
    }

    /**
     * 是否应拒绝。
     *
     * @param aggregateId 聚合
     * @return 是返回 true
     */
    public boolean shouldReject(String aggregateId) {
        return rejectingAggregates.contains(aggregateId);
    }

    /**
     * 记录 Handler 被调用时是否处于事务中。
     *
     * @param active 是否有活动事务
     */
    public void recordTransactionActive(boolean active) {
        transactionActive.add(active);
    }

    /**
     * 每次调用时的事务状态。
     *
     * @return 按调用顺序
     */
    public List<Boolean> transactionActive() {
        return List.copyOf(transactionActive);
    }

    /**
     * 全部调用记录，形如 {@code aggregate@version}。
     *
     * @return 调用记录
     */
    public List<String> invocations() {
        return List.copyOf(invocations);
    }

    /**
     * 观察到的最大并发处理数。
     *
     * @return 最大并发
     */
    public int maxInFlight() {
        return maxInFlight.get();
    }

    /**
     * 当前正在处理的数量。
     *
     * @return 在途数
     */
    public int inFlight() {
        return inFlight.get();
    }

    /**
     * 处理过事件的线程数。
     *
     * @return 线程数
     */
    public int threadCount() {
        return threads.size();
    }

    @Override
    public void clear() {
        invocations.clear();
        threads.clear();
        failingAggregates.clear();
        rejectingAggregates.clear();
        transactionActive.clear();
        maxInFlight.set(0);
        handlingDelay = Duration.ZERO;
    }
}
