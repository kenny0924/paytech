package com.paytech.gateway.test.simulator.bff;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.springframework.http.HttpMethod;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** 为 BFF Simulator 保存按调用顺序消费的严格场景。 */
public final class BffScenarioStore {
    public static final String REQUEST_ID_TOKEN = "{{requestId}}";

    private final Deque<Scenario> scenarios = new ArrayDeque<>();
    private final Deque<String> failures = new ArrayDeque<>();

    public synchronized void expect(HttpMethod method, String path, Response response) {
        scenarios.addLast(new Scenario(method, path, response));
    }

    public synchronized Response consume(HttpMethod method, String path) {
        Scenario scenario = scenarios.pollFirst();
        if (scenario == null) {
            failures.add("BFF 收到未声明请求: " + method + " " + path);
            return Response.serverError();
        }
        if (scenario.method() != method || !scenario.path().equals(path)) {
            failures.add("BFF 请求不匹配, 期望 " + scenario.method() + " " + scenario.path() + ", 实际 " + method + " " + path);
            return Response.serverError();
        }
        return scenario.response();
    }

    public synchronized void reset() {
        scenarios.clear();
        failures.clear();
    }

    public synchronized void verifyComplete() {
        Assertions.assertTrue(failures.isEmpty(), () -> String.join("; ", failures));
        Assertions.assertTrue(scenarios.isEmpty(), () -> "BFF 存在未消费场景: " + List.copyOf(scenarios));
    }

    /** BFF 场景响应；支持透传、延迟、断连和请求屏障。 */
    public record Response(int status, String body, String contentType, Map<String, List<String>> headers,
        Duration delay, boolean disconnect, RequestBarrier barrier, ChunkedBody chunkedBody, WireBody wireBody) {
        public Response(int status, String body, String contentType, Map<String, List<String>> headers,
            Duration delay, boolean disconnect, RequestBarrier barrier, ChunkedBody chunkedBody) {
            this(status, body, contentType, headers, delay, disconnect, barrier, chunkedBody, null);
        }

        public Response {
            if (status < 100 || status > 599) {
                throw new IllegalArgumentException("BFF 响应状态码无效: " + status);
            }
            if (delay == null || delay.isNegative()) {
                throw new IllegalArgumentException("BFF 响应延迟不能为负数");
            }
            Map<String, List<String>> copiedHeaders = new LinkedHashMap<>();
            headers.forEach((name, values) -> copiedHeaders.put(name, List.copyOf(values)));
            headers = Map.copyOf(copiedHeaders);
        }

        public static Response json(int status, String body) {
            return new Response(status, body, "application/json", Map.of(), Duration.ZERO, false, null, null, null);
        }

        public static Response delayed(int status, String body, Duration delay) {
            return new Response(status, body, "application/json", Map.of(), delay, false, null, null, null);
        }

        public static Response chunkedUntilReleased(int status, String firstChunk, String remainingChunk,
            RequestBarrier remainingBodyBarrier) {
            return new Response(status, "", "application/json", Map.of(), Duration.ZERO, false, null,
                new ChunkedBody(firstChunk, remainingChunk, remainingBodyBarrier, false), null);
        }

        public static Response chunkedThenDisconnect(int status, String firstChunk, RequestBarrier disconnectBarrier) {
            return new Response(status, "", "application/json", Map.of(), Duration.ZERO, false, null,
                new ChunkedBody(firstChunk, null, disconnectBarrier, true), null);
        }

        public static Response immediateDisconnect() {
            return new Response(500, "", "application/json", Map.of(), Duration.ZERO, true, null, null, null);
        }

        public static Response serverError() {
            return json(500, "{\"code\":\"SIMULATOR_FAILURE\"}");
        }

        public static Response raw(int status, byte[] bytes, String... contentTypes) {
            return new Response(status, "", null, Map.of(), Duration.ZERO, false, null, null,
                new WireBody(bytes, List.of(), List.of(contentTypes), false, null));
        }

        public static Response rawChunks(int status, List<byte[]> chunks, String... contentTypes) {
            return new Response(status, "", null, Map.of(), Duration.ZERO, false, null, null,
                new WireBody(new byte[0], chunks, List.of(contentTypes), false, null));
        }

        public Response waitingFor(RequestBarrier requestBarrier) {
            return new Response(status, body, contentType, headers, delay, disconnect, requestBarrier, chunkedBody,
                wireBody);
        }

        public Response withHeader(String name, String value) {
            return withHeaders(name, value);
        }

        public Response withHeaders(String name, String... values) {
            Map<String, List<String>> merged = new LinkedHashMap<>(headers);
            merged.put(name, List.of(values));
            return new Response(status, body, contentType, merged, delay, disconnect, barrier, chunkedBody, wireBody);
        }

        public Response withContentTypes(String... values) {
            WireBody wire = wireBody == null ? WireBody.fromBody(body, values)
                : wireBody.withContentTypes(List.of(values));
            return new Response(status, body, null, headers, delay, disconnect, barrier, chunkedBody, wire);
        }

        public Response reflectingRequestId() {
            WireBody wire = wireBody == null ? WireBody.fromBody(body, contentType == null ? new String[0]
                : new String[] {contentType}) : wireBody;
            return new Response(status, body, null, headers, delay, disconnect, barrier, chunkedBody,
                wire.withRequestIdReflection());
        }

        public Response withDeclaredContentLength(long declaredContentLength) {
            WireBody wire = wireBody == null ? WireBody.fromBody(body, contentType == null ? new String[0]
                : new String[] {contentType}) : wireBody;
            return new Response(status, body, null, headers, delay, disconnect, barrier, chunkedBody,
                wire.withDeclaredContentLength(declaredContentLength));
        }

        @Override
        public String toString() {
            return "Response[status=" + status + ", body=[REDACTED], headers=" + headers.keySet()
                + ", delayed=" + !delay.isZero() + ", disconnect=" + disconnect + "]";
        }
    }

