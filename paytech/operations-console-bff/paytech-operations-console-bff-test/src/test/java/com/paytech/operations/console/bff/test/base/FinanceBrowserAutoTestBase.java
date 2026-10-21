package com.paytech.operations.console.bff.test.base;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.ACCOUNT_ID;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.operations.console.bff.test.simulator.AccountOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.BillingOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.FinanceOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.LedgerOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;
import com.paytech.operations.console.bff.test.simulator.ReconciliationOwnerSimulator;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 财务转发浏览器测试的公共入口：真实 BFF 入站 → PLT-I02（Platform Simulator）→ 固定地址 Owner Simulator
 * <li>每例重置 Platform、ledger、account、billing、reconciliation 五个 Simulator；AfterEach 断言五者都没有意外调用，并释放挂起响应。</li>
 * <li>边界清单每行包含 id、method、BFF path、query、request、status、response、routeCode；Owner 路径默认为 BFF 路径去掉 /finance，
 * 路径不同时（billing）由 ownerPath 给出。</li>
 *
 * @author Claude
 */
public abstract class FinanceBrowserAutoTestBase extends OperationsBffHttpTestBase {
    protected static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    protected static final String REQUEST_ID = "finance-boundary-request";
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    protected PlatformOperationsSimulator platform;

    @Autowired
    protected LedgerOwnerSimulator ledger;

    @Autowired
    protected AccountOwnerSimulator account;

    @Autowired
    protected BillingOwnerSimulator billing;

    @Autowired
    protected ReconciliationOwnerSimulator reconciliation;

    @LocalServerPort
    private int port;

    /** 每例从空 Owner 配置与默认授权开始。 */
    @BeforeEach
    void setUpFinanceMocks() {
        platform.reset();
        ledger.reset();
        account.reset();
        billing.reset();
        reconciliation.reset();
    }

    /** Owner 与 Platform 不得收到未配置的请求；无论断言结果都释放挂起响应。 */
    @AfterEach
    void verifyFinanceOwnerCalls() {
        try {
            verifyNoUnexpectedCalls();
        } finally {
            ledger.reset();
            account.reset();
            billing.reset();
            reconciliation.reset();
        }
    }

    /** 同一用例内的下一次独立调用：先确认此前没有意外调用，再清空记录与响应配置并恢复默认授权。 */
    protected void resetBetweenCalls() {
        verifyNoUnexpectedCalls();
        platform.reset();
        ledger.reset();
        account.reset();
        billing.reset();
        reconciliation.reset();
    }

