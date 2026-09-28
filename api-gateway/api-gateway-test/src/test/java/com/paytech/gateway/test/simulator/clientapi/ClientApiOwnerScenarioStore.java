package com.paytech.gateway.test.simulator.clientapi;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Assertions;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;

/** 按顺序消费的最小 Owner 协议场景，错误从不包含收到的请求数据。 */
public final class ClientApiOwnerScenarioStore {
    public static final String REQUEST_ID_TOKEN = "{{requestId}}";

    private final Deque<Scenario> scenarios = new ArrayDeque<>();
    private int unexpectedCalls;

    public synchronized void expect(HttpMethod method, String rawPath, Outcome outcome) {
        scenarios.addLast(new Scenario(Objects.requireNonNull(method), Objects.requireNonNull(rawPath),
            Response.from(Objects.requireNonNull(outcome))));
    }

    public synchronized void expect(HttpMethod method, String rawPath, Response response) {
        scenarios.addLast(new Scenario(Objects.requireNonNull(method), Objects.requireNonNull(rawPath),
            Objects.requireNonNull(response)));
    }

    synchronized Response consume(HttpMethod method, String rawPath) {
        Scenario expected = scenarios.peekFirst();
        if (expected == null || !expected.method().equals(method) || !expected.rawPath().equals(rawPath)) {
            unexpectedCalls++;
            return null;
        }
        scenarios.removeFirst();
        return expected.response();
    }

    public synchronized void verifyComplete() {
        Assertions.assertEquals(0, unexpectedCalls, "Owner 收到未声明或不匹配的请求");
        Assertions.assertEquals(0, scenarios.size(), "Owner 存在未消费场景");
    }

    public synchronized void reset() {
        scenarios.clear();
        unexpectedCalls = 0;
    }

    public enum Outcome {
        ACCEPTED,

        TIMEOUT,

        INVALID_RESPONSE,

        DISCONNECT
    }

    /** Raw Owner response scenario. All payloads are defensively copied and omitted from toString. */
    public record Response(int status, byte[] body, List<byte[]> chunks, List<String> contentTypes,
        Map<String, List<String>> headers, Duration delay, boolean disconnect, Long declaredContentLength,
        boolean reflectRequestId) {
        public Response {
            if (status < 100 || status > 599) {
                throw new IllegalArgumentException("Owner 响应状态码无效");
            }
            body = body == null ? new byte[0] : body.clone();
            List<byte[]> copiedChunks = new ArrayList<>();
            if (chunks != null) {
                chunks.forEach(chunk -> copiedChunks.add(Objects.requireNonNull(chunk).clone()));
            }
            chunks = List.copyOf(copiedChunks);
            contentTypes = contentTypes == null ? List.of() : List.copyOf(contentTypes);
            Map<String, List<String>> copiedHeaders = new LinkedHashMap<>();
            if (headers != null) {
                headers.forEach((name, values) -> copiedHeaders.put(name, List.copyOf(values)));
            }
            headers = Map.copyOf(copiedHeaders);
            if (delay == null || delay.isNegative()) {
                throw new IllegalArgumentException("Owner 响应延迟不可为负数");
            }
            if (declaredContentLength != null && declaredContentLength < 0) {
                throw new IllegalArgumentException("Owner 声明响应长度不可为负数");
            }
        }

        static Response from(Outcome outcome) {
            Response accepted = json(200, "{\"accepted\":true}");
            return switch (outcome) {
                case ACCEPTED -> accepted;
                case TIMEOUT -> accepted.withDelay(Duration.ofSeconds(3));
                case INVALID_RESPONSE -> accepted.withHeader(HttpHeaders.SET_COOKIE, "unexpected=blocked");
                case DISCONNECT -> new Response(500, new byte[0], List.of(), List.of("application/json"), Map.of(),
                    Duration.ZERO, true, null, false);
            };
        }

        public static Response json(int status, String body) {
            return raw(status, body.getBytes(java.nio.charset.StandardCharsets.UTF_8), "application/json");
        }

        public static Response raw(int status, byte[] body, String... contentTypes) {
            return new Response(status, body, List.of(), List.of(contentTypes), Map.of(), Duration.ZERO, false, null,
                false);
        }

        public static Response rawChunks(int status, List<byte[]> chunks, String... contentTypes) {
            return new Response(status, new byte[0], chunks, List.of(contentTypes), Map.of(), Duration.ZERO, false,
                null, false);
        }

        @Override
        public byte[] body() {
            return body.clone();
        }

        @Override
        public List<byte[]> chunks() {
            return chunks.stream().map(byte[]::clone).toList();
        }

        public Response withHeader(String name, String... values) {
            Map<String, List<String>> merged = new LinkedHashMap<>(headers);
            merged.put(name, List.of(values));
            return new Response(status, body, chunks, contentTypes, merged, delay, disconnect,
                declaredContentLength, reflectRequestId);
        }

        public Response withDelay(Duration value) {
            return new Response(status, body, chunks, contentTypes, headers, value, disconnect, declaredContentLength,
                reflectRequestId);
        }

        public Response withDeclaredContentLength(long value) {
            return new Response(status, body, chunks, contentTypes, headers, delay, disconnect, value,
                reflectRequestId);
        }

        public Response reflectingRequestId() {
            return new Response(status, body, chunks, contentTypes, headers, delay, disconnect, declaredContentLength,
                true);
        }

        @Override
        public String toString() {
            return "Response[status=" + status + ", body=[REDACTED], headers=" + headers.keySet()
                + ", delayed=" + !delay.isZero() + ", disconnect=" + disconnect + "]";
        }
    }

    private record Scenario(HttpMethod method, String rawPath, Response response) {
        @Override
        public String toString() {
            return "Scenario[method=" + method + ", rawPath=[REDACTED], response=" + response + "]";
        }
    }
}
