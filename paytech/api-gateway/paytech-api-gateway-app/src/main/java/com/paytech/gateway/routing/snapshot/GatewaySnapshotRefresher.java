package com.paytech.gateway.routing.snapshot;

import java.util.concurrent.atomic.AtomicBoolean;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.paytech.gateway.observability.GatewayMetrics;

import reactor.core.publisher.Mono;

/** 单飞拉取、完整编译后原子替换；所有失败保留 Last Valid。 */
public final class GatewaySnapshotRefresher {
    private static final Logger LOG = LoggerFactory.getLogger(GatewaySnapshotRefresher.class);
    private final ReactiveGatewaySnapshotClient client;
    private final GatewaySnapshotCompiler compiler;
    private final GatewaySnapshotStore store;
    private final GatewayMetrics metrics;
    private final AtomicBoolean refreshing = new AtomicBoolean();

    public GatewaySnapshotRefresher(ReactiveGatewaySnapshotClient client, GatewaySnapshotCompiler compiler,
        GatewaySnapshotStore store, GatewayMetrics metrics) {
        this.client = client;
        this.compiler = compiler;
        this.store = store;
        this.metrics = metrics;
    }

    public Mono<Boolean> refresh() {
        return Mono.defer(this::refreshOnce);
    }

    private Mono<Boolean> refreshOnce() {
        if (!refreshing.compareAndSet(false, true)) {
            store.recordSuppressed();
            metrics.recordSnapshotRefreshSuppressed();
            LOG.debug("Gateway Snapshot 刷新被单飞抑制");
            return Mono.just(false);
        }
        return client.fetch().switchIfEmpty(Mono.error(new EmptySnapshotResponseException())).map(compiler::compile)
            .doOnNext(snapshot -> {
                store.replace(snapshot);
                metrics.recordSnapshotRefreshSuccess();
                LOG.info("Gateway Snapshot 刷新成功: targetCount={}, routeCount={}", snapshot.targetCount(),
                    snapshot.catalog().all().size());
            }).thenReturn(true).onErrorResume(error -> {
                String reason = failureReason(error);
                store.recordFailure(reason);
                metrics.recordSnapshotRefreshFailure();
                LOG.warn("Gateway Snapshot 刷新失败: reason={}", reason);
                return Mono.just(false);
            }).doFinally(ignored -> refreshing.set(false));
    }

    private String failureReason(Throwable error) {
        if (error instanceof EmptySnapshotResponseException) {
            return "EMPTY_RESPONSE";
        }
        if (error instanceof InvalidGatewaySnapshotException || error instanceof IllegalArgumentException) {
            return "INVALID_SNAPSHOT";
        }
        return "CONTROL_PLANE_UNAVAILABLE";
    }

    private static final class EmptySnapshotResponseException extends RuntimeException {}
}