    private void verifyNoUnexpectedCalls() {
        assertAll(() -> assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()),
            () -> assertTrue(ledger.unexpectedRequests().isEmpty(), ledger.unexpectedRequests().toString()),
            () -> assertTrue(account.unexpectedRequests().isEmpty(), account.unexpectedRequests().toString()),
            () -> assertTrue(billing.unexpectedRequests().isEmpty(), billing.unexpectedRequests().toString()),
            () -> assertTrue(reconciliation.unexpectedRequests().isEmpty(),
                reconciliation.unexpectedRequests().toString()));
    }

    /** 读取边界清单。 */
    protected static Stream<JsonNode> rows(String resource) {
        try (var input = FinanceBrowserAutoTestBase.class.getResourceAsStream(resource)) {
            var rows = new ArrayList<JsonNode>();
            JSON.readTree(input).forEach(rows::add);
            return rows.stream();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("无法读取边界清单 " + resource, failure);
        }
    }

    /** 指定编号的边界样例。 */
    protected static JsonNode row(String resource, String id) {
        return rows(resource).filter(row -> id.equals(row.path("id").asString())).findFirst().orElseThrow();
    }

    /** Owner 路径：样例给出 ownerPath 时取之，否则为 BFF 路径去掉 /finance。 */
    protected static String ownerPath(JsonNode row) {
        if (row.hasNonNull("ownerPath")) {
            return row.path("ownerPath").asString();
        }
        return row.path("path").asString().substring("/finance".length());
    }

    /** 按样例配置 Owner 的成功响应。 */
    protected static void replyAsOwner(FinanceOwnerSimulator owner, JsonNode row) {
        owner.reply(row.path("method").asString(), ownerPath(row), row.path("status").asInt(),
            JSON.writeValueAsString(row.path("response")));
    }

    /** 按样例原样发送：样例 Query 与 Body，带 Origin。 */
    protected HttpResponse<String> send(JsonNode row) throws Exception {
        return send(row, row.path("query").asString(), row.path("request"), true);
    }

    /**
     * 经真实 BFF HTTP 入口发送浏览器请求，带 Gateway 可信身份；写方法统一声明 JSON
     * <li>额外携带浏览器 Authorization、Cookie 与 Idempotency-Key，用于证明 BFF 不向 Owner 转发。</li>
     */
    protected HttpResponse<String> send(JsonNode row, String query, JsonNode body, boolean origin) throws Exception {
        String method = row.path("method").asString();
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + row.path("path").asString() + query))
            .timeout(Duration.ofSeconds(15))
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACCOUNT_ID).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", row.path("routeCode").asString()).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", "finance-command-0001")
            .header("Authorization", "Bearer browser-access-token").header("Cookie", "browser-session=canary");
        if (origin) {
            builder.header("Origin", "https://operations-console.local.test");
        }
        if (!"GET".equals(method)) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(method, body == null || body.isNull() || body.isMissingNode()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 成功转发：状态与 JSON 原样返回；Owner 只收到一次调用，方法、路径、Query 与 Body 一致
     * <li>命令携带会话 Account 与 Session 作为操作人 Header，查询不携带；Authorization、Cookie、Idempotency-Key 一律不转发。</li>
     */
    protected void assertForwarded(FinanceOwnerSimulator owner, JsonNode row, HttpResponse<String> response) {
        assertEquals(row.path("status").asInt(), response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        assertEquals(1, owner.requests().size(), owner.requests().toString());
        var call = owner.requests().getFirst();
        boolean command = !"GET".equals(row.path("method").asString());
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertEquals(ownerPath(row), call.path()),
            () -> assertEquals(query(row.path("query").asString()), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals(command ? ACCOUNT_ID : null, call.header("X-Gateway-Actor-Subject")),
            () -> assertEquals(command ? SESSION_ID : null, call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (row.path("request").isNull()) {
            assertTrue(call.body().isEmpty(), "无 Body 的请求不得转发 Body");
        } else {
            assertEquals(row.path("request"), JSON.readTree(call.body()));
        }
    }

    /** 当前会话授权被拒：403，只调用一次 PLT-I02，Owner 零调用。 */
    protected void assertDenied(FinanceOwnerSimulator owner, HttpResponse<String> response) {
        assertEquals(403, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        assertTrue(owner.requests().isEmpty(), owner.requests().toString());
    }

    /** Owner 登记的合同错误码原样到达浏览器：400、枚举 Code 与默认说明、本次 Request ID，不外露 Owner 原文。 */
    protected static void assertCodedRejection(CodeMessageEnum code, HttpResponse<String> response) {
        assertEquals(400, response.statusCode(), response.body());
        var body = JSON.readTree(response.body());
        assertAll(() -> assertEquals(code.getCode(), body.path("code").asString()),
            () -> assertEquals(code.getMessage(), body.path("detail").asString()),
            () -> assertEquals(REQUEST_ID, body.path("requestId").asString()),
            () -> assertFalse(response.body().contains(FinanceOwnerSimulator.OWNER_DETAIL_CANARY)),
            () -> assertFalse(response.body().contains(FinanceOwnerSimulator.OWNER_REQUEST_ID)));
    }

    /** 本地拒绝：指定状态，PLT-I02 已完成，Owner 零调用。 */
    protected void assertRejectedLocally(int status, FinanceOwnerSimulator owner, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        assertTrue(owner.requests().isEmpty(), owner.requests().toString());
    }

    /** 解析 Query 为键值。 */
    protected static Map<String, String> query(String raw) {
        if (raw == null || raw.isEmpty() || raw.equals("?")) {
            return Map.of();
        }
        var result = new HashMap<String, String>();
        for (String pair : raw.replaceFirst("^\\?", "").split("&")) {
            String[] parts = pair.split("=", 2);
            result.put(URLDecoder.decode(parts[0], StandardCharsets.UTF_8),
                URLDecoder.decode(parts.length > 1 ? parts[1] : "", StandardCharsets.UTF_8));
        }
        return result;
    }
}
