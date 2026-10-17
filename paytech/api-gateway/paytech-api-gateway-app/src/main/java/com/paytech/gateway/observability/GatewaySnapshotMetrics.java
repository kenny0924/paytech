package com.paytech.gateway.observability;

import java.util.List;
import java.util.Objects;
import java.util.function.ToDoubleFunction;

import org.springframework.stereotype.Component;

import com.paytech.gateway.routing.snapshot.GatewaySnapshotStatus;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotStore;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

/** 通过固定标签 Gauge 公开脱敏的 Gateway Snapshot 运行诊断。 */
@Component
public final class GatewaySnapshotMetrics {
    public static final String VALID_GAUGE = "gateway.snapshot.valid";
    public static final String LAST_SUCCESS_AGE_GAUGE = "gateway.snapshot.last.success.age.seconds";
    public static final String CONSECUTIVE_FAILURES_GAUGE = "gateway.snapshot.consecutive.failures";
    public static final String USING_LAST_VALID_GAUGE = "gateway.snapshot.last.valid.in.use";
    public static final String TARGET_COUNT_GAUGE = "gateway.snapshot.targets";
    public static final String ROUTE_COUNT_GAUGE = "gateway.snapshot.routes";
    public static final String REFRESH_RESULT_GAUGE = "gateway.snapshot.last.refresh.result";
    public static final String FAILURE_REASON_GAUGE = "gateway.snapshot.last.failure.reason";
    private static final List<String> CHANNELS =
        List.of("OPERATIONS", "CLIENT", "PARTNER", "CHECKOUT", "CLIENT_API", "CHANNEL_CALLBACK");
    private static final List<String> RESULTS = List.of("NEVER_ATTEMPTED", "SUCCESS", "FAILURE", "SUPPRESSED");
    private static final List<String> FAILURE_REASONS =
        List.of("NONE", "EMPTY_RESPONSE", "INVALID_SNAPSHOT", "CONTROL_PLANE_UNAVAILABLE");

    public GatewaySnapshotMetrics(MeterRegistry meterRegistry, GatewaySnapshotStore snapshotStore) {
        MeterRegistry registry = Objects.requireNonNull(meterRegistry, "Gateway MeterRegistry 不可为空");
        GatewaySnapshotStore store = Objects.requireNonNull(snapshotStore, "Gateway Snapshot Store 不可为空");
        gauge(registry, VALID_GAUGE, "Whether this instance has loaded a valid snapshot", store,
            status -> status.validSnapshot() ? 1D : 0D);
        gauge(registry, LAST_SUCCESS_AGE_GAUGE,
            "Seconds since the last successful snapshot load; -1 means never loaded", store,
            status -> status.lastSuccessAgeSeconds() == null ? -1D : status.lastSuccessAgeSeconds());
        gauge(registry, CONSECUTIVE_FAILURES_GAUGE, "Consecutive snapshot refresh failures", store,
            GatewaySnapshotStatus::consecutiveFailures);
        gauge(registry, USING_LAST_VALID_GAUGE, "Whether refresh failures leave Last Valid in use", store,
            status -> status.usingLastValidSnapshot() ? 1D : 0D);
        countGauges(registry, store);
        stateGauges(registry, store);
    }

    private void countGauges(MeterRegistry registry, GatewaySnapshotStore store) {
        channelGauge(registry, TARGET_COUNT_GAUGE, "Current active Gateway targets", "ALL", store,
            status -> status.targetCount());
        channelGauge(registry, ROUTE_COUNT_GAUGE, "Current active Gateway routes", "ALL", store,
            status -> status.routeCount());
        for (String channel : CHANNELS) {
            channelGauge(registry, TARGET_COUNT_GAUGE, "Current active Gateway targets", channel, store,
                status -> status.targetCountsByChannel().getOrDefault(channel, 0));
            channelGauge(registry, ROUTE_COUNT_GAUGE, "Current active Gateway routes", channel, store,
                status -> status.routeCountsByChannel().getOrDefault(channel, 0));
        }
    }

    private void stateGauges(MeterRegistry registry, GatewaySnapshotStore store) {
        for (String result : RESULTS) {
            Gauge
                .builder(REFRESH_RESULT_GAUGE, store,
                    value -> result.equals(value.status().lastRefreshResult()) ? 1D : 0D)
                .description("One-hot last snapshot refresh result").tag("result", result).register(registry);
        }
        for (String reason : FAILURE_REASONS) {
            Gauge
                .builder(FAILURE_REASON_GAUGE, store,
                    value -> reason.equals(value.status().lastFailureReason()) ? 1D : 0D)
                .description("One-hot stable last snapshot failure reason").tag("reason", reason).register(registry);
        }
    }

    private void channelGauge(MeterRegistry registry, String name, String description, String channel,
        GatewaySnapshotStore store, ToDoubleFunction<GatewaySnapshotStatus> value) {
        Gauge.builder(name, store, source -> value.applyAsDouble(source.status())).description(description)
            .tag("channel", channel).register(registry);
    }

    private void gauge(MeterRegistry registry, String name, String description, GatewaySnapshotStore store,
        ToDoubleFunction<GatewaySnapshotStatus> value) {
        Gauge.builder(name, store, source -> value.applyAsDouble(source.status())).description(description)
            .register(registry);
    }
}
