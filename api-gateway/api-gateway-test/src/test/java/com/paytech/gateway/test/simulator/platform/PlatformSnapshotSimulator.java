package com.paytech.gateway.test.simulator.platform;

import java.net.URI;
import java.time.Duration;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpHeaders;

import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import tools.jackson.databind.json.JsonMapper;

/** 以真实私有 HTTP 契约返回完整 PLT-I20 DTO 的可重置 Platform Simulator。 */
public final class PlatformSnapshotSimulator {
    private static final String SNAPSHOT_PATH = "/platform-support/gateway-config/v1/public-snapshot";
    private final Duration routeResponseTimeout;
    private final AtomicReference<GatewayPublicSnapshotResponseBody> snapshot;
    private final AtomicInteger requests = new AtomicInteger();
    private final AtomicInteger responseStatus = new AtomicInteger(200);
    private DisposableServer server;

    public PlatformSnapshotSimulator(Duration routeResponseTimeout) {
        this.routeResponseTimeout = Objects.requireNonNull(routeResponseTimeout, "Gateway Route 响应超时夹具不可为空");
        snapshot = new AtomicReference<>(GatewaySnapshotFixture.operationsSnapshot(routeResponseTimeout));
    }

    public synchronized void start() {
        if (server != null) {
            return;
        }
        server = HttpServer.create().host("127.0.0.1").port(0).handle((request, response) -> {
            requests.incrementAndGet();
            if (!"GET".equals(request.method().name()) || !SNAPSHOT_PATH.substring(1).equals(request.path())) {
                return response.status(404).send().then();
            }
            response.status(responseStatus.get()).header(HttpHeaders.CONTENT_TYPE, "application/json");
            return response.sendString(Mono.just(json(snapshot.get()))).then();
        }).bindNow();
    }

    public synchronized URI uri() {
        if (server == null) {
            throw new IllegalStateException("Platform Snapshot Simulator 尚未启动");
        }
        return URI.create("http://127.0.0.1:" + server.port());
    }

    public synchronized boolean isRunning() {
        return server != null && !server.isDisposed();
    }

    public synchronized void stop() {
        if (server != null) {
            server.disposeNow();
            server = null;
        }
    }

    public void publish(GatewayPublicSnapshotResponseBody value) {
        snapshot.set(Objects.requireNonNull(value, "Gateway Snapshot 测试值不可为空"));
    }

    public int requestCount() {
        return requests.get();
    }

    public void respondWithStatus(int status) {
        responseStatus.set(status);
    }

    public void reset() {
        publish(GatewaySnapshotFixture.operationsSnapshot(routeResponseTimeout));
        responseStatus.set(200);
        requests.set(0);
    }

    private String json(GatewayPublicSnapshotResponseBody value) {
        return JsonMapper.builder().build().writeValueAsString(value);
    }
}
