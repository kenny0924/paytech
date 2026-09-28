package com.paytech.gateway.routing.snapshot;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import com.paytech.gateway.routing.runtime.GatewayRuntimeSnapshot;

/** 原子保留 Last Valid Snapshot 与低基数刷新状态。 */
public final class GatewaySnapshotStore {
    private final Clock clock;
    private final AtomicReference<State> state = new AtomicReference<>(State.initial());

    public GatewaySnapshotStore() {
        this(Clock.systemUTC());
    }

    public GatewaySnapshotStore(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "Gateway Snapshot Clock 不可为空");
    }

    public Optional<GatewayRuntimeSnapshot> current() {
        return Optional.ofNullable(state.get().snapshot());
    }

    public void replace(GatewayRuntimeSnapshot value) {
        GatewayRuntimeSnapshot snapshot = Objects.requireNonNull(value, "Gateway Runtime Snapshot 不可为空");
        state.set(new State(snapshot, clock.instant(), 0, "SUCCESS", "NONE"));
    }

    public void recordFailure(String reason) {
        String failureReason = requireReason(reason);
        state.updateAndGet(previous -> new State(previous.snapshot(), previous.lastSuccessAt(),
            previous.consecutiveFailures() + 1, "FAILURE", failureReason));
    }

    public void recordSuppressed() {
        state.updateAndGet(previous -> new State(previous.snapshot(), previous.lastSuccessAt(),
            previous.consecutiveFailures(), "SUPPRESSED", previous.lastFailureReason()));
    }

    public GatewaySnapshotStatus status() {
        State current = state.get();
        GatewayRuntimeSnapshot snapshot = current.snapshot();
        Instant lastSuccessAt = current.lastSuccessAt();
        Long ageSeconds =
            lastSuccessAt == null ? null : Math.max(0, Duration.between(lastSuccessAt, clock.instant()).toSeconds());
        Map<String, Integer> counts = snapshot == null ? Map.of() : snapshot.catalog().all().stream()
            .collect(Collectors.toMap(route -> route.channel().getCode(), ignored -> 1, Integer::sum, TreeMap::new));
        return new GatewaySnapshotStatus(snapshot != null, lastSuccessAt, ageSeconds, current.consecutiveFailures(),
            current.lastRefreshResult(), current.lastFailureReason(), snapshot == null ? 0 : snapshot.targetCount(),
            snapshot == null ? 0 : snapshot.catalog().all().size(),
            snapshot == null ? Map.of() : snapshot.targetCountsByChannel(), Map.copyOf(counts),
            snapshot != null && current.consecutiveFailures() > 0);
    }

    private String requireReason(String reason) {
        if (!java.util.Set.of("EMPTY_RESPONSE", "INVALID_SNAPSHOT", "CONTROL_PLANE_UNAVAILABLE").contains(reason)) {
            throw new IllegalArgumentException("Gateway Snapshot 失败原因非法");
        }
        return reason;
    }

    private record State(GatewayRuntimeSnapshot snapshot, Instant lastSuccessAt, int consecutiveFailures,
        String lastRefreshResult, String lastFailureReason) {
        private static State initial() {
            return new State(null, null, 0, "NEVER_ATTEMPTED", "NONE");
        }
    }
}
