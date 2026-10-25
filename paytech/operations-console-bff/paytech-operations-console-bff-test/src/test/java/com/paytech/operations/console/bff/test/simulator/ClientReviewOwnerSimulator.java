package com.paytech.operations.console.bff.test.simulator;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executors;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

/**
 * compliance 与 client-master 的测试端点（入驻审核、主档维护、资格登记）：按“方法 + 路径”返回配置的 Owner DTO，并记录每次调用
 * <li>同一路径按 Query 区分的调用（例如按主体查询资格）可用“方法 + 路径?Query”配置，优先于只按路径的配置。</li>
 * <li>未配置的请求返回 500 并计入 unexpected，由测试 AfterEach 断言为空。</li>
 * <li>只返回 Owner 合同事实，不预组装 BFF 页面。</li>
 */
public final class ClientReviewOwnerSimulator implements AutoCloseable {
    /** 一次 Owner 调用。 */
    public record Request(String method, String path, String query, Map<String, List<String>> headers, String body) {
        /** 单值请求头；不存在为 null。 */
        public String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).findFirst()
                .map(entry -> entry.getValue().getFirst()).orElse(null);
        }
    }

    /** 配置的响应。 */
    record Reply(int status, String contentType, String body) {
    }

    private final HttpServer server;
    private final List<Request> requests = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();
    private final Map<String, Reply> replies = new HashMap<>();

    /** 启动随机 loopback 端口。 */
    public ClientReviewOwnerSimulator() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
        server.createContext("/", this::handle);
        server.start();
    }

    /** 测试直连地址。 */
    public String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /** 配置成功 JSON 响应。 */
    public synchronized void reply(String method, String path, int status, String json) {
        replies.put(method + " " + path, new Reply(status, "application/json", json));
    }

    /** 配置按精确 Query 匹配的成功 JSON 响应。 */
    public synchronized void replyQuery(String method, String path, String rawQuery, int status, String json) {
        replies.put(method + " " + path + "?" + rawQuery, new Reply(status, "application/json", json));
    }

    /** 配置按精确 Query 匹配的 Owner Problem 响应。 */
    public synchronized void problemQuery(String method, String path, String rawQuery, int status, String code) {
        problem(method, path + "?" + rawQuery, status, code);
    }

    /** 配置 Owner Problem 响应。 */
    public synchronized void problem(String method, String path, int status, String code) {
        replies.put(method + " " + path, new Reply(status, "application/problem+json",
            "{\"type\":\"about:blank\",\"title\":\"" + org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase() + "\",\"detail\":\"Owner fixture\",\"code\":\"" + code
                + "\",\"requestId\":\"owner-request\",\"timestamp\":\"2026-10-13T00:00:00.000000Z\"}"));
    }

    /** 已记录的调用。 */
    public synchronized List<Request> requests() {
        return List.copyOf(requests);
    }

    /** 未配置的调用。 */
    public synchronized List<String> unexpectedRequests() {
        return List.copyOf(unexpected);
    }

    /** 清空配置与记录。 */
    public synchronized void reset() {
        requests.clear();
        unexpected.clear();
        replies.clear();
    }

    @Override
    public void close() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Reply reply;
        synchronized (this) {
            requests.add(new Request(method, path, exchange.getRequestURI().getRawQuery(), Map.copyOf(exchange.getRequestHeaders()), body));
            String query = exchange.getRequestURI().getRawQuery();
            reply = query == null ? null : replies.get(method + " " + path + "?" + query);
            if (reply == null) {
                reply = replies.get(method + " " + path);
            }
            if (reply == null) {
                unexpected.add(method + " " + path);
            }
        }
        if (reply == null) {
            reply = new Reply(500, "text/plain", "unexpected");
        }
        byte[] bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", reply.contentType());
        exchange.getResponseHeaders().set("X-Request-Id", "owner-request");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
