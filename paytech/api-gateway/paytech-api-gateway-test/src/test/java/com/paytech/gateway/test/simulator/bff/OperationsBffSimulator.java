package com.paytech.gateway.test.simulator.bff;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import io.netty.channel.ChannelOption;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.ByteBufFlux;
import reactor.netty.Connection;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

/** 以 Loopback Reactor Netty 运行的严格、可重置 Operations BFF Simulator。 */
public final class OperationsBffSimulator implements AutoCloseable {
    private final BffScenarioStore scenarioStore = new BffScenarioStore();
    private final BffRequestJournal requestJournal = new BffRequestJournal();
    private final BffTransportProbe transportProbe = new BffTransportProbe();
    private DisposableServer server;

    public synchronized void start() {
        if (server != null) {
            return;
        }
        server = HttpServer.create().host("127.0.0.1").port(0).handle(this::handle).bindNow();
    }

    public synchronized URI uri() {
        if (server == null) {
            throw new IllegalStateException("BFF Simulator 尚未启动");
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

    @Override
    public void close() {
        stop();
    }

    public BffScenarioStore scenarios() {
        return scenarioStore;
    }

    public BffRequestJournal journal() {
        return requestJournal;
    }

    public BffTransportProbe transportProbe() {
        return transportProbe;
    }

    public void reset() {
        scenarioStore.reset();
        requestJournal.reset();
        transportProbe.reset();
    }

    public void verifyComplete() {
        scenarioStore.verifyComplete();
    }

    private Mono<Void> handle(reactor.netty.http.server.HttpServerRequest request,
        reactor.netty.http.server.HttpServerResponse response) {
        transportProbe.recordRequestStart();
        return ByteBufFlux
            .fromInbound(request.receive().doOnNext(buffer -> transportProbe.recordBodySignal(buffer.readableBytes())))
            .aggregate().asByteArray().defaultIfEmpty(new byte[0]).flatMap(body -> respond(request, response, body));
    }

    private Mono<Void> respond(reactor.netty.http.server.HttpServerRequest request,
        reactor.netty.http.server.HttpServerResponse response, byte[] body) {
        HttpMethod method = HttpMethod.valueOf(request.method().name());
        String path = "/" + request.path();
        requestJournal.record(method, path, requestHeaders(request), body);
        BffScenarioStore.Response scenarioResponse = scenarioStore.consume(method, path);
        if (scenarioResponse.disconnect()) {
            response.withConnection(connection -> connection.dispose());
            return response.send().then();
        }
        Mono<Void> waitForBarrier =
            scenarioResponse.barrier() == null ? Mono.empty() : scenarioResponse.barrier().awaitRelease();
        Mono<Void> waitForDelay =
            scenarioResponse.delay().isZero() ? Mono.empty() : Mono.delay(scenarioResponse.delay()).then();
        response.status(scenarioResponse.status());
        String requestId = request.requestHeaders().get("X-Request-Id");
        addContentTypes(response, scenarioResponse);
        scenarioResponse.headers().forEach((name, values) -> values
            .forEach(value -> response.addHeader(name, reflect(value, requestId))));
        if (scenarioResponse.wireBody() != null && scenarioResponse.wireBody().declaredContentLength() != null) {
            response.header(HttpHeaders.CONTENT_LENGTH,
                Long.toString(scenarioResponse.wireBody().declaredContentLength()));
        }
        if (scenarioResponse.chunkedBody() != null) {
            return waitForBarrier.then(waitForDelay)
                .then(sendChunked(response, scenarioResponse.chunkedBody(), requestId,
                    scenarioResponse.wireBody() != null && scenarioResponse.wireBody().reflectRequestId()));
        }
        if (scenarioResponse.wireBody() != null) {
            return waitForBarrier.then(waitForDelay)
                .then(sendWireBody(response, scenarioResponse.wireBody(), requestId, method));
        }
        return waitForBarrier.then(waitForDelay).then(response.sendString(Mono.just(scenarioResponse.body())).then());
    }

    private Mono<Void> sendChunked(reactor.netty.http.server.HttpServerResponse response,
        BffScenarioStore.ChunkedBody chunkedBody, String requestId, boolean reflectRequestId) {
        Mono<String> terminalChunk =
            chunkedBody.disconnectAfterBarrier() ? disconnectAfterBarrier(response, chunkedBody)
                : chunkedBody.remainingBodyBarrier().awaitRelease().thenReturn(chunkedBody.remainingChunk());
        Flux<String> chunks = Flux.concat(Mono.just(chunkedBody.firstChunk()), terminalChunk);
        if (reflectRequestId) {
            chunks = chunks.map(chunk -> reflect(chunk, requestId));
        }
        response.chunkedTransfer(true);
        return response.send(ByteBufFlux.fromString(chunks, StandardCharsets.UTF_8, response.alloc()), ignored -> true)
            .then();
    }

    private Mono<Void> sendWireBody(reactor.netty.http.server.HttpServerResponse response,
        BffScenarioStore.WireBody wireBody, String requestId, HttpMethod requestMethod) {
        List<byte[]> chunks = wireBody.chunks().isEmpty() ? List.of(wireBody.bytes()) : wireBody.chunks();
        if (wireBody.reflectRequestId()) {
            chunks = chunks.stream().map(chunk -> reflect(chunk, requestId)).toList();
        }
        long actualLength = chunks.stream().mapToLong(chunk -> chunk.length).sum();
        if (requestMethod != HttpMethod.HEAD && wireBody.declaredContentLength() != null
            && wireBody.declaredContentLength() > actualLength) {
            response.keepAlive(false);
        }
        return response.sendByteArray(Flux.fromIterable(chunks)).then();
    }

    private void addContentTypes(reactor.netty.http.server.HttpServerResponse response,
        BffScenarioStore.Response scenarioResponse) {
        if (scenarioResponse.wireBody() != null) {
            scenarioResponse.wireBody().contentTypes()
                .forEach(value -> response.addHeader(HttpHeaders.CONTENT_TYPE, value));
        } else if (scenarioResponse.contentType() != null) {
            response.header(HttpHeaders.CONTENT_TYPE, scenarioResponse.contentType());
        }
    }

    private byte[] reflect(byte[] bytes, String requestId) {
        byte[] marker = BffScenarioStore.REQUEST_ID_TOKEN.getBytes(StandardCharsets.UTF_8);
        int offset = indexOf(bytes, marker);
        if (offset < 0) {
            return bytes;
        }
        byte[] replacement = Objects.requireNonNullElse(requestId, "").getBytes(StandardCharsets.UTF_8);
        byte[] reflected = new byte[bytes.length - marker.length + replacement.length];
        System.arraycopy(bytes, 0, reflected, 0, offset);
        System.arraycopy(replacement, 0, reflected, offset, replacement.length);
        System.arraycopy(bytes, offset + marker.length, reflected, offset + replacement.length,
            bytes.length - offset - marker.length);
        return reflected;
    }

    private String reflect(String value, String requestId) {
        return value.replace(BffScenarioStore.REQUEST_ID_TOKEN, Objects.requireNonNullElse(requestId, ""));
    }

    private int indexOf(byte[] bytes, byte[] marker) {
        for (int offset = 0; offset <= bytes.length - marker.length; offset++) {
            if (Arrays.equals(bytes, offset, offset + marker.length, marker, 0, marker.length)) {
                return offset;
            }
        }
        return -1;
    }

    private Mono<String> disconnectAfterBarrier(reactor.netty.http.server.HttpServerResponse response,
        BffScenarioStore.ChunkedBody chunkedBody) {
        AtomicReference<Connection> connection = new AtomicReference<>();
        response.withConnection(connection::set);
        return chunkedBody.remainingBodyBarrier().awaitRelease().then(Mono.defer(() -> {
            Connection current = Objects.requireNonNull(connection.get(), "BFF 分块断连缺少 Server Connection");
            current.channel().config().setOption(ChannelOption.SO_LINGER, 0);
            current.dispose();
            return Mono.never();
        }));
    }

    private HttpHeaders requestHeaders(reactor.netty.http.server.HttpServerRequest request) {
        HttpHeaders headers = new HttpHeaders();
        request.requestHeaders().forEach(header -> headers.add(header.getKey(), header.getValue()));
        return HttpHeaders.readOnlyHttpHeaders(headers);
    }
}
