package com.paytech.vault.client.test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpServer;

/** 真实随机端口 HTTP 故障场景及请求记录。 */
public final class VaultSimulator implements AutoCloseable {
    private static final byte[] STREAM_CHUNK = "x".repeat(8192).getBytes(StandardCharsets.UTF_8);
    private static final int STREAM_PREFIX_BYTES = 262145;
    private static final int STREAM_TAIL_BYTES = 32 * 1024 * 1024;
    private final HttpServer server;
    private final ExecutorService executor = Executors.newCachedThreadPool();
    public final List<Request> requests = new CopyOnWriteArrayList<>();
    private final BlockingQueue<Scenario> scenarios = new LinkedBlockingQueue<>();

    public VaultSimulator() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/", exchange -> {
            requests.add(new Request(exchange.getRequestMethod(), exchange.getRequestURI().toString(),
                    exchange.getRequestHeaders(), new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
            Scenario scenario = scenarios.poll();
            if (scenario == null) {
                scenario = Scenario.echoing(599, "UNPREPARED".getBytes(StandardCharsets.UTF_8), false, null, null);
            }
            try {
                await(scenario.responseGate());
                scenario.contentTypes().forEach(value -> exchange.getResponseHeaders().add("Content-Type", value));
                exchange.getResponseHeaders().set("Cache-Control", "no-store");
                if (scenario.echoRequestId()) {
                    exchange.getResponseHeaders().set("X-Request-Id",
                            exchange.getRequestHeaders().getFirst("X-Request-Id"));
                } else {
                    scenario.requestIds().forEach(value -> exchange.getResponseHeaders().add("X-Request-Id", value));
                }
                if (scenario.status() == 302) exchange.getResponseHeaders().set("Location", url() + "/redirect");
                if (scenario.streamingProbe() == null) {
                    exchange.sendResponseHeaders(scenario.status(), scenario.truncated() ? scenario.body().length + 20 : 0);
                    if (scenario.truncationProbe() != null) {
                        exchange.getResponseBody().write(scenario.body());
                        exchange.getResponseBody().flush();
                        scenario.truncationProbe().prefixWritten.countDown();
                        await(scenario.truncationProbe().truncateGate);
                    } else if (scenario.bodyGate() != null) {
                        exchange.getResponseBody().write(' ');
                        exchange.getResponseBody().flush();
                        await(scenario.bodyGate());
                        exchange.getResponseBody().write(scenario.body());
                    } else {
                        exchange.getResponseBody().write(scenario.body());
                    }
                } else {
                    exchange.sendResponseHeaders(scenario.status(), 0);
                    writeStreamingBody(exchange.getResponseBody(), scenario.streamingProbe());
                }
            } catch (InterruptedException ignored) {
                Thread.currentThread().interrupt();
            } catch (IOException aborted) {
                if (scenario.streamingProbe() == null) throw aborted;
                scenario.streamingProbe().aborted.countDown();
            } finally { exchange.close(); }
        });
        server.start();
    }

    public String url() { return "http://127.0.0.1:" + server.getAddress().getPort(); }
    public int port() { return server.getAddress().getPort(); }
    public void respond(int status, String body) {
        respond(status, body.getBytes(StandardCharsets.UTF_8), false, null);
    }
    public void respond(int status, byte[] body, boolean truncated, CountDownLatch gate) {
        scenarios.add(Scenario.echoing(status, body, truncated, gate, null));
    }
    public void respondWithBodyGate(int status, String body, CountDownLatch gate) {
        scenarios.add(Scenario.echoing(status, body.getBytes(StandardCharsets.UTF_8), false, null, gate));
    }
    public void respond(int status, String body, String contentType, List<String> requestIds) {
        respond(status, body.getBytes(StandardCharsets.UTF_8), List.of(contentType), requestIds);
    }
    public void respond(int status, byte[] body, List<String> contentTypes, List<String> requestIds) {
        scenarios.add(Scenario.explicit(status, body, contentTypes, requestIds));
    }
    public StreamingProbe respondWithStreamingTail(int status) {
        var probe = new StreamingProbe();
        scenarios.add(Scenario.streaming(status, probe));
        return probe;
    }
    public TruncationProbe respondWithTruncationAfterPrefix(int status, byte[] body) {
        var probe = new TruncationProbe();
        scenarios.add(Scenario.truncated(status, body, probe));
        return probe;
    }
    private static void await(CountDownLatch gate) throws InterruptedException {
        if (gate != null) gate.await(5, TimeUnit.SECONDS);
    }
    private static void writeStreamingBody(java.io.OutputStream output, StreamingProbe probe)
            throws IOException, InterruptedException {
        writeRepeated(output, STREAM_PREFIX_BYTES, null);
        output.flush();
        probe.prefixWritten.countDown();
        await(probe.tailGate);
        writeRepeated(output, STREAM_TAIL_BYTES, probe.tailBytesWritten);
        output.flush();
    }
    private static void writeRepeated(java.io.OutputStream output, int bytes, AtomicInteger counter)
            throws IOException {
        int remaining = bytes;
        while (remaining > 0) {
            int count = Math.min(remaining, STREAM_CHUNK.length);
            output.write(STREAM_CHUNK, 0, count);
            output.flush();
            if (counter != null) counter.addAndGet(count);
            remaining -= count;
        }
    }
    @Override public void close() { server.stop(0); executor.shutdownNow(); }
    public record Request(String method, String path, com.sun.net.httpserver.Headers headers, String body) {
        @Override public String toString() { return "Request[redacted]"; }
    }
    public static final class StreamingProbe {
        private final CountDownLatch prefixWritten = new CountDownLatch(1);
        private final CountDownLatch tailGate = new CountDownLatch(1);
        private final CountDownLatch aborted = new CountDownLatch(1);
        private final AtomicInteger tailBytesWritten = new AtomicInteger();

        private StreamingProbe() {}
        public boolean awaitPrefix() throws InterruptedException {
            return prefixWritten.await(2, TimeUnit.SECONDS);
        }
        public void releaseTail() {
            tailGate.countDown();
        }
        public boolean awaitAbort() throws InterruptedException {
            return aborted.await(2, TimeUnit.SECONDS);
        }
        public int tailBytesWritten() {
            return tailBytesWritten.get();
        }
        public int totalTailBytes() {
            return STREAM_TAIL_BYTES;
        }
    }
    public static final class TruncationProbe {
        private final CountDownLatch prefixWritten = new CountDownLatch(1);
        private final CountDownLatch truncateGate = new CountDownLatch(1);

        private TruncationProbe() {}
        public boolean awaitPrefix() throws InterruptedException {
            return prefixWritten.await(2, TimeUnit.SECONDS);
        }
        public void truncate() {
            truncateGate.countDown();
        }
    }
    private record Scenario(int status, byte[] body, List<String> contentTypes, List<String> requestIds,
            boolean echoRequestId, boolean truncated, CountDownLatch responseGate, CountDownLatch bodyGate,
            StreamingProbe streamingProbe, TruncationProbe truncationProbe) {
        private Scenario {
            body = body.clone();
            contentTypes = List.copyOf(contentTypes);
            requestIds = List.copyOf(requestIds);
        }
        private static Scenario echoing(int status, byte[] body, boolean truncated, CountDownLatch responseGate,
                CountDownLatch bodyGate) {
            return new Scenario(status, body, List.of("application/json"), List.of(), true, truncated,
                    responseGate, bodyGate, null, null);
        }
        private static Scenario explicit(int status, byte[] body, List<String> contentTypes,
                List<String> requestIds) {
            return new Scenario(status, body, contentTypes, requestIds, false, false, null, null, null, null);
        }
        private static Scenario streaming(int status, StreamingProbe probe) {
            return new Scenario(status, new byte[0], List.of("application/problem+json"), List.of(), true, false,
                    null, null, probe, null);
        }
        private static Scenario truncated(int status, byte[] body, TruncationProbe probe) {
            return new Scenario(status, body, List.of("application/json"), List.of(), true, true,
                    null, null, null, probe);
        }
    }
}
