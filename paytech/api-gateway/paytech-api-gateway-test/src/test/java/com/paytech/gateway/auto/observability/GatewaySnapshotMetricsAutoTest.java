package com.paytech.gateway.auto.observability;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import com.paytech.gateway.observability.GatewaySnapshotMetrics;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotStore;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class GatewaySnapshotMetricsAutoTest {

    @Test
    void exposesRedactedInitialAndLastValidSnapshotDiagnostics() {
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        GatewaySnapshotStore store =
            new GatewaySnapshotStore(Clock.fixed(Instant.parse("2026-09-03T10:00:00Z"), ZoneOffset.UTC));
        new GatewaySnapshotMetrics(registry, store);

        assertEquals(0D, gauge(registry, GatewaySnapshotMetrics.VALID_GAUGE));
        assertEquals(-1D, gauge(registry, GatewaySnapshotMetrics.LAST_SUCCESS_AGE_GAUGE));
        assertEquals(1D, gauge(registry, GatewaySnapshotMetrics.REFRESH_RESULT_GAUGE, "result", "NEVER_ATTEMPTED"));
        assertEquals(1D, gauge(registry, GatewaySnapshotMetrics.FAILURE_REASON_GAUGE, "reason", "NONE"));

        store.replace(new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.operationsSnapshot()));
        store.recordFailure("CONTROL_PLANE_UNAVAILABLE");

        assertEquals(1D, gauge(registry, GatewaySnapshotMetrics.VALID_GAUGE));
        assertEquals(0D, gauge(registry, GatewaySnapshotMetrics.LAST_SUCCESS_AGE_GAUGE));
        assertEquals(1D, gauge(registry, GatewaySnapshotMetrics.CONSECUTIVE_FAILURES_GAUGE));
        assertEquals(1D, gauge(registry, GatewaySnapshotMetrics.USING_LAST_VALID_GAUGE));
        assertEquals(1D, gauge(registry, GatewaySnapshotMetrics.TARGET_COUNT_GAUGE, "channel", "ALL"));
        assertEquals(1D, gauge(registry, GatewaySnapshotMetrics.TARGET_COUNT_GAUGE, "channel", "OPERATIONS"));
        assertEquals(3D, gauge(registry, GatewaySnapshotMetrics.ROUTE_COUNT_GAUGE, "channel", "ALL"));
        assertEquals(3D, gauge(registry, GatewaySnapshotMetrics.ROUTE_COUNT_GAUGE, "channel", "OPERATIONS"));
        assertEquals(1D, gauge(registry, GatewaySnapshotMetrics.REFRESH_RESULT_GAUGE, "result", "FAILURE"));
        assertEquals(1D,
            gauge(registry, GatewaySnapshotMetrics.FAILURE_REASON_GAUGE, "reason", "CONTROL_PLANE_UNAVAILABLE"));
    }

    private double gauge(SimpleMeterRegistry registry, String name, String... tags) {
        return registry.get(name).tags(tags).gauge().value();
    }
}
