package com.paytech.gateway.auto.routing;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.routing.snapshot.*;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Mono;

class GatewaySnapshotRefreshAutoTest {
    @Test
    void storesFirstFullyEmptySnapshotAsReady() {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        ReactiveGatewaySnapshotClient client = () -> Mono.just(new GatewayPublicSnapshotResponseBody(1, List.of(), List.of()));
        GatewaySnapshotRefresher refresher = refresher(client, store, new SimpleMeterRegistry());

        assertTrue(refresher.refresh().block());
        assertTrue(store.current().isPresent());
        assertEquals(0, store.current().orElseThrow().targetCount());
        assertEquals(0, store.current().orElseThrow().catalog().all().size());
    }

    @Test
    void fullyEmptySnapshotAtomicallyReplacesThePreviousCatalog() {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        AtomicInteger invocation = new AtomicInteger();
        ReactiveGatewaySnapshotClient client = () -> Mono.just(invocation.getAndIncrement() == 0
            ? GatewaySnapshotFixture.operationsSnapshot() : new GatewayPublicSnapshotResponseBody(1, List.of(), List.of()));
        GatewaySnapshotRefresher refresher = refresher(client, store, new SimpleMeterRegistry());

        assertTrue(refresher.refresh().block());
        assertEquals(3, store.current().orElseThrow().catalog().all().size());
        assertTrue(refresher.refresh().block());

        assertEquals(0, store.current().orElseThrow().targetCount());
        assertEquals(0, store.current().orElseThrow().catalog().all().size());
        assertEquals("SUCCESS", store.status().lastRefreshResult());
        assertEquals(0, store.status().consecutiveFailures());
    }

    @Test
    void emptySuccessfulResponseIsRecordedAsFailure() {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        GatewaySnapshotRefresher refresher = refresher(Mono::empty, store, meters);

        assertFalse(refresher.refresh().block());

        assertTrue(store.current().isEmpty());
        assertEquals("EMPTY_RESPONSE", store.status().lastFailureReason());
        assertEquals(1, store.status().consecutiveFailures());
        assertEquals(1D,
            meters.get(GatewayMetrics.SNAPSHOT_REFRESH_COUNTER).tag("outcome", "failure").counter().count());
    }

    @Test
    void invalidRefreshPreservesLastValidAndReportsItsReason() {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        AtomicInteger invocation = new AtomicInteger();
        ReactiveGatewaySnapshotClient client =
            () -> invocation.getAndIncrement() == 0 ? Mono.just(GatewaySnapshotFixture.emptySnapshot())
                : Mono.just(new GatewayPublicSnapshotResponseBody(99, List.of(), List.of()));
        GatewaySnapshotRefresher refresher = refresher(client, store, new SimpleMeterRegistry());

        assertTrue(refresher.refresh().block());
        var first = store.current().orElseThrow();
        assertFalse(refresher.refresh().block());

        assertEquals(first, store.current().orElseThrow());
        assertEquals("INVALID_SNAPSHOT", store.status().lastFailureReason());
        assertEquals(1, store.status().consecutiveFailures());
        assertTrue(store.status().usingLastValidSnapshot());
    }

    @Test
    void concurrentRefreshIsSuppressedAndObservable() {
        GatewaySnapshotStore store = new GatewaySnapshotStore();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        GatewaySnapshotRefresher refresher = refresher(Mono::never, store, meters);
        reactor.core.Disposable first = refresher.refresh().subscribe();
        try {
            assertFalse(refresher.refresh().block());
            assertEquals("SUPPRESSED", store.status().lastRefreshResult());
            assertEquals(1D,
                meters.get(GatewayMetrics.SNAPSHOT_REFRESH_COUNTER).tag("outcome", "suppressed").counter().count());
        } finally {
            first.dispose();
        }
    }

    private GatewaySnapshotRefresher refresher(ReactiveGatewaySnapshotClient client, GatewaySnapshotStore store,
        SimpleMeterRegistry meters) {
        return new GatewaySnapshotRefresher(client, new GatewaySnapshotCompiler(), store,
            new GatewayMetrics(meters));
    }
}