    /** Wire-level response body and metadata; byte arrays are copied at both boundaries. */
    public record WireBody(byte[] bytes, List<byte[]> chunks, List<String> contentTypes, boolean reflectRequestId,
        Long declaredContentLength) {
        public WireBody {
            bytes = bytes == null ? new byte[0] : bytes.clone();
            List<byte[]> copiedChunks = new ArrayList<>();
            if (chunks != null) {
                chunks.forEach(chunk -> copiedChunks.add(Objects.requireNonNull(chunk).clone()));
            }
            chunks = List.copyOf(copiedChunks);
            contentTypes = contentTypes == null ? List.of() : List.copyOf(contentTypes);
            if (declaredContentLength != null && declaredContentLength < 0) {
                throw new IllegalArgumentException("BFF 声明响应长度不可为负数");
            }
        }

        static WireBody fromBody(String body, String... contentTypes) {
            byte[] bytes = body == null ? new byte[0] : body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            return new WireBody(bytes, List.of(), List.of(contentTypes), false, null);
        }

        @Override
        public byte[] bytes() {
            return bytes.clone();
        }

        @Override
        public List<byte[]> chunks() {
            return chunks.stream().map(byte[]::clone).toList();
        }

        WireBody withContentTypes(List<String> values) {
            return new WireBody(bytes, chunks, values, reflectRequestId, declaredContentLength);
        }

        WireBody withRequestIdReflection() {
            return new WireBody(bytes, chunks, contentTypes, true, declaredContentLength);
        }

        WireBody withDeclaredContentLength(long value) {
            return new WireBody(bytes, chunks, contentTypes, reflectRequestId, value);
        }

        @Override
        public String toString() {
            return "WireBody[bytes=" + bytes.length + ", chunks=" + chunks.size() + ", contentTypes="
                + contentTypes.size() + ", reflectRequestId=" + reflectRequestId + "]";
        }
    }

    /** 首块立即 flush，其余响应体由测试屏障控制。 */
    public record ChunkedBody(String firstChunk, String remainingChunk, RequestBarrier remainingBodyBarrier,
        boolean disconnectAfterBarrier) {
        public ChunkedBody {
            if (firstChunk == null || firstChunk.isEmpty()) {
                throw new IllegalArgumentException("BFF 首个分块响应内容不可为空");
            }
            if (remainingBodyBarrier == null) {
                throw new IllegalArgumentException("BFF 分块响应屏障不可为空");
            }
            if (!disconnectAfterBarrier && (remainingChunk == null || remainingChunk.isEmpty())) {
                throw new IllegalArgumentException("BFF 剩余分块响应内容不可为空");
            }
            if (disconnectAfterBarrier && remainingChunk != null) {
                throw new IllegalArgumentException("BFF 断连分块场景不得声明剩余响应内容");
            }
        }

        @Override
        public String toString() {
            return "ChunkedBody[first=[REDACTED], remaining=[REDACTED], disconnect=" + disconnectAfterBarrier + "]";
        }
    }

    /** 允许测试在 BFF 接到请求后再继续响应。 */
    public static final class RequestBarrier {
        private final CountDownLatch requested = new CountDownLatch(1);
        private final Sinks.One<Void> release = Sinks.one();

        public void awaitRequest(Duration timeout) {
            try {
                Assertions.assertTrue(requested.await(timeout.toMillis(), TimeUnit.MILLISECONDS), "BFF 未在期限内收到请求");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 BFF 请求时被中断", exception);
            }
        }

        public void release() {
            release.tryEmitEmpty();
        }

        Mono<Void> awaitRelease() {
            requested.countDown();
            return release.asMono();
        }
    }

    private record Scenario(HttpMethod method, String path, Response response) {
    }
}
