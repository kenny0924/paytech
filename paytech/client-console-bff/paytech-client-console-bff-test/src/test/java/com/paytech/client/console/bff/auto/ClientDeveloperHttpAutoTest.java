package com.paytech.client.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;

import com.paytech.client.console.bff.ClientConsoleBffApplication;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 工作区开发者接口：真 BFF HTTP 链路消费 Platform 协议模拟器
 * <li>覆盖租户上下文要求、按操作的权限判断、凭证与 Webhook 转发、一次性密钥 no-store、路径与 Query 校验、Owner 错误透传。</li>
 * <li>模拟器只返回 Platform 合同事实，租户归属与脱敏由 Platform 用例负责。</li>
 */
@ActiveProfiles({ "auto", "onboarding-auto" })
@SpringBootTest(classes = ClientConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientDeveloperHttpAutoTest {

    private static final String ORIGIN = "https://client-console.local.test";
    private static final String SECRET = "ck_0123456789abcdefghij.fixture-secret-value";
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final HttpServer OWNER = startOwner();
    private static volatile String mode = "ok";

    private record Call(String method, String path, String query, String body, String account, String session) {}

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** Platform 只替换传输地址。 */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + OWNER.getAddress().getPort();
        registry.add("client.platform-test-base-url", () -> base + "/platform-support");
        registry.add("client.cct-test-base-url", () -> base);
        registry.add("client.risk-test-base-url", () -> base);
    }

    /** 每例从全部授予的默认状态开始。 */
    @BeforeEach
    void prepareDeveloperOwner() {
        CALLS.clear();
        mode = "ok";
    }

    @AfterAll
    static void stopOwner() {
        OWNER.stop(0);
    }

    /** 账号上下文直接拒绝，不调用 Platform。 */
    @Test
    void test_accountContextRejectedBeforeOwner() throws Exception {
        var result = send("GET", "/client-developer/v1/credentials", null, "ACCOUNT");

        assertEquals(403, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"code\":\"CLIENT_WORKSPACE_CONTEXT_REQUIRED\""), result.body());
        assertTrue(CALLS.isEmpty());
    }

    /** 未授予 MANAGE 时 403，不调用凭证接口；权限判断带当前账号与会话。 */
    @Test
    void test_permissionDeniedDoesNotReachOwnerOperation() throws Exception {
        mode = "deny";

        var result = send("POST", "/client-developer/v1/credentials",
            "{\"name\":\"Server\",\"scopeCodes\":[\"orders:read\"],\"validityDays\":365}", "TENANT");

        assertEquals(403, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"code\":\"CLIENT_PERMISSION_DENIED\""), result.body());
        var authorize = CALLS.getFirst();
        assertTrue(authorize.path().endsWith("/authorization/v1/client/authorize"));
        assertEquals("{\"permissionCodes\":[\"CLIENT_API_CREDENTIAL_MANAGE\"]}", authorize.body());
        assertEquals("1", authorize.account());
        assertEquals("2", authorize.session());
        assertTrue(CALLS.stream().noneMatch(c -> c.path().contains("/client-api-credentials/")));
    }

    /** 创建凭证：透传 201 与一次性密钥，响应禁止缓存。 */
    @Test
    void test_createCredentialRelaysSecretWithNoStore() throws Exception {
        String body = "{\"name\":\"Server\",\"scopeCodes\":[\"orders:read\"],\"validityDays\":365}";

        var result = send("POST", "/client-developer/v1/credentials", body, "TENANT");

        assertEquals(201, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"secret\":\"" + SECRET + "\""), result.body());
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        assertEquals("no-cache", result.headers().firstValue("Pragma").orElseThrow());
        var call = CALLS.stream().filter(c -> c.path().endsWith("/client-api-credentials/v1/credentials")).findFirst().orElseThrow();
        assertEquals("POST", call.method());
        assertEquals(body, call.body());
        assertEquals("1", call.account());
    }

    /** 读操作使用 READ 权限并透传 Query。 */
    @Test
    void test_listCredentialsUsesReadPermission() throws Exception {
        var result = send("GET", "/client-developer/v1/credentials?status=ACTIVE", null, "TENANT");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"keyPrefix\":\"ck_0123456789abcdefghij\""), result.body());
        assertFalse(result.body().contains("fixture-secret-value"));
        assertEquals("{\"permissionCodes\":[\"CLIENT_API_CREDENTIAL_READ\"]}", CALLS.getFirst().body());
        var call = CALLS.stream().filter(c -> c.path().endsWith("/credentials")).findFirst().orElseThrow();
        assertEquals("status=ACTIVE", call.query());
    }

    /** 投递列表：Webhook READ 权限，页面 from/to 对应 Platform 创建时间区间。 */
    @Test
    void test_deliveriesMapsQueryAndPermission() throws Exception {
        var result = send("GET", "/client-developer/v1/webhook-deliveries?endpointId=70&from=2026-10-01T00:00:00Z&page=0&size=20",
            null, "TENANT");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"total\":1"), result.body());
        assertEquals("{\"permissionCodes\":[\"CLIENT_WEBHOOK_READ\"]}", CALLS.getFirst().body());
        var call = CALLS.stream().filter(c -> c.path().endsWith("/client-webhooks/v1/deliveries")).findFirst().orElseThrow();
        assertTrue(call.query().contains("createdFrom=2026-10-01T00%3A00%3A00Z"), call.query());
        assertTrue(call.query().contains("endpointId=70"), call.query());
        assertTrue(call.query().contains("size=20"), call.query());
    }

    /** 测试事件与重发无 Body；重发透传 202；带 Body 时在调用前拒绝。 */
    @Test
    void test_emptyCommandsRelayStatus() throws Exception {
        var test = send("POST", "/client-developer/v1/webhook-endpoints/70/test-events", null, "TENANT");
        var replay = send("POST", "/client-developer/v1/webhook-deliveries/80/replay", null, "TENANT");

        assertEquals(200, test.statusCode(), test.body());
        assertTrue(test.body().contains("\"result\":\"SUCCEEDED\""), test.body());
        assertEquals(202, replay.statusCode(), replay.body());
        assertTrue(replay.body().contains("\"deliveryId\":\"81\""), replay.body());
        assertEquals("{\"permissionCodes\":[\"CLIENT_WEBHOOK_MANAGE\"]}", CALLS.getFirst().body());

        CALLS.clear();
        var withBody = send("POST", "/client-developer/v1/webhook-deliveries/80/replay", "{\"reason\":\"x\"}", "TENANT");
        assertEquals(400, withBody.statusCode(), withBody.body());
        assertTrue(CALLS.stream().noneMatch(c -> c.path().endsWith("/replay")));
    }

    /** 新建端点与轮换密钥：透传 201 与一次性签名密钥。 */
    @Test
    void test_webhookSecretsRelayedOnce() throws Exception {
        var created = send("POST", "/client-developer/v1/webhook-endpoints",
            "{\"url\":\"https://hooks.example.test/a\",\"eventTypes\":[\"order.paid\"]}", "TENANT");
        var rotated = send("POST", "/client-developer/v1/webhook-endpoints/70/secret-rotation", "{\"immediate\":false}", "TENANT");

        assertEquals(201, created.statusCode(), created.body());
        assertTrue(created.body().contains("\"signingSecret\":\"whsec_fixture\""), created.body());
        assertEquals(201, rotated.statusCode(), rotated.body());
        assertTrue(rotated.body().contains("\"rotationNo\":2"), rotated.body());
        assertEquals("no-store", rotated.headers().firstValue("Cache-Control").orElseThrow());
    }

    /** 投递详情只返回客户可见字段。 */
    @Test
    void test_deliveryDetailPassesClientFields() throws Exception {
        var result = send("GET", "/client-developer/v1/webhook-deliveries/80", null, "TENANT");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"requestUrl\":\"https://hooks.example.test/a\""), result.body());
        assertFalse(result.body().contains("requestedBy"));
        assertFalse(result.body().contains("sourceSystem"));
    }

    /** Owner 的 404 原样透传（其他租户或不存在的凭证）。 */
    @Test
    void test_ownerNotFoundPassesThrough() throws Exception {
        mode = "missing";

        var result = send("GET", "/client-developer/v1/credentials/99", null, "TENANT");

        assertEquals(404, result.statusCode(), result.body());
    }

    /** 非规范路径 ID 与未知 Query 在调用业务接口前拒绝。 */
    @Test
    void test_invalidPathAndQueryRejected() throws Exception {
        var badId = send("GET", "/client-developer/v1/credentials/abc", null, "TENANT");
        var badQuery = send("GET", "/client-developer/v1/credentials?tenantId=9", null, "TENANT");

        assertEquals(400, badId.statusCode(), badId.body());
        assertEquals(400, badQuery.statusCode(), badQuery.body());
        assertTrue(CALLS.stream().noneMatch(c -> c.path().contains("/client-api-credentials/")));
    }

    /** 经 Gateway 重建的身份头；租户上下文固定为成员 501 / 租户 601。 */
    private HttpResponse<String> send(String method, String path, String body, String contextType) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Origin", ORIGIN)
            .header("X-Request-Id", "browser-developer-fixture")
            .header("X-Gateway-Realm", "CUSTOMER").header("X-Gateway-Channel", "CLIENT")
            .header("X-Gateway-Actor-Type", "PLATFORM_USER").header("X-Gateway-Context-Type", contextType)
            .header("X-Gateway-Actor-Subject", "1").header("X-Gateway-Session-Id", "2");
        if ("TENANT".equals(contextType)) {
            builder.header("X-Gateway-Tenant-Id", "601").header("X-Gateway-Tenant-Type", "CLIENT")
                .header("X-Gateway-Tenant-Member-Id", "501");
        }
        if (body != null) {
            builder.header("Content-Type", "application/json");
        }
        return http.send(builder.method(method, body == null ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(body)).build(), HttpResponse.BodyHandlers.ofString());
    }

    /** 协议模拟器在虚拟线程上响应，记录每次调用。 */
    private static HttpServer startOwner() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", ClientDeveloperHttpAutoTest::respond);
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Platform 合同事实；按 mode 切换场景。 */
    private static void respond(HttpExchange exchange) throws java.io.IOException {
        var h = exchange.getRequestHeaders();
        String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
        CALLS.add(new Call(method, path, exchange.getRequestURI().getRawQuery(),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
            h.getFirst("X-Gateway-Actor-Subject"), h.getFirst("X-Gateway-Session-Id")));
        if (path.endsWith("/authorization/v1/client/authorize")) {
            write(exchange, 200, mode.equals("deny") ? "{\"granted\":[]}" : "{\"granted\":[\"CLIENT_API_CREDENTIAL_READ\","
                + "\"CLIENT_API_CREDENTIAL_MANAGE\",\"CLIENT_WEBHOOK_READ\",\"CLIENT_WEBHOOK_MANAGE\"]}");
            return;
        }
        if (mode.equals("missing")) {
            problem(exchange, 404, "Not Found", "NOT_FOUND");
            return;
        }
        if (path.endsWith("/client-api-credentials/v1/credentials") && method.equals("POST")) {
            write(exchange, 201, "{\"credential\":" + credential() + ",\"secret\":\"" + SECRET + "\"}");
        } else if (path.endsWith("/client-api-credentials/v1/credentials")) {
            write(exchange, 200, "{\"items\":[" + credential() + "]}");
        } else if (path.endsWith("/client-webhooks/v1/endpoints") && method.equals("POST")) {
            write(exchange, 201, "{\"endpointId\":\"70\",\"version\":1,\"signingSecret\":\"whsec_fixture\"}");
        } else if (path.endsWith("/secret-rotation")) {
            write(exchange, 201, "{\"rotationNo\":2,\"signingSecret\":\"whsec_rotated\",\"previousValidUntil\":\"2026-10-16T01:00:00.000000Z\"}");
        } else if (path.endsWith("/test-events")) {
            write(exchange, 200, "{\"result\":\"SUCCEEDED\",\"httpStatus\":200,\"failureCode\":null,\"durationMs\":12}");
        } else if (path.endsWith("/replay")) {
            write(exchange, 202, "{\"deliveryId\":\"81\"}");
        } else if (path.endsWith("/client-webhooks/v1/deliveries")) {
            write(exchange, 200, "{\"items\":[{\"deliveryId\":\"80\",\"eventId\":\"evt_1\",\"eventType\":\"webhook.ping\","
                + "\"endpointId\":\"70\",\"status\":\"SUCCEEDED\",\"attemptCount\":1,\"lastAttemptTime\":null,"
                + "\"replayOfDeliveryId\":null,\"createTime\":\"2026-10-16T00:00:00.000000Z\"}],\"page\":0,\"size\":20,\"total\":1}");
        } else if (path.endsWith("/client-webhooks/v1/deliveries/80")) {
            write(exchange, 200, "{\"deliveryId\":\"80\",\"endpointId\":\"70\",\"status\":\"SUCCEEDED\",\"attemptCount\":1,"
                + "\"lastAttemptTime\":null,\"replayOfDeliveryId\":null,\"createTime\":\"2026-10-16T00:00:00.000000Z\","
                + "\"event\":{\"eventId\":\"evt_1\",\"eventType\":\"webhook.ping\",\"schemaVersion\":1,\"envelope\":{\"type\":\"webhook.ping\"}},"
                + "\"attempts\":[{\"attemptNo\":1,\"requestUrl\":\"https://hooks.example.test/a\",\"rotationNo\":1,\"status\":\"SUCCEEDED\","
                + "\"httpStatus\":200,\"failureCode\":null,\"durationMs\":12,\"startedTime\":\"2026-10-16T00:00:00.000000Z\","
                + "\"finishedTime\":\"2026-10-16T00:00:00.012000Z\"}]}");
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }
    }

    /** 凭证摘要；不含密钥。 */
    private static String credential() {
        return "{\"credentialId\":\"90\",\"name\":\"Server\",\"keyPrefix\":\"ck_0123456789abcdefghij\",\"status\":\"ACTIVE\","
            + "\"scopeCodes\":[\"orders:read\"],\"createdAt\":\"2026-10-16T00:00:00.000000Z\","
            + "\"expiresAt\":\"2027-10-16T00:00:00.000000Z\",\"rotatedFromId\":null,\"version\":1}";
    }

    /** 严格 Problem 协议。 */
    private static void problem(HttpExchange exchange, int status, String title, String code) throws java.io.IOException {
        exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
        var bytes = ("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"detail\":\"fixture\",\"code\":\"" + code
            + "\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-10-16T00:00:00.000000Z\"}").getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }

    /** 按实际长度写 JSON。 */
    private static void write(HttpExchange exchange, int status, String body) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", "application/json");
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
