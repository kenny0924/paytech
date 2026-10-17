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
 * Vault 管理接口的测试端点：按“方法 + 路径”返回配置的 Vault DTO，并记录每次调用
 * <li>未配置的请求返回 500 并计入 unexpected，由测试 AfterEach 断言为空。</li>
 * <li>Problem 响应回显请求的 X-Request-Id，满足真实客户端的错误合同校验。</li>
 * <li>只记录协议头与请求体，测试值均为合成数据。</li>
 */
public final class VaultAdminSimulator implements AutoCloseable {
    /** 一次 Vault 调用。 */
    public record Request(String method, String path, String query, Map<String, List<String>> headers, String body) {
        /** 单值请求头；不存在为 null。 */
        public String header(String name) {
            return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name)).findFirst()
                .map(entry -> entry.getValue().getFirst()).orElse(null);
        }
    }

    /** 配置的响应；problemCode 非空时为 Problem。 */
    private record Reply(int status, String body, String problemCode) {
    }

    private final HttpServer server;
    private final List<Request> requests = new ArrayList<>();
    private final List<String> unexpected = new ArrayList<>();
    private final Map<String, Reply> replies = new HashMap<>();

    /** 启动随机 loopback 端口。 */
    public VaultAdminSimulator() throws IOException {
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
    public synchronized void reply(String method, String path, String json) {
        replies.put(method + " " + path, new Reply(200, json, null));
    }

    /** 配置 Vault Problem 响应。 */
    public synchronized void problem(String method, String path, int status, String code) {
        replies.put(method + " " + path, new Reply(status, null, code));
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

    /** 停止端点。 */
    @Override
    public void close() {
        server.stop(0);
    }

    /** 记录请求并返回配置响应。 */
    private void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        String path = exchange.getRequestURI().getPath();
        String requestId = exchange.getRequestHeaders().getFirst("X-Request-Id");
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        Reply reply;
        synchronized (this) {
            requests.add(new Request(method, path, exchange.getRequestURI().getRawQuery(),
                Map.copyOf(exchange.getRequestHeaders()), body));
            reply = replies.get(method + " " + path);
            if (reply == null) {
                unexpected.add(method + " " + path);
            }
        }
        if (reply == null) {
            write(exchange, 500, "text/plain", "unexpected", requestId);
        } else if (reply.problemCode() == null) {
            write(exchange, reply.status(), "application/json", reply.body(), requestId);
        } else {
            write(exchange, reply.status(), "application/problem+json", "{\"type\":\"about:blank\",\"title\":\""
                + org.springframework.http.HttpStatus.valueOf(reply.status()).getReasonPhrase()
                + "\",\"detail\":\"Vault fixture\",\"code\":\"" + reply.problemCode() + "\",\"requestId\":\"" + requestId
                + "\",\"timestamp\":\"2026-10-15T00:00:00.000000Z\"}", requestId);
        }
    }

    /** 写出响应。 */
    private static void write(HttpExchange exchange, int status, String contentType, String body, String requestId)
        throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.getResponseHeaders().set("X-Request-Id", requestId);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
