package com.paytech.gateway.routing.snapshot;

import java.time.Instant;
import java.util.Map;

/** Last Valid Snapshot 及最近刷新结果的低基数运行状态。 */
public record GatewaySnapshotStatus(boolean validSnapshot, Instant lastSuccessAt, Long lastSuccessAgeSeconds,
    int consecutiveFailures, String lastRefreshResult, String lastFailureReason, int targetCount, int routeCount,
    Map<String, Integer> targetCountsByChannel, Map<String, Integer> routeCountsByChannel,
    boolean usingLastValidSnapshot) {
}
