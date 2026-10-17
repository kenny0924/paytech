package com.paytech.gateway.auto.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.health.contributor.Status;

import com.paytech.gateway.observability.GatewayBootstrapHealthIndicator;
import com.paytech.gateway.routing.runtime.GatewayRuntimeCatalog;
import com.paytech.gateway.routing.runtime.GatewayRuntimeSnapshot;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotStore;

class GatewaySnapshotHealthAutoTest {

    @Test
    void reportsReadinessDownUntilFirstValidSnapshot() {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        GatewayBootstrapHealthIndicator indicator = new GatewayBootstrapHealthIndicator(store);

        assertEquals(Status.DOWN, indicator.health().getStatus());
    }

    @Test
    void validEmptyCatalogIsReady() {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        GatewayBootstrapHealthIndicator indicator = new GatewayBootstrapHealthIndicator(store);
        store.replace(new GatewayRuntimeSnapshot(1, new GatewayRuntimeCatalog(List.of())));

        assertEquals(Status.UP, indicator.health().getStatus());
        assertEquals(0, indicator.health().getDetails().get("routeCount"));
        assertEquals(0, indicator.health().getDetails().get("targetCount"));
        assertEquals("SUCCESS", indicator.health().getDetails().get("lastRefreshResult"));
    }

    @Test
    void reportsLastValidRefreshFailureWithoutDroppingReadiness() {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        GatewayBootstrapHealthIndicator indicator = new GatewayBootstrapHealthIndicator(store);
        store.replace(new GatewayRuntimeSnapshot(1, Map.of("example", new com.paytech.gateway.routing.runtime.GatewayRuntimeTarget("example",
            com.paytech.platform.support.api.enums.GatewayRouteChannel.OPERATIONS, "example")), new GatewayRuntimeCatalog(List.of())));
        store.recordFailure("CONTROL_PLANE_UNAVAILABLE");

        assertEquals(Status.UP, indicator.health().getStatus());
        assertEquals(true, indicator.health().getDetails().get("usingLastValidSnapshot"));
        assertEquals(1, indicator.health().getDetails().get("consecutiveFailures"));
        assertEquals("CONTROL_PLANE_UNAVAILABLE", indicator.health().getDetails().get("lastFailureReason"));
        assertEquals(Map.of("OPERATIONS", 1), indicator.health().getDetails().get("targetCountsByChannel"));
    }
}
