package com.paytech.gateway.test.simulator.jwks;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Assertions;
import org.springframework.http.HttpMethod;

import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;

/** 保存按调用顺序消费的严格 JWKS HTTP 场景。 */
public final class JwksScenarioStore {
    private final Deque<Scenario> scenarios = new ArrayDeque<>();
    private final Deque<String> failures = new ArrayDeque<>();
    private Response defaultResponse;

    public synchronized void expectJwks(String body) {
        expect(Response.standard(200, body));
    }

    public synchronized void expectCustomerJwks(String body) {
        scenarios.addLast(new Scenario(HttpMethod.GET, JwksSimulator.CUSTOMER_JWKS_PATH, Response.standard(200, body)));
    }

    public synchronized void expectStatus(int status) {
        expect(Response.standard(status, "{\"error\":\"JWKS_UNAVAILABLE\"}"));
    }

    public synchronized void expectEmpty(int status) {
        expect(Response.standard(status, ""));
    }

    public synchronized void expectDisconnect() {
        expect(Response.disconnectResponse());
    }

    public synchronized void expectRedirect(String location) {
        expect(Response.redirect(location));
    }

    public synchronized RequestBarrier expectJwksBehindBarrier(String body) {
        return expectBehindBarrier(200, body);
    }

    public synchronized RequestBarrier expectStatusBehindBarrier(int status) {
        return expectBehindBarrier(status, "{\"error\":\"JWKS_UNAVAILABLE\"}");
    }

    public synchronized ChunkBarrier expectChunkedJwks(String firstChunk, String remainingBody) {
        ChunkBarrier barrier = new ChunkBarrier();
        expect(Response.chunked(firstChunk, remainingBody, barrier));
        return barrier;
    }

    private RequestBarrier expectBehindBarrier(int status, String body) {
        RequestBarrier barrier = new RequestBarrier();
        expect(Response.waiting(status, body, barrier));
        return barrier;
    }

    public synchronized void respondWithStatus(int status) {
        defaultResponse = Response.standard(status, "{\"error\":\"JWKS_UNAVAILABLE\"}");
    }

    public synchronized void respondWithJwks(String body) {
        defaultResponse = Response.standard(200, body);
    }

    private void expect(Response response) {
        scenarios.addLast(new Scenario(HttpMethod.GET, JwksSimulator.JWKS_PATH, response));
    }

    public synchronized Response consume(HttpMethod method, String path) {
        Scenario scenario = scenarios.pollFirst();
        if (scenario == null) {
            if (defaultResponse != null) {
                return defaultResponse;
            }
            failures.add("JWKS 收到未声明请求: " + method + " " + path);
            return Response.serverError();
        }
        if (scenario.method() != method || !scenario.path().equals(path)) {
            failures.add("JWKS 请求不匹配, 期望 " + scenario.method() + " " + scenario.path() + ", 实际 " + method + " " + path);
            return Response.serverError();
        }
        return scenario.response();
    }

    public synchronized void reset() {
        scenarios.clear();
        failures.clear();
        defaultResponse = null;
    }

    public synchronized void verifyComplete() {
        Assertions.assertTrue(failures.isEmpty(), () -> String.join("; ", failures));
        Assertions.assertTrue(scenarios.isEmpty(), () -> "JWKS 存在未消费场景: " + List.copyOf(scenarios));
    }

    /** JWKS 场景响应。 */
    public record Response(int status, String body, boolean disconnect, RequestBarrier barrier, String location,
        String remainingBody, ChunkBarrier chunkBarrier) {
        public Response {
            if (status < 100 || status > 599) {
                throw new IllegalArgumentException("JWKS 响应状态码无效: " + status);
            }
        }

        static Response standard(int status, String body) {
            return new Response(status, body, false, null, null, null, null);
        }

        static Response waiting(int status, String body, RequestBarrier barrier) {
            return new Response(status, body, false, barrier, null, null, null);
        }

        static Response chunked(String firstChunk, String remainingBody, ChunkBarrier barrier) {
            return new Response(200, firstChunk, false, null, null, remainingBody, barrier);
        }

        static Response redirect(String location) {
            return new Response(302, "redirect", false, null, location, null, null);
        }

        static Response disconnectResponse() {
            return new Response(503, "", true, null, null, null, null);
        }

        static Response serverError() {
            return standard(500, "{\"error\":\"SIMULATOR_FAILURE\"}");
        }
    }

    /** 允许测试确认真实 HTTP 请求到达后，再显式释放响应。 */
    public static final class RequestBarrier {
        private final CountDownLatch requested = new CountDownLatch(1);
        private final Sinks.One<Void> release = Sinks.one();

        public void awaitRequest(Duration timeout) {
            try {
                Assertions.assertTrue(requested.await(timeout.toMillis(), TimeUnit.MILLISECONDS), "JWKS 未在期限内收到请求");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 JWKS 请求时被中断", exception);
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

    /** 允许测试确认首个响应分块已发出，再显式释放剩余文档。 */
    public static final class ChunkBarrier {
        private final CountDownLatch firstChunkSent = new CountDownLatch(1);
        private final Sinks.One<Void> releaseRemaining = Sinks.one();

        public void awaitFirstChunk(Duration timeout) {
            try {
                Assertions.assertTrue(firstChunkSent.await(timeout.toMillis(), TimeUnit.MILLISECONDS),
                    "JWKS 未在期限内发出首个响应分块");
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待 JWKS 首个响应分块时被中断", exception);
            }
        }

        public void releaseRemaining() {
            releaseRemaining.tryEmitEmpty();
        }

        void markFirstChunkSent() {
            firstChunkSent.countDown();
        }

        Mono<Void> awaitRemainingRelease() {
            return releaseRemaining.asMono();
        }
    }

    private record Scenario(HttpMethod method, String path, Response response) {
    }
}
