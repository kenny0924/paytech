package com.paytech.client.console.bff.auto;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.Executors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.paytech.client.console.bff.ClientConsoleBffApplication;
import com.paytech.client.console.bff.integration.CallerServiceCodes;
import com.paytech.payout.api.PayoutHeaders;
import com.paytech.payout.api.enums.BeneficiaryAccountStatus;
import com.paytech.payout.api.enums.BeneficiaryActorType;
import com.paytech.payout.api.enums.BeneficiaryBlockReason;
import com.paytech.payout.api.enums.BeneficiaryCloseReason;
import com.paytech.payout.api.enums.BeneficiaryOwnerType;
import com.paytech.payout.api.enums.BeneficiaryRailType;
import com.paytech.payout.api.enums.PayoutErrorCode;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import tools.jackson.databind.json.JsonMapper;

/**
 * 工作区“资金”接口（/client-payout/v1）：真 BFF HTTP 链路消费 Platform 与 payout 测试端点
 * <li>clientId 只来自 Platform 成员事实；Owner 与操作人由 BFF 填入，浏览器提交的 Owner 不被接受。</li>
 * <li>权限：余额 CLIENT_BALANCE_READ、读取 CLIENT_PAYOUT_ACCOUNT_READ、写入 CLIENT_PAYOUT_ACCOUNT_MANAGE；无权限、账号上下文、
 * 非法 Query 都不调用 payout。</li>
 * <li>端点只返回各 Owner 的合同 DTO；未配置的请求记为意外调用，每例结束断言为空。</li>
 *
 * @see com.paytech.client.console.bff.ext.rest.ClientPayoutController
 * @author Claude
 */
