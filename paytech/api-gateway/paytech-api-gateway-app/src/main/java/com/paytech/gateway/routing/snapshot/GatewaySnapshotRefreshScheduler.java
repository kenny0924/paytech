package com.paytech.gateway.routing.snapshot;

import java.util.Objects;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/** 启动后立即并按固定间隔单飞刷新 PLT-I20 Last Valid Snapshot。 */
@Component
@ConditionalOnProperty(prefix = "gateway.platform-snapshot", name = "scheduling-enabled", havingValue = "true",
    matchIfMissing = true)
public final class GatewaySnapshotRefreshScheduler {
    private final GatewaySnapshotRefresher refresher;

    public GatewaySnapshotRefreshScheduler(GatewaySnapshotRefresher refresher) {
        this.refresher = Objects.requireNonNull(refresher, "Gateway Snapshot Refresher 不可为空");
    }

    @Scheduled(initialDelay = 0, fixedDelayString = "${gateway.platform-snapshot.refresh-interval:PT5S}")
    public Mono<Void> refresh() {
        return Mono.defer(refresher::refresh).then();
    }
}
