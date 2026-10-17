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
 * 工作区：真 BFF HTTP 链路消费 Platform 与 CCT 协议模拟器
 * <li>覆盖开始页工作区聚合、进入工作区与返回账号的 Cookie 替换、刷新降级透传、工作区首页的上下文校验。</li>
 * <li>模拟器只返回各 Owner 合同事实，不替代 Owner 的持久化用例。</li>
 */
@ActiveProfiles({ "auto", "onboarding-auto" })
@SpringBootTest(classes = ClientConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientWorkspaceHttpAutoTest {

    private static final String ORIGIN = "https://client-console.local.test";
    private static final String OLD = "abcdefghijklmnopqrstuv.abcdefghijklmnopqrstuvwxyz0123456789ABCDEFG";
    private static final String NEW = "ABCDEFGHIJKLMNOPQRSTUV.ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789abcdefg";
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final HttpServer OWNER = startOwner();
    private static volatile String mode = "ok";

    private record Call(String method, String path, String query, String body, String account, String session) {}

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** Platform 与 CCT 只替换传输地址，不替换 Controller、Processor 或适配逻辑。 */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + OWNER.getAddress().getPort();
        registry.add("client.platform-test-base-url", () -> base + "/platform-support");
        registry.add("client.cct-test-base-url", () -> base);
        registry.add("client.risk-test-base-url", () -> base);
    }

    /** 每例从默认模拟器状态开始。 */
    @BeforeEach
    void prepareWorkspaceOwner() {
        CALLS.clear();
        mode = "ok";
    }

    @AfterAll
    static void stopOwner() {
        OWNER.stop(0);
    }

    /** 开始页：Platform 工作区与 CCT 名称合并；没有名称的项保留编号为空，受限项给出公开原因。 */
    @Test
    void test_startListsWorkspacesWithNamesAndReasons() throws Exception {
        var result = send("GET", "/client-onboarding/v1/start", null, "ACCOUNT", null);

        assertEquals(200, result.statusCode(), result.body());
        String body = result.body();
        assertTrue(body.contains("\"workspaces\":{\"status\":\"AVAILABLE\""), body);
        assertTrue(body.contains("{\"memberId\":\"501\",\"clientId\":\"20\",\"clientNo\":\"CL-20\",\"displayName\":\"Example Trading LLC\","
            + "\"clientType\":\"ORGANIZATION\",\"state\":\"ACCESSIBLE\",\"reasonText\":null,\"current\":false}"), body);
        assertTrue(body.contains("{\"memberId\":\"502\",\"clientId\":\"21\",\"clientNo\":null,\"displayName\":null,"
            + "\"clientType\":null,\"state\":\"RESTRICTED\",\"reasonText\":\"工作区已暂停\",\"current\":false}"), body);
        assertTrue(body.contains("\"activeApplicationId\":\"10\""), body);
        var summaries = CALLS.stream().filter(c -> c.path().endsWith("/client-summaries")).findFirst().orElseThrow();
        assertEquals("clientId=20&clientId=21", summaries.query());
        assertEquals("1", summaries.account());
    }

    /** 租户上下文下开始页标记当前工作区。 */
    @Test
    void test_startMarksCurrentWorkspaceInTenantContext() throws Exception {
        mode = "tenant";

        var result = send("GET", "/client-onboarding/v1/start", null, "TENANT", "501");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"memberId\":\"501\",\"clientId\":\"20\""), result.body());
        assertTrue(result.body().contains("\"reasonText\":null,\"current\":true}"), result.body());
    }

    /** CCT 不可用只让工作区区域不可用，入驻申请照常返回。 */
    @Test
    void test_startKeepsApplicationsWhenSummaryUnavailable() throws Exception {
        mode = "cct-down";

        var result = send("GET", "/client-onboarding/v1/start", null, "ACCOUNT", null);

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"workspaces\":{\"status\":\"UNAVAILABLE\",\"items\":[]}"), result.body());
        assertTrue(result.body().contains("\"activeApplicationId\":\"10\""), result.body());
    }

    /** 进入工作区：Cookie 与成员 ID 交 Platform 判定，成功后替换 Cookie，浏览器响应不含 Refresh Secret。 */
    @Test
    void test_selectWorkspaceReplacesCookie() throws Exception {
        var result = send("POST", "/client-auth/v1/workspace-context", "{\"memberId\":\"501\"}", "ACCOUNT", null);

        assertEquals(200, result.statusCode(), result.body());
        String cookie = result.headers().firstValue("Set-Cookie").orElseThrow();
        assertTrue(cookie.startsWith("__Secure-client-refresh=" + NEW + ";"), cookie);
        assertTrue(cookie.contains("Path=/api/client/auth/v1"), cookie);
        assertTrue(result.body().contains("\"contextType\":\"TENANT\""), result.body());
        assertFalse(result.body().contains(NEW));
        var call = CALLS.stream().filter(c -> c.path().endsWith("/workspace-context")).findFirst().orElseThrow();
        assertEquals("{\"refreshToken\":\"" + OLD + "\",\"memberId\":\"501\"}", call.body());
        assertEquals("1", call.account());
        assertEquals("2", call.session());
    }

    /** 不可进入的工作区保持 403，不替换 Cookie。 */
    @Test
    void test_selectUnavailableWorkspaceIsForbidden() throws Exception {
        mode = "select-forbidden";

        var result = send("POST", "/client-auth/v1/workspace-context", "{\"memberId\":\"999\"}", "ACCOUNT", null);

        assertEquals(403, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"code\":\"CLIENT_WORKSPACE_UNAVAILABLE\""), result.body());
        assertTrue(result.headers().firstValue("Set-Cookie").isEmpty());
    }

    /** 非规范成员 ID 与多余字段在调用 Platform 前拒绝。 */
    @Test
    void test_selectWorkspaceRejectsInvalidInputBeforeOwner() throws Exception {
        for (String body : List.of("{\"memberId\":\"abc\"}", "{\"memberId\":\"501\",\"tenantId\":\"9\"}", "{}")) {
            var result = send("POST", "/client-auth/v1/workspace-context", body, "ACCOUNT", null);
            assertTrue(result.statusCode() == 400 || result.statusCode() == 422, body + " -> " + result.statusCode());
        }
        assertTrue(CALLS.isEmpty());
    }

    /** 返回账号：同样新建会话并替换 Cookie。 */
    @Test
    void test_accountContextReplacesCookie() throws Exception {
        mode = "tenant";

        var result = send("POST", "/client-auth/v1/account-context", "{}", "TENANT", "501");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.headers().firstValue("Set-Cookie").orElseThrow().startsWith("__Secure-client-refresh=" + NEW + ";"));
        assertTrue(result.body().contains("\"contextType\":\"ACCOUNT\""), result.body());
        var call = CALLS.stream().filter(c -> c.path().endsWith("/account-context")).findFirst().orElseThrow();
        assertEquals("{\"refreshToken\":\"" + OLD + "\"}", call.body());
    }

    /** 刷新降级为账号上下文时透传公开原因码。 */
    @Test
    void test_refreshPassesFallbackReason() throws Exception {
        mode = "fallback";

        var result = send("POST", "/client-auth/v1/refresh", "{}", null, null);

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"contextType\":\"ACCOUNT\""), result.body());
        assertTrue(result.body().contains("\"fallbackReason\":\"TENANT_SUSPENDED\""), result.body());
    }

    /** 首页：账号上下文直接拒绝，不调用任何 Owner。 */
    @Test
    void test_homeRequiresTenantContext() throws Exception {
        var result = send("GET", "/client-workspace/v1/home", null, "ACCOUNT", null);

        assertEquals(403, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"code\":\"CLIENT_WORKSPACE_CONTEXT_REQUIRED\""), result.body());
        assertTrue(CALLS.isEmpty());
    }

    /** 首页：clientId 由 Platform 成员事实解析，CCT 提供名称与已批准资料。 */
    @Test
    void test_homeShowsCurrentWorkspace() throws Exception {
        mode = "tenant";

        var result = send("GET", "/client-workspace/v1/home", null, "TENANT", "501");

        assertEquals(200, result.statusCode(), result.body());
        assertEquals("{\"workspace\":{\"clientId\":\"20\",\"clientNo\":\"CL-20\",\"displayName\":\"Example Trading LLC\","
            + "\"clientType\":\"ORGANIZATION\",\"clientStatus\":\"ACTIVE\"},\"profile\":{\"country\":\"AE\","
            + "\"businessModelCode\":\"DIGITAL_GOODS\",\"approvedAt\":\"2026-10-16T08:00:00.000000Z\"},"
            + "\"notice\":\"PAYMENTS_NOT_YET_ENABLED\",\"capabilities\":[\"API_CREDENTIAL_READ\",\"WEBHOOK_READ\",\"TEAM_READ\"]}", result.body());
        var summaries = CALLS.stream().filter(c -> c.path().endsWith("/client-summaries")).findFirst().orElseThrow();
        assertEquals("clientId=20", summaries.query());
    }

    /** 首页：Gateway 头与 Platform 实时上下文不一致（伪造租户）按会话失效处理，不读取 CCT。 */
    @Test
    void test_homeRejectsForeignTenantHeader() throws Exception {
        mode = "tenant";

        var result = send("GET", "/client-workspace/v1/home", null, "TENANT", "777");

        assertEquals(401, result.statusCode(), result.body());
        assertTrue(CALLS.stream().noneMatch(c -> c.path().endsWith("/client-summaries")));
    }

    /** 首页：当前成员的工作区已受限时返回工作区不可用。 */
    @Test
    void test_homeRestrictedWorkspaceIsForbidden() throws Exception {
        mode = "tenant-restricted";

        var result = send("GET", "/client-workspace/v1/home", null, "TENANT", "501");

        assertEquals(403, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"code\":\"CLIENT_WORKSPACE_UNAVAILABLE\""), result.body());
    }

    /** 经 Gateway 重建的身份头；contextType 为空时只发 Cookie（刷新为公开路由）。 */
    private HttpResponse<String> send(String method, String path, String body, String contextType, String member) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Origin", ORIGIN)
            .header("Cookie", "__Secure-client-refresh=" + OLD)
            .header("X-Request-Id", "browser-workspace-fixture");
        if (contextType != null) {
            builder.header("X-Gateway-Realm", "CUSTOMER").header("X-Gateway-Channel", "CLIENT")
                .header("X-Gateway-Actor-Type", "PLATFORM_USER").header("X-Gateway-Context-Type", contextType)
                .header("X-Gateway-Actor-Subject", "1").header("X-Gateway-Session-Id", "2");
        }
        if (member != null) {
            builder.header("X-Gateway-Tenant-Id", "601").header("X-Gateway-Tenant-Type", "CLIENT")
                .header("X-Gateway-Tenant-Member-Id", member);
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
            server.createContext("/", ClientWorkspaceHttpAutoTest::respond);
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Platform 与 CCT 的合同事实；按 mode 切换场景。 */
    private static void respond(HttpExchange exchange) throws java.io.IOException {
        var h = exchange.getRequestHeaders();
        String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
        CALLS.add(new Call(method, path, exchange.getRequestURI().getRawQuery(),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
            h.getFirst("X-Gateway-Actor-Subject"), h.getFirst("X-Gateway-Session-Id")));
        boolean tenant = mode.startsWith("tenant");
        String body;
        if (path.endsWith("/authentication/v1/client/context")) {
            body = context(tenant ? "TENANT" : "ACCOUNT", null);
        } else if (path.endsWith("/authentication/v1/client/workspaces")) {
            boolean restricted = mode.equals("tenant-restricted");
            body = "{\"items\":[" + workspace("501", "601", "20", restricted ? "TENANT_SUSPENDED" : null) + ","
                + workspace("502", "602", "21", "TENANT_SUSPENDED") + "]}";
        } else if (path.endsWith("/workspace-context")) {
            if (mode.equals("select-forbidden")) {
                problem(exchange, 403, "Forbidden", "CLIENT_WORKSPACE_UNAVAILABLE");
                return;
            }
            body = grant("TENANT", null);
        } else if (path.endsWith("/account-context")) {
            body = grant("ACCOUNT", null);
        } else if (path.endsWith("/authentication/v1/client/refresh")) {
            body = grant("ACCOUNT", mode.equals("fallback") ? "TENANT_SUSPENDED" : null);
        } else if (path.endsWith("/authorization/v1/client/authorize")) {
            body = "{\"granted\":[\"CLIENT_WEBHOOK_READ\",\"CLIENT_API_CREDENTIAL_READ\",\"CLIENT_TEAM_READ\"]}";
        } else if (path.endsWith("/client-summaries")) {
            if (mode.equals("cct-down")) {
                problem(exchange, 503, "Service Unavailable", "ONBOARDING_DEPENDENCY_UNAVAILABLE");
                return;
            }
            body = "{\"items\":[{\"clientId\":\"20\",\"clientNo\":\"CL-20\",\"clientType\":\"ORGANIZATION\",\"clientStatus\":\"ACTIVE\","
                + "\"displayName\":\"Example Trading LLC\",\"country\":\"AE\",\"businessModelCode\":\"DIGITAL_GOODS\","
                + "\"approvedAt\":\"2026-10-16T08:00:00.000000Z\"}]}";
        } else if (path.endsWith("/start")) {
            body = "{\"applications\":[{\"applicationId\":\"10\",\"applicationNo\":\"FIXTURE\",\"clientType\":\"ORGANIZATION\","
                + "\"status\":\"APPROVED\",\"active\":true,\"savedAt\":\"2026-09-16T00:00:00.000000Z\"}],\"hasMore\":false}";
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        write(exchange, 200, body, "application/json");
    }

    /** Platform 上下文；租户上下文固定为成员 501 / 租户 601。 */
    private static String context(String type, String fallback) {
        boolean t = type.equals("TENANT");
        return "{\"accountId\":\"1\",\"sessionId\":\"2\",\"emailMasked\":\"f***@example.test\",\"emailVerified\":true,"
            + "\"contextType\":\"" + type + "\",\"tenantId\":" + (t ? "\"601\"" : "null") + ",\"memberId\":"
            + (t ? "\"501\"" : "null") + ",\"allowedActions\":[\"LOGOUT\"],\"fallbackReason\":"
            + (fallback == null ? "null" : "\"" + fallback + "\"") + "}";
    }

    /** Platform 新会话；Refresh Secret 为新值。 */
    private static String grant(String type, String fallback) {
        return "{\"context\":" + context(type, fallback) + ",\"accessToken\":\"fixture-access\","
            + "\"expiresAt\":\"2099-01-01T00:00:00.000000Z\",\"refreshToken\":\"" + NEW + "\","
            + "\"refreshExpiresAt\":\"2099-01-08T00:00:00.000000Z\"}";
    }

    /** Platform 成员工作区。 */
    private static String workspace(String member, String tenant, String client, String reason) {
        return "{\"memberId\":\"" + member + "\",\"tenantId\":\"" + tenant + "\",\"clientId\":\"" + client
            + "\",\"ownerStatus\":\"" + (reason == null ? "ACTIVE" : "SUSPENDED") + "\",\"securityStatus\":\"ACTIVE\","
            + "\"memberStatus\":\"ACTIVE\",\"accessible\":" + (reason == null) + ",\"reasonCode\":"
            + (reason == null ? "null" : "\"" + reason + "\"") + "}";
    }

    /** 严格 Problem 协议。 */
    private static void problem(HttpExchange exchange, int status, String title, String code) throws java.io.IOException {
        exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
        write(exchange, status, "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"detail\":\"fixture\",\"code\":\"" + code
            + "\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-10-16T00:00:00.000000Z\"}", "application/problem+json");
    }

    /** 按实际长度写 JSON。 */
    private static void write(HttpExchange exchange, int status, String body, String type) throws java.io.IOException {
        var bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", type);
        exchange.sendResponseHeaders(status, bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