@ActiveProfiles({ "auto", "onboarding-auto" })
@SpringBootTest(classes = ClientConsoleBffApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ClientPayoutHttpAutoTest {

    private static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    private static final String ORIGIN = "https://client-console.local.test";
    private static final String CLIENT_ID = "20";
    private static final String ACCOUNT_ID = "900100";
    private static final String ACCOUNTS = "/payout/v1/beneficiary-accounts";
    private static final List<Call> CALLS = new CopyOnWriteArrayList<>();
    private static final List<String> UNEXPECTED = new CopyOnWriteArrayList<>();
    private static final Map<String, Reply> REPLIES = new ConcurrentHashMap<>();
    private static final HttpServer OWNER = startOwner();
    private static volatile List<String> granted = List.of();

    private record Call(String method, String path, String query, String body, String caller) {}

    private record Reply(int status, String contentType, String body) {}

    @LocalServerPort
    int port;

    private final HttpClient http = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    /** Platform 与 payout 只替换传输地址，不替换 Controller、拦截器或代理。 */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        String base = "http://127.0.0.1:" + OWNER.getAddress().getPort();
        registry.add("client.platform-test-base-url", () -> base + "/platform-support");
        registry.add("client.cct-test-base-url", () -> base);
        registry.add("client.risk-test-base-url", () -> base);
        registry.add("client.payout-test-base-url", () -> base);
    }

    /** 每例从空 payout 配置、全部资金权限开始。 */
    @BeforeEach
    void preparePayoutOwner() {
        CALLS.clear();
        UNEXPECTED.clear();
        REPLIES.clear();
        granted = List.of("CLIENT_PAYOUT_ACCOUNT_READ", "CLIENT_PAYOUT_ACCOUNT_MANAGE", "CLIENT_BALANCE_READ");
    }

    /** 不得有未配置的 Owner 调用。 */
    @AfterEach
    void verifyNoUnexpectedCalls() {
        try {
            assertTrue(UNEXPECTED.isEmpty(), UNEXPECTED.toString());
        } finally {
            REPLIES.clear();
        }
    }

    @AfterAll
    static void stopOwner() {
        OWNER.stop(0);
    }

    /** 列表：Owner 取当前工作区 Client，Query 原样转发，声明 Client BFF 调用方。 */
    @Test
    void test_listUsesWorkspaceClient() throws Exception {
        REPLIES.put("GET " + ACCOUNTS, json(Map.of("items", List.of(account()))));

        var response = send("GET", "/client-payout/v1/beneficiary-accounts?status=" + BeneficiaryAccountStatus.VERIFIED.getCode(), null);

        assertEquals(200, response.statusCode(), response.body());
        var call = payoutCall();
        assertEquals("ownerType=CLIENT&ownerId=" + CLIENT_ID + "&status=VERIFIED", call.query());
        assertEquals(CallerServiceCodes.CLIENT_CONSOLE_BFF, call.caller());
        assertEquals(ACCOUNT_ID, JSON.readTree(response.body()).path("items").get(0).path("beneficiaryAccountId").asString());
    }

    /** 提交：Owner、提交人由 BFF 填入；浏览器多给 Owner 字段被拒且不调用 payout；成功保持 201。 */
    @Test
    void test_submitFillsOwnerAndMember() throws Exception {
        REPLIES.put("POST " + ACCOUNTS, new Reply(201, "application/json", JSON.writeValueAsString(account())));
        var body = new LinkedHashMap<String, Object>(Map.of("railType", BeneficiaryRailType.LOCAL.getCode(), "bankCountry", "AE",
            "currency", "USD", "fields", Map.of("holderName", "Example Trading LLC", "iban", "AE070331234567890123456",
                "bankName", "Emirates NBD")));

        var created = send("POST", "/client-payout/v1/beneficiary-accounts", JSON.writeValueAsString(body));

        assertEquals(201, created.statusCode(), created.body());
        var forwarded = JSON.readTree(payoutCall().body());
        assertAll(() -> assertEquals(BeneficiaryOwnerType.CLIENT.getCode(), forwarded.path("ownerType").asString()),
            () -> assertEquals(CLIENT_ID, forwarded.path("ownerId").asString()),
            () -> assertEquals(BeneficiaryActorType.CLIENT_MEMBER.getCode(), forwarded.path("actorType").asString()),
            () -> assertEquals("1", forwarded.path("actorId").asString()));

        CALLS.clear();
        body.put("ownerId", "21");
        var forged = send("POST", "/client-payout/v1/beneficiary-accounts", JSON.writeValueAsString(body));

        assertTrue(forged.statusCode() == 400 || forged.statusCode() == 422, forged.body());
        assertTrue(CALLS.stream().noneMatch(call -> call.path().startsWith("/payout/")), CALLS.toString());
    }

    /** 详情、设默认与关闭：都带当前工作区 Owner；payout 的 404 / 409 原样到达。 */
    @Test
    void test_accountCommandsCarryWorkspaceOwner() throws Exception {
        REPLIES.put("GET " + ACCOUNTS + "/" + ACCOUNT_ID, problem(404, PayoutErrorCode.BENEFICIARY_ACCOUNT_NOT_FOUND.getCode()));
        REPLIES.put("POST " + ACCOUNTS + "/" + ACCOUNT_ID + "/default",
            problem(409, PayoutErrorCode.BENEFICIARY_STATUS_CONFLICT.getCode()));
        REPLIES.put("POST " + ACCOUNTS + "/" + ACCOUNT_ID + "/close", json(account()));

        var detail = send("GET", "/client-payout/v1/beneficiary-accounts/" + ACCOUNT_ID, null);
        var setDefault = send("POST", "/client-payout/v1/beneficiary-accounts/" + ACCOUNT_ID + "/default",
            "{\"expectedVersion\":\"2\"}");
        var close = send("POST", "/client-payout/v1/beneficiary-accounts/" + ACCOUNT_ID + "/close",
            "{\"reasonCode\":\"" + BeneficiaryCloseReason.OWNER_REQUEST.getCode() + "\",\"expectedVersion\":\"2\"}");

        assertEquals(404, detail.statusCode(), detail.body());
        assertEquals(409, setDefault.statusCode(), setDefault.body());
        assertEquals(200, close.statusCode(), close.body());
        var calls = CALLS.stream().filter(call -> call.path().startsWith("/payout/")).toList();
        assertEquals("ownerType=CLIENT&ownerId=" + CLIENT_ID, calls.get(0).query());
        for (var command : calls.subList(1, 3)) {
            var forwarded = JSON.readTree(command.body());
            assertAll(() -> assertEquals(CLIENT_ID, forwarded.path("ownerId").asString()),
                () -> assertEquals(BeneficiaryActorType.CLIENT_MEMBER.getCode(), forwarded.path("actorType").asString()),
                () -> assertEquals("2", forwarded.path("expectedVersion").asString()));
        }
        assertEquals(BeneficiaryCloseReason.OWNER_REQUEST.getCode(), JSON.readTree(calls.get(2).body()).path("reasonCode").asString());
    }

    /** 余额与字段要求：余额 Owner 为当前工作区；字段要求 Query 白名单与必填。 */
    @Test
    void test_balancesAndRequirements() throws Exception {
        var position = new LinkedHashMap<String, Object>();
        position.put("entityNo", "AE001");
        position.put("currency", "USD");
        position.put("pending", "0.00");
        position.put("available", "0.00");
        position.put("locked", "0.00");
        position.put("securityDeposit", "0.00");
        position.put("recoveryReceivable", "0.00");
        position.put("blockReasons", List.of(BeneficiaryBlockReason.NO_AVAILABLE_BALANCE.getCode()));
        REPLIES.put("GET /payout/v1/balance-explanations", json(Map.of("ownerType", BeneficiaryOwnerType.CLIENT.getCode(),
            "ownerId", CLIENT_ID, "positions", List.of(position), "usableAccounts", List.of())));
        REPLIES.put("GET /payout/v1/beneficiary-requirements", json(Map.of("railType", BeneficiaryRailType.SWIFT.getCode(),
            "bankCountry", "*", "currency", "USD", "fields", List.of())));

        var balances = send("GET", "/client-payout/v1/balances", null);
        var requirements = send("GET", "/client-payout/v1/beneficiary-requirements?railType=SWIFT&bankCountry=GB&currency=USD", null);
        var missing = send("GET", "/client-payout/v1/beneficiary-requirements?railType=SWIFT&currency=USD", null);

        assertEquals(200, balances.statusCode(), balances.body());
        assertEquals(BeneficiaryBlockReason.NO_AVAILABLE_BALANCE.getCode(),
            JSON.readTree(balances.body()).path("positions").get(0).path("blockReasons").get(0).asString());
        assertEquals(200, requirements.statusCode(), requirements.body());
        assertEquals(400, missing.statusCode(), missing.body());
        var calls = CALLS.stream().filter(call -> call.path().startsWith("/payout/")).toList();
        assertEquals(2, calls.size());
        assertEquals("ownerType=CLIENT&ownerId=" + CLIENT_ID, calls.get(0).query());
        assertEquals("railType=SWIFT&bankCountry=GB&currency=USD", calls.get(1).query());
    }

    /** 权限：只读成员不能提交；没有余额权限不能看余额；都不调用 payout。 */
    @Test
    void test_permissionsGateEachEndpoint() throws Exception {
        granted = List.of("CLIENT_PAYOUT_ACCOUNT_READ");

        var submit = send("POST", "/client-payout/v1/beneficiary-accounts", "{}");
        var balances = send("GET", "/client-payout/v1/balances", null);

        assertEquals(403, submit.statusCode(), submit.body());
        assertEquals(403, balances.statusCode(), balances.body());
        assertTrue(CALLS.stream().noneMatch(call -> call.path().startsWith("/payout/")), CALLS.toString());
        var asked = CALLS.stream().filter(call -> call.path().endsWith("/authorize")).map(Call::body).toList();
        assertTrue(asked.get(0).contains("CLIENT_PAYOUT_ACCOUNT_MANAGE"), asked.toString());
        assertTrue(asked.get(1).contains("CLIENT_BALANCE_READ"), asked.toString());
    }

    /** 账号上下文与非法 Query：本地拒绝，不调用 payout。 */
    @Test
    void test_rejectsAccountContextAndUnknownQuery() throws Exception {
        var account = send("GET", "/client-payout/v1/beneficiary-accounts", null, "ACCOUNT");
        var query = send("GET", "/client-payout/v1/beneficiary-accounts?ownerId=21", null);
        var badId = send("GET", "/client-payout/v1/beneficiary-accounts/abc", null);

        assertEquals(403, account.statusCode(), account.body());
        assertEquals(400, query.statusCode(), query.body());
        assertEquals(400, badId.statusCode(), badId.body());
        assertTrue(CALLS.stream().noneMatch(call -> call.path().startsWith("/payout/")), CALLS.toString());
    }

    /** 唯一一次 payout 调用。 */
    private static Call payoutCall() {
        var calls = CALLS.stream().filter(call -> call.path().startsWith("/payout/")).toList();
        assertEquals(1, calls.size(), calls.toString());
        return calls.getFirst();
    }

    /** payout 账户视图（Client 视图不含共用提示）。 */
    private static Map<String, Object> account() {
        var account = new LinkedHashMap<String, Object>();
        account.put("beneficiaryAccountId", ACCOUNT_ID);
        account.put("ownerType", BeneficiaryOwnerType.CLIENT.getCode());
        account.put("ownerId", CLIENT_ID);
        account.put("accountCode", "BA-0001");
        account.put("railType", BeneficiaryRailType.LOCAL.getCode());
        account.put("bankCountry", "AE");
        account.put("currency", "USD");
        account.put("bankName", "Emirates NBD");
        account.put("holderNameMasked", "E****** T****** LLC");
        account.put("accountLast4", "3456");
        account.put("contentHash", "b".repeat(64));
        account.put("status", BeneficiaryAccountStatus.PENDING_REVIEW.getCode());
        account.put("submittedByType", BeneficiaryActorType.CLIENT_MEMBER.getCode());
        account.put("submittedBy", "1");
        account.put("usable", false);
        account.put("defaultAccount", false);
        account.put("version", "1");
        account.put("createTime", "2026-10-30T06:05:00Z");
        account.put("updateTime", "2026-10-30T06:05:00Z");
        return account;
    }

    /** 成功 JSON。 */
    private static Reply json(Object body) {
        return new Reply(200, "application/json", JSON.writeValueAsString(body));
    }

    /** 严格 Problem。 */
    private static Reply problem(int status, String code) {
        String title = org.springframework.http.HttpStatus.valueOf(status).getReasonPhrase();
        return new Reply(status, "application/problem+json", "{\"type\":\"about:blank\",\"title\":\"" + title + "\",\"detail\":\"fixture\","
            + "\"code\":\"" + code + "\",\"requestId\":\"fixture-owner\",\"timestamp\":\"2026-10-30T00:00:00.000000Z\"}");
    }

    /** 租户上下文请求。 */
    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        return send(method, path, body, "TENANT");
    }

    /** 经 Gateway 重建的身份头；租户上下文固定为成员 501 / 租户 601。 */
    private HttpResponse<String> send(String method, String path, String body, String contextType) throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
            .header("Origin", ORIGIN).header("X-Request-Id", "browser-payout-fixture")
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

    /** 测试端点在虚拟线程上响应，记录每次调用。 */
    private static HttpServer startOwner() {
        try {
            var server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());
            server.createContext("/", ClientPayoutHttpAutoTest::respond);
            server.start();
            return server;
        } catch (Exception failure) {
            throw new IllegalStateException(failure);
        }
    }

    /** Platform 的上下文、工作区与授权；payout 按“方法 + 路径”返回配置的响应。 */
    private static void respond(HttpExchange exchange) throws java.io.IOException {
        String path = exchange.getRequestURI().getPath(), method = exchange.getRequestMethod();
        String body = new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
        CALLS.add(new Call(method, path, exchange.getRequestURI().getRawQuery(), body,
            exchange.getRequestHeaders().getFirst(PayoutHeaders.CALLER_SERVICE)));
        Reply reply;
        if (path.endsWith("/authentication/v1/client/context")) {
            reply = new Reply(200, "application/json", "{\"accountId\":\"1\",\"sessionId\":\"2\",\"emailMasked\":\"f***@example.test\","
                + "\"emailVerified\":true,\"contextType\":\"TENANT\",\"tenantId\":\"601\",\"memberId\":\"501\","
                + "\"allowedActions\":[\"LOGOUT\"],\"fallbackReason\":null}");
        } else if (path.endsWith("/authentication/v1/client/workspaces")) {
            reply = new Reply(200, "application/json", "{\"items\":[{\"memberId\":\"501\",\"tenantId\":\"601\",\"clientId\":\"" + CLIENT_ID
                + "\",\"ownerStatus\":\"ACTIVE\",\"securityStatus\":\"ACTIVE\",\"memberStatus\":\"ACTIVE\",\"accessible\":true,"
                + "\"reasonCode\":null}]}");
        } else if (path.endsWith("/authorization/v1/client/authorize")) {
            var asked = JSON.readTree(body).path("permissionCodes");
            var result = granted.stream().filter(code -> asked.toString().contains("\"" + code + "\"")).toList();
            reply = json(Map.of("granted", result));
        } else {
            reply = REPLIES.get(method + " " + path);
        }
        if (reply == null) {
            UNEXPECTED.add(method + " " + path);
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
            return;
        }
        var bytes = reply.body().getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().set("Content-Type", reply.contentType());
        exchange.getResponseHeaders().set("X-Request-Id", "fixture-owner");
        exchange.sendResponseHeaders(reply.status(), bytes.length);
        try (var output = exchange.getResponseBody()) {
            output.write(bytes);
        }
    }
}
