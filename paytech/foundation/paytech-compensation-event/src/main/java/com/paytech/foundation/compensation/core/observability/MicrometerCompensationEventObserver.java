package com.paytech.foundation.compensation.core.observability;

import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicLong;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * 使用受控事件及结果维度的 Micrometer 观测实现。
 *
 * @author Codex
 */
public final class MicrometerCompensationEventObserver implements CompensationEventObserver {

    private static final String METRIC_PREFIX = "compensation.";

    private final MeterRegistry meterRegistry;
    private final AtomicLong backlogRows = new AtomicLong();
    private final AtomicLong oldestPendingNanos = new AtomicLong();
    private final AtomicLong expiredLeaseRows = new AtomicLong();

    public MicrometerCompensationEventObserver(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "meterRegistry must not be null");
        registerBacklogGauges();
    }

    @Override
    public void registrationFinished(String eventType, boolean duplicate) {
        lifecycleCounter("registrations", eventType, "result", duplicate ? "duplicate" : "created").increment();
    }

    @Override
    public void claimFinished(String eventType, boolean leaseTakeover) {
        lifecycleCounter("claims", eventType, "result", leaseTakeover ? "lease_takeover" : "claimed").increment();
    }

    @Override
    public void executorRejected() {
        Counter.builder(METRIC_PREFIX + "executor.rejections").register(meterRegistry).increment();
    }

    @Override
    public void handlerCapacityRejected() {
        Counter.builder(METRIC_PREFIX + "handler.capacity.rejections").register(meterRegistry).increment();
    }

    @Override
    public void attemptFinished(String eventType, String result, Duration duration) {
        Counter.builder(METRIC_PREFIX + "attempts").tag("event.type", eventType).tag("result", result)
            .register(meterRegistry).increment();
        Timer.builder(METRIC_PREFIX + "handler.duration").tag("event.type", eventType).tag("result", result)
            .register(meterRegistry).record(duration);
    }

    @Override
    public void staleTransition(String eventType, String result) {
        lifecycleCounter("stale.transitions", eventType, "result", result).increment();
    }

    @Override
    public void eventFailed(String eventType, String reason) {
        lifecycleCounter("failed.events", eventType, "reason", reason).increment();
    }

    @Override
    public void manualReopened(String eventType) {
        lifecycleCounter("manual.reopens", eventType, "result", "reopened").increment();
    }

    @Override
    public void backlogObserved(CompensationBacklogSnapshot snapshot) {
        CompensationBacklogSnapshot requiredSnapshot = Objects.requireNonNull(snapshot, "snapshot must not be null");
        backlogRows.set(requiredSnapshot.pendingRows());
        oldestPendingNanos.set(requiredSnapshot.oldestPendingAge().toNanos());
        expiredLeaseRows.set(requiredSnapshot.expiredLeaseRows());
    }

    private Counter lifecycleCounter(String suffix, String eventType, String dimension, String value) {
        return Counter.builder(METRIC_PREFIX + suffix).tag("event.type", eventType).tag(dimension, value)
            .register(meterRegistry);
    }

    private void registerBacklogGauges() {
        Gauge.builder(METRIC_PREFIX + "backlog.rows", backlogRows, AtomicLong::doubleValue).register(meterRegistry);
        Gauge.builder(METRIC_PREFIX + "oldest.pending.seconds", oldestPendingNanos,
            value -> value.doubleValue() / 1_000_000_000.0D).register(meterRegistry);
        Gauge.builder(METRIC_PREFIX + "expired.leases", expiredLeaseRows, AtomicLong::doubleValue)
            .register(meterRegistry);
    }
}
