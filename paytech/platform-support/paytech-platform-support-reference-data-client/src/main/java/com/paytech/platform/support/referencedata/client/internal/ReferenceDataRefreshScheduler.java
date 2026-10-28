package com.paytech.platform.support.referencedata.client.internal;

import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;

import com.paytech.platform.support.referencedata.client.ReferenceDataKind;
import com.paytech.platform.support.referencedata.client.ReferenceDataRefresher;
import com.paytech.platform.support.referencedata.client.ReferenceItem;

/**
 * 刷新入口与自带调度：单线程守护调度器 {@code reference-data-refresh}，不依赖宿主 {@code @EnableScheduling}。
 * <li>应用启动后立即在调度线程开始首次加载，不阻塞启动；只要还有种类未成功，就按 initial-retry-interval 只重试未成功的种类；
 * 全部成功后按 refresh-interval 全量刷新所有已开启的种类。应用关闭时停止调度。</li>
 * <li>每类独立：成功整体原子替换，失败保留旧快照、记 WARN 并累计连续失败；HTTP 只发生在调度线程或 {@link #refreshNow()}，
 * 不进入任何业务事务。</li>
 * <li>{@code scheduling-enabled=false} 时不启动调度线程，只能经 {@link #refreshNow()} 刷新。</li>
 */
public final class ReferenceDataRefreshScheduler implements ReferenceDataRefresher, SmartLifecycle {

    private static final Logger LOG = LoggerFactory.getLogger(ReferenceDataRefreshScheduler.class);

    private static final String THREAD_NAME = "reference-data-refresh";

    private final Clock clock;

    private final boolean schedulingEnabled;

    private final Duration refreshInterval;

    private final Duration initialRetryInterval;

    private final ReferenceDataSnapshot snapshot;

    private final PlatformReferenceDataLoader loader;

    private volatile ScheduledExecutorService executor;

    private volatile boolean running;

    /**
     * 创建刷新入口
     *
     * @param snapshot 快照
     * @param loader Platform 拉取与校验
     * @param schedulingEnabled 是否启动自带调度
     * @param refreshInterval 全部加载成功后的全量刷新间隔
     * @param initialRetryInterval 首次全部成功前的重试间隔
     */
    public ReferenceDataRefreshScheduler(ReferenceDataSnapshot snapshot, PlatformReferenceDataLoader loader,
        boolean schedulingEnabled, Duration refreshInterval, Duration initialRetryInterval) {
        this.clock = Clock.systemUTC();
        this.snapshot = snapshot;
        this.loader = loader;
        this.schedulingEnabled = schedulingEnabled;
        this.refreshInterval = refreshInterval;
        this.initialRetryInterval = initialRetryInterval;
    }

    /** 同步刷新全部已开启的种类；与调度线程互斥。 */
    @Override
    public List<Result> refreshNow() {
        return refresh(snapshot.enabledKinds());
    }

    /** 开启调度时立即提交首次加载，不在启动线程发 HTTP。 */
    @Override
    public void start() {
        if (schedulingEnabled) {
            executor = Executors.newSingleThreadScheduledExecutor(task -> {
                Thread thread = new Thread(task, THREAD_NAME);
                thread.setDaemon(true);
                return thread;
            });
            executor.execute(this::runScheduled);
        }
        running = true;
    }

    /** 停止调度并中断正在进行的刷新；快照保持最后一次成功的内容。 */
    @Override
    public void stop() {
        running = false;
        ScheduledExecutorService current = executor;
        if (current != null) {
            current.shutdownNow();
        }
    }

    /** 生命周期状态。 */
    @Override
    public boolean isRunning() {
        return running;
    }

    /** 调度线程的一次执行：未全部就绪时只重试未成功的种类，之后全量刷新；无论结果都安排下一次。 */
    private void runScheduled() {
        try {
            List<ReferenceDataKind> unloaded = snapshot.unloadedKinds();
            refresh(unloaded.isEmpty() ? snapshot.enabledKinds() : unloaded);
        } finally {
            scheduleNext(snapshot.allLoaded() ? refreshInterval : initialRetryInterval);
        }
    }

    /** 安排下一次执行；应用关闭后不再安排。 */
    private void scheduleNext(Duration delay) {
        ScheduledExecutorService current = executor;
        if (!running || current == null || current.isShutdown()) {
            return;
        }
        try {
            current.schedule(this::runScheduled, delay.toMillis(), TimeUnit.MILLISECONDS);
        } catch (RejectedExecutionException closing) {
            LOG.debug("Reference data refresh not rescheduled because the scheduler is shutting down");
        }
    }

    /**
     * 逐个种类拉取并替换；同一时刻只有一次刷新
     * <li>Platform 不可用或响应不合法只影响该种类本次刷新，不向调用方抛出。</li>
     */
    private synchronized List<Result> refresh(Collection<ReferenceDataKind> kinds) {
        List<Result> results = new ArrayList<>(kinds.size());
        for (ReferenceDataKind kind : kinds) {
            results.add(refreshKind(kind));
        }
        return results;
    }

    /** 拉取一个种类：成功整体替换，失败记录并保留旧快照。 */
    private Result refreshKind(ReferenceDataKind kind) {
        try {
            List<? extends ReferenceItem> items = loader.load(kind);
            snapshot.replace(kind, items, clock.instant());
            return new Result(kind, true, items.size(), null);
        } catch (RuntimeException failure) {
            snapshot.recordFailure(kind, failure);
            ReferenceDataSnapshot.KindStatus status = snapshot.status(kind);
            LOG.warn("Reference data refresh failed, keeping previous snapshot: kind={}, loadedAt={}, "
                + "consecutiveFailures={}, cause={}: {}", kind.getCode(), status.loadedAt(),
                status.consecutiveFailures(), failure.getClass().getSimpleName(), failure.getMessage());
            return new Result(kind, false, 0, failure.getClass().getName());
        }
    }
}
