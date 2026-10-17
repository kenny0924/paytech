package com.paytech.client.console.bff.auto;

import static org.junit.jupiter.api.Assertions.*;

import com.paytech.client.console.bff.ClientConsoleBffApplication;
import com.paytech.client.console.bff.domain.enums.ClientBffErrorCode;
import com.paytech.foundation.common.error.CommonErrorCode;
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
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * 工作区团队接口：真 BFF HTTP 链路消费 Platform 协议模拟器
 * <li>覆盖租户上下文要求、READ / MANAGE 权限拦截、分页 Query 透传、创建 201、空 Body 命令、路径与 Query 校验、团队错误码状态映射。</li>
 * <li>模拟器只返回 Platform 合同事实；成员保护、归属与邀请规则由 Platform 用例负责。</li>
 *
 * @see com.paytech.client.console.bff.ext.rest.ClientTeamController
 * @see com.paytech.platform.support.api.clientteam.ClientTeamApi
 */
@ActiveProfiles({ "auto", "onboarding-auto" })
@SpringBootTest(classes = ClientConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientTeamHttpAutoTest {

    private static final String ORIGIN = "https://client-console.local.test";
    private static final String TEAM = "/client-team/v1";
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

    /** 每例从全部授予、Owner 正常响应的默认状态开始。 */
    @BeforeEach
    void prepareTeamOwner() {
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
        var result = send("GET", TEAM + "/members", null, "ACCOUNT");

        assertEquals(403, result.statusCode(), result.body());
        assertCode(result, ClientBffErrorCode.WORKSPACE_CONTEXT_REQUIRED.getCode());
        assertTrue(CALLS.isEmpty());
    }

    /** 成员列表使用 READ 权限，透传 page/size 与分页结构。 */
    @Test
    void test_membersUseReadPermission() throws Exception {
        var result = send("GET", TEAM + "/members?page=1&size=20", null, "TENANT");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"memberId\":\"501\""), result.body());
        assertTrue(result.body().contains("\"self\":true"), result.body());
        assertTrue(result.body().contains("\"total\":1"), result.body());
        assertEquals("{\"permissionCodes\":[\"CLIENT_TEAM_READ\"]}", CALLS.getFirst().body());
        var call = only("/client-team/v1/members");
        assertEquals("GET", call.method());
        assertEquals("page=1&size=20", call.query());
        assertEquals("1", call.account());
        assertEquals("2", call.session());
    }

    /** 只有 READ 的成员：邀请列表与写操作 403，不调用团队接口。 */
    @Test
    void test_readOnlyMemberDeniedManageOperations() throws Exception {
        mode = "read-only";

        var list = send("GET", TEAM + "/invitations?status=PENDING", null, "TENANT");
        var create = send("POST", TEAM + "/invitations", invitationBody(), "TENANT");
        var remove = send("POST", TEAM + "/members/502/removal", null, "TENANT");

        for (var result : List.of(list, create, remove)) {
            assertEquals(403, result.statusCode(), result.body());
            assertCode(result, ClientAuthenticationErrorCode.CLIENT_PERMISSION_DENIED.getCode());
        }
        assertTrue(CALLS.stream().allMatch(c -> c.path().endsWith("/authorization/v1/client/authorize")));
        assertTrue(CALLS.stream().allMatch(c -> c.body().equals("{\"permissionCodes\":[\"CLIENT_TEAM_MANAGE\"]}")));
    }

    /** 邀请列表需要 MANAGE，透传 status 筛选。 */
    @Test
    void test_invitationsUseManagePermission() throws Exception {
        var result = send("GET", TEAM + "/invitations?status=EXPIRED&page=1&size=10", null, "TENANT");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"invitationId\":\"71\""), result.body());
        assertEquals("{\"permissionCodes\":[\"CLIENT_TEAM_MANAGE\"]}", CALLS.getFirst().body());
        assertEquals("status=EXPIRED&page=1&size=10", only("/client-team/v1/invitations").query());
    }

    /** 发起邀请：透传 201 与 emailDelivery，Body 原样交给 Platform，响应禁止缓存。 */
    @Test
    void test_createInvitationRelays201() throws Exception {
        String body = invitationBody();

        var result = send("POST", TEAM + "/invitations", body, "TENANT");

        assertEquals(201, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"emailDelivery\":\"SENT\""), result.body());
        assertFalse(result.body().contains("invitee@example.test"), result.body());
        assertEquals("no-store", result.headers().firstValue("Cache-Control").orElseThrow());
        var call = only("/client-team/v1/invitations");
        assertEquals("POST", call.method());
        assertEquals(body, call.body());
    }

    /** 调整角色透传 Body 与成员 ID。 */
    @Test
    void test_changeRoleRelays() throws Exception {
        String body = "{\"roleCode\":\"" + ClientTeamRole.TENANT_VIEWER.getCode() + "\"}";

        var result = send("POST", TEAM + "/members/502/role", body, "TENANT");

        assertEquals(200, result.statusCode(), result.body());
        assertTrue(result.body().contains("\"roleCode\":\"" + ClientTeamRole.TENANT_VIEWER.getCode() + "\""), result.body());
        assertEquals(body, only("/client-team/v1/members/502/role").body());
    }

    /** 重发、撤销、移除无 Body 且不要求 Content-Type；带 Body 时在调用前拒绝。 */
    @Test
    void test_emptyBodyCommands() throws Exception {
        var resend = send("POST", TEAM + "/invitations/71/resend", null, "TENANT");
        var revoke = send("POST", TEAM + "/invitations/71/revocation", null, "TENANT");
        var remove = send("POST", TEAM + "/members/502/removal", null, "TENANT");

        assertEquals(200, resend.statusCode(), resend.body());
        assertEquals(200, revoke.statusCode(), revoke.body());
        assertEquals(200, remove.statusCode(), remove.body());
        only("/client-team/v1/invitations/71/resend");
        only("/client-team/v1/invitations/71/revocation");
        only("/client-team/v1/members/502/removal");

        CALLS.clear();
        var withBody = send("POST", TEAM + "/members/502/removal", "{\"reason\":\"x\"}", "TENANT");
        assertEquals(400, withBody.statusCode(), withBody.body());
        assertTrue(CALLS.stream().noneMatch(c -> c.path().contains("/client-team/")));
    }

    /** 非规范路径 ID、未知 Query、写请求带 Query 在调用团队接口前拒绝。 */
    @Test
    void test_invalidPathAndQueryRejected() throws Exception {
        var badId = send("POST", TEAM + "/members/007/removal", null, "TENANT");
        var badQuery = send("GET", TEAM + "/members?tenantId=9", null, "TENANT");
        var statusOnMembers = send("GET", TEAM + "/members?status=PENDING", null, "TENANT");
        var queryOnCommand = send("POST", TEAM + "/invitations?page=1", invitationBody(), "TENANT");

        for (var result : List.of(badId, badQuery, statusOnMembers, queryOnCommand)) {
            assertEquals(400, result.statusCode(), result.body());
        }
        assertTrue(CALLS.stream().noneMatch(c -> c.path().contains("/client-team/")));
    }

    /** Platform 团队错误码映射为约定状态并保留 code。 */
    @Test
    void test_ownerErrorsMapped() throws Exception {
        mode = ClientTeamErrorCode.CLIENT_MEMBER_PROTECTED.getCode();
        var protectedMember = send("POST", TEAM + "/members/501/removal", null, "TENANT");
        mode = ClientTeamErrorCode.CLIENT_INVITATION_DUPLICATE.getCode();
        var duplicate = send("POST", TEAM + "/invitations", invitationBody(), "TENANT");
        mode = ClientTeamErrorCode.CLIENT_INVITATION_LIMIT_REACHED.getCode();
        var limit = send("POST", TEAM + "/invitations", invitationBody(), "TENANT");
        mode = ClientTeamErrorCode.CLIENT_MEMBER_EXISTS.getCode();
        var exists = send("POST", TEAM + "/invitations", invitationBody(), "TENANT");
        mode = ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE.getCode();
        var unavailable = send("POST", TEAM + "/invitations", invitationBody(), "TENANT");
        mode = CommonErrorCode.NOT_FOUND.getCode();
        var missing = send("POST", TEAM + "/invitations/99/revocation", null, "TENANT");

        assertStatus(protectedMember, 409, ClientTeamErrorCode.CLIENT_MEMBER_PROTECTED.getCode());
        assertStatus(duplicate, 409, ClientTeamErrorCode.CLIENT_INVITATION_DUPLICATE.getCode());
        assertStatus(limit, 409, ClientTeamErrorCode.CLIENT_INVITATION_LIMIT_REACHED.getCode());
        assertStatus(exists, 409, ClientTeamErrorCode.CLIENT_MEMBER_EXISTS.getCode());
        assertStatus(unavailable, 403, ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE.getCode());
        assertStatus(missing, 404, CommonErrorCode.NOT_FOUND.getCode());
    }

    /** 状态与错误码。 */
    private static void assertStatus(HttpResponse<String> result, int status, String code) {
        assertEquals(status, result.statusCode(), result.body());
        assertCode(result, code);
    }

    /** Problem 中的 code。 */
    private static void assertCode(HttpResponse<String> result, String code) {
        assertTrue(result.body().contains("\"code\":\"" + code + "\""), result.body());
    }

    /** 只有一次对指定 Platform 路径的调用。 */
    private static Call only(String suffix) {
        var calls = CALLS.stream().filter(c -> c.path().endsWith(suffix)).toList();
        assertEquals(1, calls.size(), "调用 " + suffix + "：" + CALLS);
        return calls.getFirst();
    }

    /** 合法邀请 Body。 */
    private static String invitationBody() {
        return "{\"email\":\"invitee@example.test\",\"roleCode\":\"" + ClientTeamRole.TENANT_DEVELOPER.getCode() + "\"}";
    }

    /** 经 Gateway 重建的身份头；租户上下文固定为成员 501 / 租户 601。 */
    private HttpResponse<String> send(String method, String path, String body, String contextType) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Origin", ORIGIN)
            .header("X-Request-Id", "browser-team-fixture")
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
            server.createContext("/", ClientTeamHttpAutoTest::respond);
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Platform 合同事实；mode 为错误码时团队接口返回对应 Problem。 */
    private static void respond(HttpExchange exchange) throws java.io.IOException {
        var h = exchange.getRequestHeaders();
        String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
        CALLS.add(new Call(method, path, exchange.getRequestURI().getRawQuery(),
            new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8),
            h.getFirst("X-Gateway-Actor-Subject"), h.getFirst("X-Gateway-Session-Id")));
        if (path.endsWith("/authorization/v1/client/authorize")) {
            write(exchange, 200, mode.equals("read-only") ? "{\"granted\":[\"CLIENT_TEAM_READ\"]}"
                : "{\"granted\":[\"CLIENT_TEAM_READ\",\"CLIENT_TEAM_MANAGE\"]}");
            return;
        }
        if (!mode.equals("ok") && !mode.equals("read-only")) {
            int status = mode.equals(CommonErrorCode.NOT_FOUND.getCode()) ? 404
                : mode.equals(ClientAuthenticationErrorCode.CLIENT_WORKSPACE_UNAVAILABLE.getCode()) ? 403 : 409;
            problem(exchange, status, mode);
            return;
        }
        if (path.endsWith("/client-team/v1/members") && method.equals("GET")) {
            write(exchange, 200, "{\"items\":[" + member("501", ClientTeamRole.TENANT_OWNER, true) + "],\"page\":1,\"size\":20,\"total\":1}");
        } else if (path.endsWith("/client-team/v1/invitations") && method.equals("GET")) {
            write(exchange, 200, "{\"items\":[" + invitation(null) + "],\"page\":1,\"size\":10,\"total\":1}");
        } else if (path.endsWith("/client-team/v1/invitations")) {
            write(exchange, 201, invitation("SENT"));
        } else if (path.endsWith("/resend")) {
            write(exchange, 200, invitation("SENT"));
        } else if (path.endsWith("/revocation")) {
            write(exchange, 200, invitation(null));
        } else if (path.endsWith("/role")) {
            write(exchange, 200, member("502", ClientTeamRole.TENANT_VIEWER, false));
        } else if (path.endsWith("/removal")) {
            write(exchange, 200, member("502", ClientTeamRole.TENANT_DEVELOPER, false));
        } else {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        }
    }

    /** 成员摘要。 */
    private static String member(String memberId, ClientTeamRole role, boolean self) {
        return "{\"memberId\":\"" + memberId + "\",\"emailMasked\":\"a***@example.test\",\"memberType\":\"" + role.getMemberType()
            + "\",\"roleCode\":\"" + role.getCode() + "\",\"status\":\"ACTIVE\",\"joinedAt\":\"2026-10-16T00:00:00.000000Z\",\"self\":"
            + self + "}";
    }

    /** 邀请摘要；emailDelivery 只在发起与重发时出现。 */
    private static String invitation(String emailDelivery) {
        return "{\"invitationId\":\"71\",\"emailMasked\":\"i***@example.test\",\"roleCode\":\""
            + ClientTeamRole.TENANT_DEVELOPER.getCode() + "\",\"status\":\"PENDING\",\"expiresAt\":\"2026-10-24T00:00:00.000000Z\","
            + "\"invitedByEmailMasked\":\"a***@example.test\",\"createdAt\":\"2026-10-17T00:00:00.000000Z\",\"emailDelivery\":"
            + (emailDelivery == null ? "null" : "\"" + emailDelivery + "\"") + "}";
    }

    /** 严格 Problem 协议。 */
    private static void problem(HttpExchange exchange, int status, String code) throws java.io.IOException {
        exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
        exchange.getResponseHeaders().set("Content-Type", "application/problem+json");
        String title = org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase();
        var bytes = ("{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"detail\":\"fixture\",\"code\":\"" + code
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
