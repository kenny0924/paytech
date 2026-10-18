package com.paytech.gateway.test.simulator.clientapi;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;
import reactor.netty.http.server.HttpServerRequest;
import reactor.netty.http.server.HttpServerResponse;

/** 独立随机 Loopback HTTP Owner，默认拒绝任何未声明请求。 */
public final class ClientApiOwnerSimulator implements AutoCloseable {
    private final ClientApiOwnerScenarioStore scenarios = new ClientApiOwnerScenarioStore();
    private final ClientApiOwnerRequestJournal journal = new ClientApiOwnerRequestJournal();
    private DisposableServer server;

    public synchronized void start() {
        if (server == null) {
            server = HttpServer.create().host("127.0.0.1").port(0).handle(this::handle).bindNow();
        }
    }

    public synchronized URI uri() {
        if (!isRunning()) {
            throw new IllegalStateException("Owner Simulator 尚未启动");
        }
        return URI.create("http://127.0.0.1:" + server.port());
    }

    public synchronized boolean isRunning() {
        return server != null && !server.isDisposed();
    }

    public ClientApiOwnerScenarioStore scenarios() {
        return scenarios;
    }

    public ClientApiOwnerRequestJournal journal() {
        return journal;
    }

    public void verifyComplete() {
        scenarios.verifyComplete();
    }

    public void reset() {
        scenarios.reset();
        journal.reset();
    }

    @Override
    public synchronized void close() {
        if (server != null) {
            server.disposeNow();
            server = null;
        }
        reset();
    }

    private Mono<Void> handle(HttpServerRequest request, HttpServerResponse response) {
        int queryStart = request.uri().indexOf('?');
        String rawPath = queryStart < 0 ? request.uri() : request.uri().substring(0, queryStart);
        HttpMethod method = HttpMethod.valueOf(request.method().name());
        var scenario = scenarios.consume(method, rawPath);
        if (scenario == null) {
            // 不把未批准 Path、Query、Header 或 Body 放入日志/断言；调用方当次即收到失败。
            return response.status(500).sendString(Mono.just("{\"code\":\"SIMULATOR_FAILURE\"}")).then();
        }
        return request.receive().map(buffer -> buffer.readableBytes()).reduce(0, Integer::sum).flatMap(length -> {
            HttpHeaders headers = new HttpHeaders();
            request.requestHeaders().forEach(entry -> headers.add(entry.getKey(), entry.getValue()));
            journal.record(method, rawPath, headers, length);
            if (scenario.disconnect()) {
                response.withConnection(connection -> connection.dispose());
                return Mono.empty();
            }
            String requestId = request.requestHeaders().get("X-Request-Id");
            scenario.contentTypes().forEach(value -> response.addHeader(HttpHeaders.CONTENT_TYPE, value));
            scenario.headers().forEach((name, values) -> values
                .forEach(value -> response.addHeader(name, reflect(value, requestId))));
            if (scenario.declaredContentLength() != null) {
                response.header(HttpHeaders.CONTENT_LENGTH, Long.toString(scenario.declaredContentLength()));
            }
            Mono<Void> delay = scenario.delay().isZero() ? Mono.empty() : Mono.delay(scenario.delay()).then();
            List<byte[]> chunks = scenario.chunks().isEmpty() ? List.of(scenario.body()) : scenario.chunks();
            reactor.core.publisher.Flux<byte[]> bytes = reactor.core.publisher.Flux.fromIterable(chunks);
            if (scenario.reflectRequestId()) {
                bytes = bytes.map(chunk -> reflect(chunk, requestId));
            }
            return delay.then(response.status(scenario.status()).sendByteArray(bytes).then());
        });
    }

    private byte[] reflect(byte[] bytes, String requestId) {
        byte[] marker = ClientApiOwnerScenarioStore.REQUEST_ID_TOKEN.getBytes(StandardCharsets.UTF_8);
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
        return value.replace(ClientApiOwnerScenarioStore.REQUEST_ID_TOKEN, Objects.requireNonNullElse(requestId, ""));
    }

    private int indexOf(byte[] bytes, byte[] marker) {
        for (int offset = 0; offset <= bytes.length - marker.length; offset++) {
            if (Arrays.equals(bytes, offset, offset + marker.length, marker, 0, marker.length)) {
                return offset;
            }
        }
        return -1;
    }
}
