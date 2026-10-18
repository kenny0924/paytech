package com.paytech.foundation.messaging.ordering;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 按 {@code aggregateId} 哈希到固定线程的串行执行器（design/08 §3）。
 * <li>同一聚合的任务恒在同一条线程按提交顺序执行；不同聚合分散到不同线程并行。</li>
 * <li>每条线程的等待队列有界；队列满时抛出 {@link java.util.concurrent.RejectedExecutionException}，调用方保留任务待扫描兜底。</li>
 * <li>补偿事件 Dispatcher 经 {@link AggregateKeySequencer} 接入；本类不依赖补偿组件。</li>
 *
 * @author Claude
 */
public final class AggregateSerialExecutor implements AutoCloseable {

    private final List<ThreadPoolExecutor> lanes;

    /**
     * 创建执行器。
     *
     * @param laneCount 线程数，1..256
     * @param queueCapacityPerLane 每条线程的等待队列容量，正数
     * @param threadNamePrefix 线程名前缀
     */
    public AggregateSerialExecutor(int laneCount, int queueCapacityPerLane, String threadNamePrefix) {
        if (laneCount < 1 || laneCount > 256) {
            throw new IllegalArgumentException("laneCount must be between 1 and 256");
        }
        if (queueCapacityPerLane < 1) {
            throw new IllegalArgumentException("queueCapacityPerLane must be positive");
        }
        Objects.requireNonNull(threadNamePrefix, "threadNamePrefix must not be null");
        List<ThreadPoolExecutor> created = new ArrayList<>(laneCount);
        for (int index = 0; index < laneCount; index++) {
            created.add(lane(threadNamePrefix + index, queueCapacityPerLane));
        }
        this.lanes = List.copyOf(created);
    }

    /**
     * 在该聚合对应的线程上排队执行任务。
     *
     * @param aggregateId 聚合标识
     * @param task 任务
     * @throws java.util.concurrent.RejectedExecutionException 对应线程队列已满或执行器已关闭
     */
    public void execute(String aggregateId, Runnable task) {
        Objects.requireNonNull(task, "task must not be null");
        lanes.get(laneOf(aggregateId)).execute(task);
    }

    /**
     * 该聚合对应的执行线程，作为普通 {@link Executor} 交给需要它的组件。
     *
     * @param aggregateId 聚合标识
     * @return 固定线程的执行器
     */
    public Executor executorFor(String aggregateId) {
        ThreadPoolExecutor lane = lanes.get(laneOf(aggregateId));
        return lane::execute;
    }

    /**
     * 聚合落到的线程序号。
     *
     * @param aggregateId 聚合标识
     * @return 0..laneCount-1
     */
    public int laneOf(String aggregateId) {
        Objects.requireNonNull(aggregateId, "aggregateId must not be null");
        return Math.floorMod(aggregateId.hashCode(), lanes.size());
    }

    /**
     * 全部线程上正在执行与排队等待的任务数，供宿主判断是否空闲。
     *
     * @return 任务数
     */
    public int pendingTaskCount() {
        int pending = 0;
        for (ThreadPoolExecutor lane : lanes) {
            pending += lane.getQueue().size() + lane.getActiveCount();
        }
        return pending;
    }

    /**
     * 停止接收新任务，并等待已排队任务结束。
     *
     * @param timeoutMillis 每条线程的最长等待毫秒数
     * @return 全部线程在时限内结束返回 true
     * @throws InterruptedException 等待被中断
     */
    public boolean shutdownAndAwait(long timeoutMillis) throws InterruptedException {
        lanes.forEach(ThreadPoolExecutor::shutdown);
        boolean terminated = true;
        for (ThreadPoolExecutor lane : lanes) {
            terminated &= lane.awaitTermination(timeoutMillis, TimeUnit.MILLISECONDS);
        }
        return terminated;
    }

    /**
     * 立即停止全部线程，未执行的任务被丢弃（由补偿扫描兜底）。
     */
    @Override
    public void close() {
        lanes.forEach(ThreadPoolExecutor::shutdownNow);
    }

    private static ThreadPoolExecutor lane(String threadName, int queueCapacity) {
        AtomicInteger sequence = new AtomicInteger();
        return new ThreadPoolExecutor(1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(queueCapacity),
            runnable -> {
                Thread thread = new Thread(runnable, threadName + "-" + sequence.incrementAndGet());
                thread.setDaemon(true);
                return thread;
            }, new ThreadPoolExecutor.AbortPolicy());
    }
}
