package com.paytech.gateway.test.simulator.jwks;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/** 在 Loopback 运行的内部 HTTP JWKS Simulator。 */
public final class JwksSimulator {
    public static final String CUSTOMER_JWKS_PATH = "/platform-support/authentication/v1/realms/CUSTOMER/jwks";
    public static final String JWKS_PATH = "/platform-support/authentication/v1/realms/OPERATIONS/jwks";

    private final JwksScenarioStore scenarioStore = new JwksScenarioStore();
    private final JwksRequestJournal requestJournal = new JwksRequestJournal();
    private final java.util.concurrent.atomic.AtomicReference<String> lastAuthorization =
        new java.util.concurrent.atomic.AtomicReference<>();
    private DisposableServer server;

    public synchronized void start() {
        if (server != null) {
            return;
        }
        server = HttpServer.create().host("127.0.0.1").port(0).handle(this::handle).bindNow();
    }

    public synchronized URI uri() {
        if (server == null) {
            throw new IllegalStateException("JWKS Simulator 尚未启动");
        }
        return URI.create("http://127.0.0.1:" + server.port());
    }

    public synchronized boolean isRunning() {
        return server != null && !server.isDisposed();
    }

    public synchronized void stop() {
        if (server == null) {
            return;
        }
        server.disposeNow();
        server = null;
        reset();
    }

    public JwksScenarioStore scenarios() {
        return scenarioStore;
    }

    public JwksRequestJournal journal() {
        return requestJournal;
    }

    /** 最近一次请求的 Authorization 原值；未携带时为空。 */
    public java.util.Optional<String> lastAuthorization() {
        return java.util.Optional.ofNullable(lastAuthorization.get());
    }

    public void reset() {
        scenarioStore.reset();
        requestJournal.reset();
        lastAuthorization.set(null);
    }

    public void verifyComplete() {
        scenarioStore.verifyComplete();
        requestJournal.verifyComplete();
    }

    private Mono<Void> handle(reactor.netty.http.server.HttpServerRequest request,
        reactor.netty.http.server.HttpServerResponse response) {
        HttpMethod method = HttpMethod.valueOf(request.method().name());
        String path = "/" + request.path();
        requestJournal.record(method, path);
        lastAuthorization.set(request.requestHeaders().get(HttpHeaders.AUTHORIZATION));
        JwksScenarioStore.Response scenario = scenarioStore.consume(method, path);
        if (scenario.disconnect()) {
            response.withConnection(connection -> connection.dispose());
            return response.send().then();
        }
        if (scenario.location() != null) {
            response.header(HttpHeaders.LOCATION, scenario.location());
        }
        Mono<Void> waitForBarrier = scenario.barrier() == null ? Mono.empty() : scenario.barrier().awaitRelease();
        response.status(scenario.status()).header(HttpHeaders.CONTENT_TYPE, "application/json");
        if (scenario.chunkBarrier() != null) {
            Flux<String> body = Flux.concat(
                Mono.just(scenario.body()).doOnNext(ignored -> scenario.chunkBarrier().markFirstChunkSent()),
                scenario.chunkBarrier().awaitRemainingRelease().thenReturn(scenario.remainingBody()));
            return response.sendString(body).then();
        }
        return waitForBarrier.then(response.sendString(Mono.just(scenario.body())).then());
    }
}
