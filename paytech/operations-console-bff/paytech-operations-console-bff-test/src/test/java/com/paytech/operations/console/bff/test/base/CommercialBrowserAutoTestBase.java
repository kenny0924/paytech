package com.paytech.operations.console.bff.test.base;

import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.AUTHORIZE_PATH;
import static com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.SESSION_ID;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
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

import com.paytech.operations.console.bff.test.simulator.CommercialOwnerSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator;
import com.paytech.operations.console.bff.test.simulator.PlatformOperationsSimulator.RecordedRequest;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 商务浏览器测试的公共入口：真实 BFF 入站 → PLT-I02（Platform Simulator）→ 真实 HttpExchange 客户端 → commercial Simulator
 * <li>每例重置 Platform 与 commercial 两个 Simulator；AfterEach 断言两者都没有意外调用，并释放挂起响应。</li>
 * <li>边界清单每行包含 id、method、BFF path、ownerPath、query、request、status、ownerStatus、response、routeCode；
 * 合同 / 模板版本写入另有 preReads（转发前读取方案或版本 Owner 类型的 GET 调用及其响应）；
 * 不携带操作人的 POST（如开通项前置核对）标注 actorForwarded=false。</li>
 * <li>操作者是数字 Account ID（commercial 只接受规范正十进制操作人）。</li>
 *
 * @author Claude
 */
public abstract class CommercialBrowserAutoTestBase extends OperationsBffHttpTestBase {
    protected static final JsonMapper JSON = JsonMapper.builder().findAndAddModules().build();
    protected static final String REQUEST_ID = "commercial-boundary-request";
    protected static final String BOUNDARY = "/commercial-admin-boundary.json";
    /** 会话操作者（数字 Account ID）。 */
    protected static final String ACTOR = PlatformOperationsSimulator.RISK_ACCOUNT_ID;
    private static final HttpClient HTTP = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();

    @Autowired
    protected PlatformOperationsSimulator platform;

    @Autowired
    protected CommercialOwnerSimulator commercial;

    @LocalServerPort
    private int port;

    /** 每例从空 Owner 配置与默认授权（含全部商务权限）开始。 */
    @BeforeEach
    void setUpCommercialMocks() {
        platform.reset();
        platform.useCommercialIdentity();
        commercial.reset();
    }

    /** Owner 与 Platform 不得收到未配置的请求；无论断言结果都释放挂起响应。 */
    @AfterEach
    void verifyCommercialOwnerCalls() {
        try {
            verifyNoUnexpectedCalls();
        } finally {
            commercial.reset();
        }
    }

    /** 同一用例内的下一次独立调用：先确认此前没有意外调用，再清空记录与配置并恢复默认授权。 */
    protected void resetBetweenCalls() {
        verifyNoUnexpectedCalls();
        platform.reset();
        platform.useCommercialIdentity();
        commercial.reset();
    }

    private void verifyNoUnexpectedCalls() {
        assertAll(() -> assertTrue(platform.unexpectedRequests().isEmpty(), platform.unexpectedRequests().toString()),
            () -> assertTrue(commercial.unexpectedRequests().isEmpty(), commercial.unexpectedRequests().toString()));
    }

    /** 读取边界清单。 */
    protected static Stream<JsonNode> rows() {
        try (var input = CommercialBrowserAutoTestBase.class.getResourceAsStream(BOUNDARY)) {
            var rows = new ArrayList<JsonNode>();
            JSON.readTree(input).forEach(rows::add);
            return rows.stream();
        } catch (java.io.IOException failure) {
            throw new IllegalStateException("无法读取边界清单 " + BOUNDARY, failure);
        }
    }

    /** 指定编号的边界样例。 */
    protected static JsonNode row(String id) {
        return rows().filter(row -> id.equals(row.path("id").asString())).findFirst().orElseThrow();
    }

    /** 按样例配置 commercial 的成功响应。 */
    protected void replyAsOwner(JsonNode row) {
        commercial.reply(row.path("method").asString(), row.path("ownerPath").asString(),
            row.path("ownerStatus").asInt(), JSON.writeValueAsString(row.path("response")));
    }

    /** 按样例配置转发前读取（preReads）的 Owner 响应；没有 preReads 的样例不配置。 */
    protected void replyPreReads(JsonNode row) {
        row.path("preReads").forEach(read -> commercial.reply(read.path("method").asString(),
            read.path("ownerPath").asString(), 200, JSON.writeValueAsString(read.path("response"))));
    }

    /** 按样例原样发送：样例 Query 与 Body，带 Origin；转发前读取的 Owner 响应随样例配置。 */
    protected HttpResponse<String> send(JsonNode row) throws Exception {
        replyPreReads(row);
        return send(row.path("method").asString(), row.path("path").asString() + row.path("query").asString(),
            row.path("routeCode").asString(), row.path("request"));
    }

