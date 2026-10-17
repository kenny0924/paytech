package com.paytech.gateway.observability;

import java.util.Objects;

import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.stereotype.Component;

import com.paytech.gateway.routing.snapshot.GatewaySnapshotStore;

/** 只有至少加载过一份完整有效 Snapshot 后才允许 Gateway 进入 Ready。 */
@Component("gatewayBootstrap")
public final class GatewayBootstrapHealthIndicator implements HealthIndicator {
    private final GatewaySnapshotStore snapshotStore;

    public GatewayBootstrapHealthIndicator(GatewaySnapshotStore snapshotStore) {
        this.snapshotStore = Objects.requireNonNull(snapshotStore, "Gateway Snapshot Store 不可为空");
    }

    @Override
    public Health health() {
        var status = snapshotStore.status();
        Health.Builder health = status.validSnapshot() ? Health.up() : Health.down();
        health.withDetail("state", status.validSnapshot() ? "loaded" : "awaiting-first-snapshot")
            .withDetail("validSnapshot", status.validSnapshot()).withDetail("targetCount", status.targetCount())
            .withDetail("routeCount", status.routeCount())
            .withDetail("targetCountsByChannel", status.targetCountsByChannel())
            .withDetail("routeCountsByChannel", status.routeCountsByChannel())
            .withDetail("consecutiveFailures", status.consecutiveFailures())
            .withDetail("lastRefreshResult", status.lastRefreshResult())
            .withDetail("lastFailureReason", status.lastFailureReason())
            .withDetail("usingLastValidSnapshot", status.usingLastValidSnapshot());
        if (status.lastSuccessAt() != null) {
            health.withDetail("lastSuccessAt", status.lastSuccessAt().toString()).withDetail("lastSuccessAgeSeconds",
                status.lastSuccessAgeSeconds());
        }
        return health.build();
    }
}
