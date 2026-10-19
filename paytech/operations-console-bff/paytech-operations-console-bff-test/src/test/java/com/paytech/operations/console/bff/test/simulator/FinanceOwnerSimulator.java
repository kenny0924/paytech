package com.paytech.operations.console.bff.test.simulator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.http.HttpStatus;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * 固定地址 Finance Owner（ledger / account）的 Loopback 测试端点：按“方法 + 路径”返回配置的原始响应，并记录每次调用
 * <li>未配置的请求返回 500 并计入 unexpected，由测试 AfterEach 断言为空。</li>
 * <li>只返回 Owner 合同事实；挂起响应用于驱动 BFF 读取超时，reset 时释放。</li>
 *
 * @author Claude
 */
public class FinanceOwnerSimulator implements AutoCloseable {
    /** Owner Problem 的 Request ID，浏览器响应不得出现。 */
    public static final String OWNER_REQUEST_ID = "finance-owner-request";

    /** Owner Problem 的 detail，浏览器响应不得出现。 */
    public static final String OWNER_DETAIL_CANARY = "FINANCE-OWNER-DETAIL-CANARY";

    /** 一次 Owner 调用。 */
    public record Request(String method, String path, String query, Map<String, List<String>> headers, String body) {
        /** 单值请求头；不存在为 null。 */
        public String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).findFirst()
                .map(entry -> entry.getValue().getFirst()).orElse(null);
        }
    }

    /** 配置的响应；hang 为 true 时不返回直到 reset。 */
    private record Reply(int status, String contentType, String body, Map<String, String> headers, boolean hang) {
    }

    private final String name;
    private final HttpServer server;
    private final List<Request> requests = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();
    private final Map<String, Reply> replies = new HashMap<>();
    private CountDownLatch release = new CountDownLatch(1);

    /** 启动随机 loopback 端口。 */
    protected FinanceOwnerSimulator(String name) {
        this.name = name;
        try {
            server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException exception) {
            throw new IllegalStateException("无法启动 " + name + " 本地 HTTP Simulator", exception);
        }
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
    }

    /** 测试直连地址，不含路径。 */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 配置成功 JSON 响应。 */
    public synchronized void reply(String method, String path, int status, String json) {
        reply(method, path, status, "application/json", json, Map.of());
    }

    /** 配置原始响应，用于 Problem 与畸形响应反例。 */
    public synchronized void reply(String method, String path, int status, String contentType, String body,
        Map<String, String> headers) {
        replies.put(method + " " + path, new Reply(status, contentType, body, Map.copyOf(headers), false));
    }

    /** 配置 Owner Problem 响应；detail 与 Request ID 均为不得外显的 Owner 值。 */
    public synchronized void problem(String method, String path, int status, String code) {
        String body = "{\"type\":\"about:blank\",\"title\":\"" + HttpStatus.valueOf(status).getReasonPhrase()
            + "\",\"detail\":\"" + OWNER_DETAIL_CANARY + "\",\"code\":\"" + code + "\",\"requestId\":\""
            + OWNER_REQUEST_ID + "\",\"timestamp\":\"2026-10-18T00:00:00.000000Z\"}";
        reply(method, path, status, "application/problem+json", body, Map.of("X-Request-Id", OWNER_REQUEST_ID));
    }

    /** 收到请求后不返回，直到 reset 或关闭，用于驱动读取超时。 */
    public synchronized void hang(String method, String path) {
        replies.put(method + " " + path, new Reply(200, "application/json", "{}", Map.of(), true));
    }

    /** 已记录的调用。 */
    public synchronized List<Request> requests() {
        return List.copyOf(requests);
    }

    /** 未配置的调用。 */
    public synchronized List<String> unexpectedRequests() {
        return List.copyOf(unexpected);
    }

    /** 清空配置与记录，并释放挂起的响应。 */
    public synchronized void reset() {
        release.countDown();
        release = new CountDownLatch(1);
        requests.clear();
        unexpected.clear();
        replies.clear();
    }

    @Override
    public synchronized void close() {
        release.countDown();
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Reply reply;
        CountDownLatch hangUntil;
        synchronized (this) {
            requests.add(new Request(method, path, exchange.getRequestURI().getRawQuery(),
                Map.copyOf(exchange.getRequestHeaders()), body));
            reply = replies.get(method + " " + path);
            if (reply == null) {
                unexpected.add(name + " " + method + " " + path);
                reply = new Reply(500, "text/plain", "unexpected", Map.of(), false);
            }
            hangUntil = release;
        }
        try (exchange) {
            if (reply.hang()) {
                awaitRelease(hangUntil);
                return;
            }
            byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", reply.contentType());
            reply.headers().forEach((key, value) -> exchange.getResponseHeaders().set(key, value));
            exchange.sendResponseHeaders(reply.status(), bytes.length);
            exchange.getResponseBody().write(bytes);
        }
    }

    private static void awaitRelease(CountDownLatch latch) {
        try {
            latch.await(30, TimeUnit.SECONDS);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }
}
