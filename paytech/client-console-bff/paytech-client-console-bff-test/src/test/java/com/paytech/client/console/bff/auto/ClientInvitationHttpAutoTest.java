package com.paytech.client.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;

import com.paytech.client.console.bff.ClientConsoleBffApplication;
import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamErrorCode;
import com.paytech.platform.support.api.enums.ClientTeamRole;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 接受邀请接口：真 BFF HTTP 链路消费 Platform 与 CCT 协议模拟器
 * <li>覆盖公开预览不带身份、详情聚合工作区名称与 CCT 不可用降级、邮箱不一致不查 CCT、账号上下文要求、接受透传与错误映射。</li>
 * <li>模拟器只返回各 Owner 合同事实；链接校验、邮箱比较与成员创建由 Platform 用例负责。</li>
 *
 * @see com.paytech.client.console.bff.ext.rest.ClientInvitationController
 * @see com.paytech.platform.support.api.clientinvitation.ClientInvitationApi
 */
@ActiveProfiles({ "auto", "onboarding-auto" })
@SpringBootTest(classes = ClientConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientInvitationHttpAutoTest {

    private static final String ORIGIN = "https://client-console.local.test";
    private static final String INVITATIONS = "/client-invitations/v1";
    private static final String PROOF = "{\"challengeNo\":\"CH0001\",\"secret\":\"fixture-link-secret\"}";
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final HttpServer OWNER = startOwner();
    private static volatile String mode = "ok";

    private record Call(String method, String path, String query, String body, String caller, String account,
        String session) {}

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** Platform 与 CCT 只替换传输地址。 */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + OWNER.getAddress().getPort();
        registry.add("client.platform-test-base-url", () -> base + "/platform-support");
        registry.add("client.cct-test-base-url", () -> base);
        registry.add("client.risk-test-base-url", () -> base);
    }

    /** 每例从邮箱一致、CCT 可用的默认状态开始。 */
    @BeforeEach
    void prepareInvitationOwners() {
        CALLS.clear();
        mode = "ok";
    }

    @AfterAll
    static void stopOwner() {
        OWNER.stop(0);
    }

    /** 公开预览：不带任何身份头即可，不向 Platform 传身份，链接证明原样转交。 */
    @Test
    void test_publicPreviewWithoutIdentity() throws Exception {
        var result = send("POST", INVITATIONS + "/preview", PROOF, null);

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"emailMasked\":\"i***@example.test\""), result.body());
        assertTrue(result.body().contains("\"roleCode\":\"" + ClientTeamRole.TENANT_DEVELOPER.getCode() + "\""), result.body());
        assertFalse(result.body().contains("clientId"), result.body());
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        var call = only("/client-invitations/v1/preview");
        assertEquals(PROOF, call.body());
        assertNull(call.account());
        assertNull(call.session());
        assertEquals(1, CALLS.size());
    }

    /** 公开预览仍要求精确 Origin 与 JSON 内容类型，且不接受 Query。 */
    @Test
    void test_previewBoundaryChecks() throws Exception {
        var noOrigin = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + INVITATIONS + "/preview"))
            .header("Content-Type", "application/json").POST(HttpRequest.BodyPublishers.ofString(PROOF)).build(),
            HttpResponse.BodyHandlers.ofString());
        var noJson = http.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + INVITATIONS + "/preview"))
            .header("Origin", ORIGIN).header("Content-Type", "text/plain").POST(HttpRequest.BodyPublishers.ofString(PROOF)).build(),
            HttpResponse.BodyHandlers.ofString());
        var query = send("POST", INVITATIONS + "/preview?challengeNo=CH0001", PROOF, null);

        assertEquals(403, noOrigin.statusCode(), noOrigin.body());
        assertCode(noOrigin, ClientBffErrorCode.ORIGIN_REJECTED.getCode());
        assertEquals(415, noJson.statusCode(), noJson.body());
        assertEquals(400, query.statusCode(), query.body());
        assertTrue(CALLS.isEmpty());
    }

    /** 链接无效：Platform 404 CLIENT_INVITATION_INVALID 原样映射。 */
    @Test
    void test_previewInvalidLink() throws Exception {
        mode = ClientTeamErrorCode.CLIENT_INVITATION_INVALID.getCode();

        var result = send("POST", INVITATIONS + "/preview", PROOF, null);

        assertEquals(404, result.statusCode(), result.body());
        assertCode(result, ClientTeamErrorCode.CLIENT_INVITATION_INVALID.getCode());
    }

    /** 详情：邮箱一致时按 Platform 返回的 clientId 读取 CCT 摘要，补工作区名称、编号与类型。 */
    @Test
    void test_detailsAggregatesWorkspaceName() throws Exception {
        var result = send("POST", INVITATIONS + "/details", PROOF, "ACCOUNT");

        assertEquals(200, result.statusCode(), result.body());
        assertEquals("{\"emailMasked\":\"i***@example.test\",\"roleCode\":\"" + ClientTeamRole.TENANT_DEVELOPER.getCode()
            + "\",\"status\":\"PENDING\",\"expiresAt\":\"2026-10-24T00:00:00.000000Z\",\"emailMatches\":true,\"clientId\":\"20\","
            + "\"workspaceName\":\"Example Trading LLC\",\"clientNo\":\"CL-20\",\"clientType\":\"ORGANIZATION\"}", result.body());
        var details = only("/client-invitations/v1/details");
        assertEquals("1", details.account());
        assertEquals("2", details.session());
        assertEquals(PROOF, details.body());
        var summaries = only("/client-workspace/v1/client-summaries");
        assertEquals("clientId=20", summaries.query());
        assertEquals("client-console-bff", summaries.caller());
        assertEquals("1", summaries.account());
    }

    /** CCT 不可用：详情照常返回，名称、编号、类型为空。 */
    @Test
    void test_detailsDegradesWhenCctUnavailable() throws Exception {
        mode = "cct-down";

        var result = send("POST", INVITATIONS + "/details", PROOF, "ACCOUNT");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"emailMatches\":true,\"clientId\":\"20\",\"workspaceName\":null,\"clientNo\":null,"
            + "\"clientType\":null"), result.body());
        only("/client-workspace/v1/client-summaries");
    }

    /** CCT 未返回该 Client（未批准）：同样降级为无名称。 */
    @Test
    void test_detailsWithoutApprovedSummary() throws Exception {
        mode = "cct-empty";

        var result = send("POST", INVITATIONS + "/details", PROOF, "ACCOUNT");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"workspaceName\":null"), result.body());
    }

    /** 邮箱不一致：没有 clientId，不调用 CCT。 */
    @Test
    void test_detailsEmailMismatchSkipsCct() throws Exception {
        mode = "mismatch";

        var result = send("POST", INVITATIONS + "/details", PROOF, "ACCOUNT");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"emailMatches\":false,\"clientId\":null,\"workspaceName\":null"), result.body());
        assertTrue(CALLS.stream().noneMatch(c -> c.path().endsWith("/client-summaries")));
    }

    /** 租户上下文的详情与接受在调用 Owner 前 403 CLIENT_ACCOUNT_CONTEXT_REQUIRED；缺少身份 401。 */
    @Test
    void test_accountContextRequired() throws Exception {
        var details = send("POST", INVITATIONS + "/details", PROOF, "TENANT");
        var accept = send("POST", INVITATIONS + "/acceptance", PROOF, "TENANT");
        var anonymous = send("POST", INVITATIONS + "/acceptance", PROOF, null);

        assertEquals(403, details.statusCode(), details.body());
        assertCode(details, ClientBffErrorCode.ACCOUNT_CONTEXT_REQUIRED.getCode());
        assertEquals(403, accept.statusCode(), accept.body());
        assertCode(accept, ClientBffErrorCode.ACCOUNT_CONTEXT_REQUIRED.getCode());
        assertEquals(401, anonymous.statusCode(), anonymous.body());
        assertCode(anonymous, ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID.getCode());
        assertTrue(CALLS.isEmpty());
    }

    /** 接受：透传 Platform 结果与账号身份。 */
    @Test
    void test_acceptRelays() throws Exception {
        var result = send("POST", INVITATIONS + "/acceptance", PROOF, "ACCOUNT");

        assertEquals(200, result.statusCode(), result.body());
        assertEquals("{\"outcome\":\"JOINED\",\"tenantId\":\"601\",\"memberId\":\"503\",\"clientId\":\"20\"}", result.body());
        var call = only("/client-invitations/v1/acceptance");
        assertEquals(PROOF, call.body());
        assertEquals("1", call.account());
        assertEquals("2", call.session());
    }

    /** 接受的 Platform 错误码映射为约定状态并保留 code。 */
    @Test
    void test_acceptErrorsMapped() throws Exception {
        mode = ClientTeamErrorCode.CLIENT_INVITATION_CLOSED.getCode();
        var closed = send("POST", INVITATIONS + "/acceptance", PROOF, "ACCOUNT");
        mode = ClientTeamErrorCode.CLIENT_INVITATION_EMAIL_MISMATCH.getCode();
        var mismatch = send("POST", INVITATIONS + "/acceptance", PROOF, "ACCOUNT");
        mode = ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE.getCode();
        var unavailable = send("POST", INVITATIONS + "/acceptance", PROOF, "ACCOUNT");

        assertEquals(409, closed.statusCode(), closed.body());
        assertCode(closed, ClientTeamErrorCode.CLIENT_INVITATION_CLOSED.getCode());
        assertEquals(403, mismatch.statusCode(), mismatch.body());
        assertCode(mismatch, ClientTeamErrorCode.CLIENT_INVITATION_EMAIL_MISMATCH.getCode());
        assertEquals(403, unavailable.statusCode(), unavailable.body());
        assertCode(unavailable, ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE.getCode());
    }

    /** Problem 中的 code。 */
    private static void assertCode(HttpResponse<String> result, String code) {
        assertTrue(result.body().contains("\"code\":\"" + code + "\""), result.body());
    }

    /** 只有一次对指定 Owner 路径的调用。 */
    private static Call only(String suffix) {
        var calls = CALLS.stream().filter(c -> c.path().endsWith(suffix)).toList();
        assertEquals(1, calls.size(), "调用 " + suffix + "：" + CALLS);
        return calls.getFirst();
    }

    /** contextType 为 null 时不带任何 Gateway 身份头（公开路由）。 */
    private HttpResponse<String> send(String method, String path, String body, String contextType) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Origin", ORIGIN)
            .header("X-Request-Id", "browser-invitation-fixture")
            .header("Content-Type", "application/json");
        if (contextType != null) {
            builder.header("X-Gateway-Realm", "CUSTOMER").header("X-Gateway-Channel", "CLIENT")
                .header("X-Gateway-Actor-Type", "PLATFORM_USER").header("X-Gateway-Context-Type", contextType)
                .header("X-Gateway-Actor-Subject", "1").header("X-Gateway-Session-Id", "2");
        }
        if ("TENANT".equals(contextType)) {
            builder.header("X-Gateway-Tenant-Id", "601").header("X-Gateway-Tenant-Type", "CLIENT")
                .header("X-Gateway-Tenant-Member-Id", "501");
        }
        return http.send(builder.method(method, HttpRequest.BodyPublishers.ofString(body)).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /** 协议模拟器在虚拟线程上响应，记录每次调用。 */
    private static HttpServer startOwner() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", ClientInvitationHttpAutoTest::respond);
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Platform 与 CCT 合同事实；mode 为错误码时邀请接口返回对应 Problem。 */
    private static void respond(HttpExchange exchange) throws java.io.IOException {
        var h = exchange.getRequestHeaders();
        String path = exchange.getRequestURI().getPath();
        CALLS.add(new Call(exchange.getRequestMethod(), path, exchange.getRequestURI().getRawQuery(),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8), h.getFirst("X-Caller-Service"),
            h.getFirst("X-Gateway-Actor-Subject"), h.getFirst("X-Gateway-Session-Id")));
        if (path.endsWith("/client-summaries")) {
            summaries(exchange);
            return;
        }
        if (mode.equals(ClientTeamErrorCode.CLIENT_INVITATION_INVALID.getCode())) {
            problem(exchange, 404, mode);
        } else if (mode.equals(ClientTeamErrorCode.CLIENT_INVITATION_CLOSED.getCode())) {
            problem(exchange, 409, mode);
        } else if (mode.equals(ClientTeamErrorCode.CLIENT_INVITATION_EMAIL_MISMATCH.getCode())
            || mode.equals(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE.getCode())) {
            problem(exchange, 403, mode);
        } else if (path.endsWith("/client-invitations/v1/preview")) {
            write(exchange, 200, "{" + preview() + "}");
        } else if (path.endsWith("/client-invitations/v1/details")) {
            boolean matches = !mode.equals("mismatch");
            write(exchange, 200, "{" + preview() + ",\"emailMatches\":" + matches + ",\"clientId\":" + (matches ? "\"20\"" : "null") + "}");
        } else if (path.endsWith("/client-invitations/v1/acceptance")) {
            write(exchange, 200, "{\"outcome\":\"JOINED\",\"tenantId\":\"601\",\"memberId\":\"503\",\"clientId\":\"20\"}");
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }
    }

    /** CCT 已批准摘要；cct-down 返回 503，cct-empty 不返回该 Client。 */
    private static void summaries(HttpExchange exchange) throws java.io.IOException {
        if (mode.equals("cct-down")) {
            problem(exchange, 503, "ONBOARDING_DEPENDENCY_UNAVAILABLE");
        } else if (mode.equals("cct-empty")) {
            write(exchange, 200, "{\"items\":[]}");
        } else {
            write(exchange, 200, "{\"items\":[{\"clientId\":\"20\",\"clientNo\":\"CL-20\",\"clientType\":\"ORGANIZATION\","
                + "\"clientStatus\":\"ACTIVE\",\"displayName\":\"Example Trading LLC\",\"country\":\"AE\","
                + "\"businessModelCode\":\"DIGITAL_GOODS\",\"approvedAt\":\"2026-10-16T08:00:00.000000Z\"}]}");
        }
    }

    /** 预览字段。 */
    private static String preview() {
        return "\"emailMasked\":\"i***@example.test\",\"roleCode\":\"" + ClientTeamRole.TENANT_DEVELOPER.getCode()
            + "\",\"status\":\"PENDING\",\"expiresAt\":\"2026-10-24T00:00:00.000000Z\"";
    }

    /** 严格 Problem 协议。 */
    private static void problem(HttpExchange exchange, int status, String code) throws java.io.IOException {
        exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
        var bytes = ("{\"type\":\"about:blank\",\"title\":\"" + HttpStatus.valueOf(status).getReasonPhrase()
            + "\",\"detail\":\"fixture\",\"code\":\"" + code
            + "\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-10-17T00:00:00.000000Z\"}").getBytes(StandardCharsets.UTF_8);
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
