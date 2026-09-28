package com.paytech.foundation.httpclient.test.simulator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/** Random-port HTTP resource service with explicit protocol-failure scenarios. @author Codex */
public final class HttpServiceSimulator implements AutoCloseable {

    private final HttpServer server;
    private final AtomicInteger version = new AtomicInteger();
    private final List<String> requests = new CopyOnWriteArrayList<>();
    private final List<Map<String, List<String>>> requestHeaders = new CopyOnWriteArrayList<>();
    private final CountDownLatch releaseResponse = new CountDownLatch(1);
    private final ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
    private volatile Response response;
    private volatile boolean delayed;

    public HttpServiceSimulator() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(executor);
        server.createContext("/resource", this::handle);
        server.start();
    }

    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    public void respond(int status, String body, String contentType) {
        respond(status, body, contentType, Map.of());
    }

    public void respond(int status, String body, String contentType, Map<String, ?> headers) {
        response = new Response(status, body, contentType, headers);
    }

    public void delayResponse() {
        delayed = true;
    }

    public List<String> requests() {
        return List.copyOf(requests);
    }

    public List<Map<String, List<String>>> requestHeaders() {
        return List.copyOf(requestHeaders);
    }

    public int version() {
        return version.get();
    }

    private void handle(HttpExchange exchange) throws IOException {
        try (exchange) {
            requests.add(exchange.getRequestMethod() + " " + exchange.getRequestURI());
            var headers = new java.util.TreeMap<String, List<String>>(String.CASE_INSENSITIVE_ORDER);
            exchange.getRequestHeaders().forEach((name, values) -> headers.put(name, List.copyOf(values)));
            requestHeaders.add(java.util.Collections.unmodifiableMap(headers));
            exchange.getRequestBody().readAllBytes();
            if (delayed && !awaitResponse()) {
                return;
            }
            Response current = response;
            if (current != null) {
                send(exchange, current);
                return;
            }
            if ("POST".equals(exchange.getRequestMethod())) {
                version.incrementAndGet();
                send(exchange, new Response(204, "", "application/json", Map.of()));
                return;
            }
            send(exchange, new Response(200, "{\"version\":" + version.get() + "}", "application/json", Map.of()));
        }
    }

    private boolean awaitResponse() {
        try {
            return releaseResponse.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private void send(HttpExchange exchange, Response current) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", current.contentType());
        exchange.getResponseHeaders().set("X-Service-Version", Integer.toString(version.get()));
        current.headers().forEach((name, value) -> {
            if (value instanceof Iterable<?> values) {
                for (Object currentValue : values) {
                    exchange.getResponseHeaders().add(name, String.valueOf(currentValue));
                }
            } else {
                exchange.getResponseHeaders().set(name, String.valueOf(value));
            }
        });
        if (current.status() / 100 == 3) {
            exchange.getResponseHeaders().set("Location", baseUrl() + "/resource");
        }
        byte[] body = current.body().getBytes(StandardCharsets.UTF_8);
        boolean head = "HEAD".equals(exchange.getRequestMethod());
        exchange.sendResponseHeaders(current.status(), head || body.length == 0 ? -1 : body.length);
        if (!head && body.length != 0) {
            exchange.getResponseBody().write(body);
        }
    }

    @Override
    public void close() {
        releaseResponse.countDown();
        server.stop(0);
        executor.close();
    }

    private record Response(int status, String body, String contentType, Map<String, ?> headers) {}
}