    /**
     * 经真实 BFF HTTP 入口发送浏览器请求，带 Gateway 可信身份；写方法统一声明 JSON
     * <li>额外携带浏览器 Authorization、Cookie 与 Idempotency-Key，用于证明 BFF 不向 commercial 转发。</li>
     */
    protected HttpResponse<String> send(String method, String pathAndQuery, String routeCode, JsonNode body)
        throws Exception {
        var builder = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + pathAndQuery))
            .timeout(Duration.ofSeconds(15))
            .header("X-Request-Id", REQUEST_ID).header("X-Gateway-Realm", "OPERATIONS")
            .header("X-Gateway-Channel", "OPERATIONS").header("X-Gateway-Actor-Type", "PLATFORM_USER")
            .header("X-Gateway-Actor-Subject", ACTOR).header("X-Gateway-Session-Id", SESSION_ID)
            .header("X-Gateway-Route-Code", routeCode).header("X-Gateway-Context-Type", "TENANT")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Id", "tenant_10001")
            .header("X-Gateway-Tenant-Member-Id", "member_10001").header("Idempotency-Key", "commercial-command-0001")
            .header("Authorization", "Bearer browser-access-token").header("Cookie", "browser-session=canary")
            .header("Origin", "https://operations-console.local.test");
        if (!"GET".equals(method)) {
            builder.header("Content-Type", "application/json");
        }
        return HTTP.send(builder.method(method, body == null || body.isNull() || body.isMissingNode()
            ? HttpRequest.BodyPublishers.noBody()
            : HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body))).build(),
            HttpResponse.BodyHandlers.ofString());
    }

    /**
     * 成功转发：状态与 JSON 原样返回；commercial 先收到样例 preReads 的只读调用，再收到一次写入或查询，方法、路径、Query 与 Body 一致
     * <li>命令携带会话操作者作为 X-Gateway-Actor-Subject，查询、转发前读取与 actorForwarded=false 的 POST 不携带；Body 不含操作人字段；
     * 全部调用带 X-Caller-Service，不转发 Authorization、Cookie、Idempotency-Key。</li>
     */
    protected void assertForwarded(JsonNode row, HttpResponse<String> response) {
        assertEquals(row.path("status").asInt(), response.statusCode(), response.body());
        assertEquals(row.path("response"), JSON.readTree(response.body()));
        assertEquals("no-store", response.headers().firstValue("Cache-Control").orElse(null));
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        var preReads = new ArrayList<JsonNode>();
        row.path("preReads").forEach(preReads::add);
        assertEquals(preReads.size() + 1, commercial.requests().size(), commercial.requests().toString());
        for (int index = 0; index < preReads.size(); index++) {
            var read = commercial.requests().get(index);
            var expected = preReads.get(index);
            assertAll(() -> assertEquals(expected.path("method").asString(), read.method()),
                () -> assertEquals(expected.path("ownerPath").asString(), read.path()),
                () -> assertNull(read.header("X-Gateway-Actor-Subject")), () -> assertTrue(read.body().isEmpty()));
        }
        var call = commercial.requests().getLast();
        boolean command = !"GET".equals(row.path("method").asString())
            && row.path("actorForwarded").asBoolean(true);
        assertAll(() -> assertEquals(row.path("method").asString(), call.method()),
            () -> assertEquals(row.path("ownerPath").asString(), call.path()),
            () -> assertEquals(query(row.path("query").asString()), query(call.query())),
            () -> assertEquals(REQUEST_ID, call.header("X-Request-Id")),
            () -> assertEquals("operations-console-bff", call.header("X-Caller-Service")),
            () -> assertEquals(command ? ACTOR : null, call.header("X-Gateway-Actor-Subject")),
            () -> assertNull(call.header("X-Gateway-Session-Id")),
            () -> assertNull(call.header("Authorization")), () -> assertNull(call.header("Cookie")),
            () -> assertNull(call.header("Idempotency-Key")));
        if (row.path("request").isNull()) {
            assertTrue(call.body().isEmpty(), "无 Body 的请求不得转发 Body");
        } else {
            var forwarded = JSON.readTree(call.body());
            assertEquals(row.path("request"), forwarded);
            assertTrue(forwarded.path("operatorAccountId").isMissingNode(), "Body 不得携带操作人字段");
        }
    }

    /** 当前会话授权被拒：403，只调用一次 PLT-I02，commercial 零调用。 */
    protected void assertDenied(HttpResponse<String> response) {
        assertEquals(403, response.statusCode(), response.body());
        assertEquals(List.of(AUTHORIZE_PATH), platform.requests().stream().map(RecordedRequest::path).toList());
        assertTrue(commercial.requests().isEmpty(), commercial.requests().toString());
    }

    /** 路由范围不符：400 BUSINESS_CHECK_FAILED，commercial 只收到转发前读取，没有写入。 */
    protected void assertScopeRejected(JsonNode row, HttpResponse<String> response) {
        assertEquals(400, response.statusCode(), response.body());
        assertEquals("BUSINESS_CHECK_FAILED", JSON.readTree(response.body()).path("code").asString());
        assertTrue(commercial.requests().stream().allMatch(call -> "GET".equals(call.method())),
            commercial.requests().toString());
        assertEquals(row.path("preReads").size(), commercial.requests().size(), commercial.requests().toString());
    }

    /** 本地拒绝：指定状态，commercial 零调用。 */
    protected void assertRejectedLocally(int status, HttpResponse<String> response) {
        assertEquals(status, response.statusCode(), response.body());
        assertTrue(commercial.requests().isEmpty(), commercial.requests().toString());
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
