package com.paytech.platform.support.core.audit;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.platform.support.auditsecurity.configuration.OperationsAuditProperties;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditChainClosedException;
import com.paytech.platform.support.auditsecurity.domain.audit.AuditModels.Event;
import com.paytech.platform.support.auditsecurity.domainservice.service.OperationsAuditWriter;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import jakarta.annotation.PreDestroy;

/** 请求只做非阻塞入队；单写线程和独立写池共同限制审计资源。 */
@Component
@org.springframework.boot.autoconfigure.condition.ConditionalOnExpression("${operations.authentication.enabled:false} || ${client.authentication.enabled:false}")
public final class AuditDispatcher implements AutoCloseable {
    private static final Logger LOG = LoggerFactory.getLogger(AuditDispatcher.class);
    private final OperationsAuditWriter writer;
    private final ArrayBlockingQueue<Event> queue;
    private final AtomicInteger pending = new AtomicInteger();
    private final AtomicLong dropped = new AtomicLong();
    private final AtomicLong nextDiagnostic = new AtomicLong();
    private final Map<String, Counter> dropCounters = new HashMap<>();
    private final Object acceptance = new Object();
    private final Thread worker;
    private final int shutdownMillis;
    private Counter accepted;
    private Counter written;
    private Timer writeDuration;
    private volatile boolean accepting = true;
    private volatile boolean stopping;

    public AuditDispatcher(MeterRegistry registry, OperationsAuditWriter writer, OperationsAuditProperties budgets) {
        this.writer = writer;
        queue = new ArrayBlockingQueue<>(budgets.getQueueCapacity());
        shutdownMillis = budgets.getShutdownTimeoutMillis();
        registerMetrics(registry);
        worker = new Thread(this::consume, "platform-audit-writer");
        worker.setDaemon(true);
        worker.start();
    }

    private void registerMetrics(MeterRegistry registry) {
        try {
            accepted = registry.counter("platform.audit.accepted");
            written = registry.counter("platform.audit.written");
            writeDuration = registry.timer("platform.audit.write.duration");
            for (String reason : new String[]{"capture", "oversize", "full", "closed", "write", "late_chain"}) {
                dropCounters.put(reason, registry.counter("platform.audit.dropped", "reason", reason));
            }
            registry.gauge("platform.audit.pending", pending);
            registry.gauge("platform.audit.queue.size", queue, ArrayBlockingQueue::size);
        } catch (RuntimeException ignored) {
            // 可观测性故障不能阻止业务启动或审计后台的剩余能力。
        }
    }

    public boolean offer(Event event) {
        try {
            if (event == null) {
                drop("capture");
                return false;
            }
            synchronized (acceptance) {
                if (!accepting) {
                    drop("closed");
                    return false;
                }
                pending.incrementAndGet();
                if (!queue.offer(event)) {
                    pending.decrementAndGet();
                    drop("full");
                    return false;
                }
            }
            increment(accepted);
            return true;
        } catch (RuntimeException ignored) {
            drop("capture");
            return false;
        }
    }

    public int pendingCount() { return pending.get(); }

    void drop(String reason) {
        long count = dropped.incrementAndGet();
        increment(dropCounters.get(reason));
        long now = System.nanoTime();
        long next = nextDiagnostic.get();
        if (now >= next && nextDiagnostic.compareAndSet(next, now + TimeUnit.MINUTES.toNanos(1))) {
            try {
                LOG.warn("Platform audit dropped classification={} total={}", reason, count);
            } catch (RuntimeException ignored) {
                /* 不递归诊断日志系统自身。 */
            }
        }
    }

    private void consume() {
        while (!stopping && (accepting || pending.get() > 0)) {
            try {
                Event event = queue.poll(100, TimeUnit.MILLISECONDS);
                if (event != null){
                    write(event);
                }
            } catch (InterruptedException interrupted) {
                if (stopping) return;
            } catch (RuntimeException ignored) {
                drop("capture");
            }
        }
    }

    private void write(Event event) {
        long started = System.nanoTime();
        try {
            writer.write(event);
            increment(written);
        } catch (AuditChainClosedException ignored) {
            drop("late_chain");
        } catch (RuntimeException ignored) {
            drop("write");
        } finally {
            pending.decrementAndGet();
            try {
                if (writeDuration != null) writeDuration.record(System.nanoTime() - started, TimeUnit.NANOSECONDS);
            } catch (RuntimeException ignored) { /* 度量失败不终止后续事件。 */ }
            try { MDC.clear(); } catch (RuntimeException ignored) { /* 不向业务线程传播。 */ }
        }
    }

    private static void increment(Counter counter) {
        try {
            if (counter != null) {
                counter.increment();
            }
        }
        catch (RuntimeException ignored) { /* 固定度量的实现也属于审计隔离边界。 */ }
    }

    @Override
    @PreDestroy
    public void close() {
        synchronized (acceptance) { accepting = false; }
        try { worker.join(shutdownMillis); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); }
        stopping = true;
        synchronized (acceptance) {
            while (queue.poll() != null) {
                pending.decrementAndGet();
                drop("closed");
            }
        }
        worker.interrupt();
    }
}
