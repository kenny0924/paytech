package com.paytech.gateway.auto.routing;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;
import static org.junit.jupiter.api.Assertions.*;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.simulator.bff.BffRequestJournal;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.web.GatewayErrorCode;
import com.paytech.gateway.web.GatewayProtocol;
import com.paytech.platform.support.api.enums.GatewayAuthorizationMode;
import com.paytech.platform.support.api.enums.GatewayGuardDimension;
import com.paytech.platform.support.api.enums.GatewayGuardRuleType;
import com.paytech.platform.support.api.enums.GatewayHttpMethod;
import com.paytech.platform.support.api.enums.GatewayIdempotencyMode;
import com.paytech.platform.support.api.enums.GatewayRequestCookieMode;
import com.paytech.platform.support.api.enums.GatewayResponseCookieMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;
import com.paytech.platform.support.api.enums.GatewaySecurityProfileCode;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayGuardRuleSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayTargetSnapshot;

/**
 * 渠道支付通知公开入口：CHANNEL_CALLBACK 路由不认证放行，原始 Body 与 sign 原样转发，Body 上限按路由，
 * 未登记路径与其他入口渠道仍由兜底拒绝。路由参数与 Platform 块 133 一致。
 *
 * @see com.paytech.gateway.security.ChannelCallbackSecurityConfiguration
 */
class GatewayChannelCallbackTest extends GatewayAutoTestBase {
    private static final String TARGET = "channel-connector-service";
    private static final String ROUTE = "channel-payment-callback";
    private static final String EXTERNAL_PATH = "/api/channel-callbacks/v1/{channelCode}/{channelAccountId}";
    private static final String DOWNSTREAM_PATH = "/channel-callbacks/v1/{channelCode}/{channelAccountId}";
    private static final String CALLBACK_PATH = "/api/channel-callbacks/v1/PAYERMAX/9200000000101";
    private static final String OWNER_PATH = "/channel-callbacks/v1/PAYERMAX/9200000000101";
    private static final String SIGN = "sign";
    private static final String SIGNATURE = "c2lnbmF0dXJlLW9mLXJhdy1ib2R5LWJ5dGVz+/==";
    private static final String CONTENT_TYPE = "application/json;charset=UTF-8";
    private static final long BODY_LIMIT_BYTES = 65_536;
    private static final String ACKNOWLEDGEMENT = "{\"code\":\"SUCCESS\",\"msg\":\"Success\"}";
    /** 字段顺序、空白与转义均非规范化，用于证明 Gateway 不改写 Body。 */
    private static final byte[] NOTIFICATION = ("{ \"notifyType\":\"PAYMENT\",\n  \"data\" : {\"outTradeNo\":"
        + "\"730000000000000301\",\"tradeToken\":\"T2026102208123456\",\"status\":\"SUCCESS\","
        + "\"remark\":\"\\u4e2d\\u6587\"} }").getBytes(StandardCharsets.UTF_8);

    @LocalServerPort
    private int port;

    /** 发布与块 133 一致、已激活的渠道回调路由，目标实例指向本地 Owner Simulator。 */
    @BeforeEach
    void setUpChannelCallbackRoute() {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot(TARGET, GatewayRouteChannel.CHANNEL_CALLBACK.getCode(),
                GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE)),
            List.of(callbackRoute())));
        assertTrue(refreshSnapshot(), "渠道回调快照应可整体加载");
    }

    /** 不带任何认证即可到达 Owner；Body 字节、sign 与 content-type 原样转发，Owner 应答原样返回。 */
    @Test
    void test_normalFlow() {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, OWNER_PATH,
            BffScenarioStore.Response.json(200, ACKNOWLEDGEMENT));

        callback(CALLBACK_PATH, NOTIFICATION).expectStatus().isOk().expectBody().json(ACKNOWLEDGEMENT);

        BffRequestJournal.Entry entry = onlyOwnerRequest();
        assertAll(() -> assertEquals(HttpMethod.POST, entry.method()),
            () -> assertEquals(OWNER_PATH, entry.path()),
            () -> assertEquals(digest(NOTIFICATION), entry.bodyDigest()),
            () -> assertEquals(List.of(SIGNATURE), header(entry, SIGN)),
            () -> assertEquals(List.of(CONTENT_TYPE), header(entry, HttpHeaders.CONTENT_TYPE)),
            () -> assertEquals(List.of(ROUTE), header(entry, GatewayProtocol.ROUTE_CODE)),
            () -> assertEquals(1, header(entry, GatewayProtocol.REQUEST_ID).size()));
    }

    /** 未列入白名单的调用方 Header 一律不转发，调用方 Cookie 与 Authorization 也不会到达 Owner。 */
    @Test
    void test_unlistedHeadersDropped() {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, OWNER_PATH,
            BffScenarioStore.Response.json(200, ACKNOWLEDGEMENT));

        callbackClient().post().uri(CALLBACK_PATH).header(SIGN, SIGNATURE)
            .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE).header(HttpHeaders.AUTHORIZATION, "Bearer forged")
            .header(HttpHeaders.COOKIE, "session=forged").header("X-Unapproved", "value")
            .header("X-Gateway-Route-Code", "forged-route").bodyValue(NOTIFICATION).exchange().expectStatus().isOk();

        BffRequestJournal.Entry entry = onlyOwnerRequest();
        assertAll(() -> assertTrue(header(entry, HttpHeaders.AUTHORIZATION).isEmpty()),
            () -> assertTrue(header(entry, HttpHeaders.COOKIE).isEmpty()),
            () -> assertTrue(header(entry, "X-Unapproved").isEmpty()),
            () -> assertTrue(header(entry, GatewayProtocol.CLIENT_IP).isEmpty()),
            () -> assertTrue(header(entry, GatewayProtocol.ENTRY_CHANNEL).isEmpty()),
            () -> assertEquals(List.of(ROUTE), header(entry, GatewayProtocol.ROUTE_CODE)));
    }

    /** Owner 验签失败的 401 Problem 原样返回给渠道，便于渠道按非成功应答重发。 */
    @Test
    void test_ownerProblemForwarded() {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, OWNER_PATH,
            BffScenarioStore.Response.json(401, """
                {"type":"about:blank","title":"Unauthorized","detail":"渠道通知签名无效",
                 "code":"CHANNEL_CALLBACK_SIGNATURE_INVALID","requestId":"%s","timestamp":"2026-10-22T08:00:00.000000Z"}
                """.formatted(BffScenarioStore.REQUEST_ID_TOKEN)).withContentTypes("application/problem+json")
                .withHeader(GatewayProtocol.REQUEST_ID, BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId());

        assertProblem(callback(CALLBACK_PATH, NOTIFICATION), 401, "CHANNEL_CALLBACK_SIGNATURE_INVALID");
        assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    /** 超过路由 Body 上限时由 Gateway 返回 413，Owner 零调用；恰好等于上限仍转发。 */
    @Test
    void test_bodyLimit() {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, OWNER_PATH,
            BffScenarioStore.Response.json(200, ACKNOWLEDGEMENT));
        callback(CALLBACK_PATH, padded((int) BODY_LIMIT_BYTES)).expectStatus().isOk();
        assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());

        assertProblem(callback(CALLBACK_PATH, padded((int) BODY_LIMIT_BYTES + 1)), 413,
            GatewayErrorCode.GATEWAY_PAYLOAD_TOO_LARGE.getCode());
        assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    /** 未登记路径、未登记方法和其他入口渠道都不会借用回调路由，Owner 零调用。 */
    @Test
    void test_unregisteredRequestRejected() {
        assertProblem(callback("/api/channel-callbacks/v1/PAYERMAX", NOTIFICATION), 404,
            GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND.getCode());
        assertProblem(callback("/api/channel-callbacks/v1/PAYERMAX/9200000000101/extra", NOTIFICATION), 404,
            GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND.getCode());
        assertProblem(callbackClient().put().uri(CALLBACK_PATH).header(SIGN, SIGNATURE)
            .header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE).bodyValue(NOTIFICATION).exchange(), 404,
            GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND.getCode());
        assertProblem(GatewayPublicTestClient.operations(port).defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.30")
            .build().post().uri(CALLBACK_PATH).header(SIGN, SIGNATURE).header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
            .bodyValue(NOTIFICATION).exchange(), 404, GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND.getCode());
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    /** 路由停用后同一请求返回 404，Owner 零调用。 */
    @Test
    void test_disabledRouteRejected() {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot(TARGET, GatewayRouteChannel.CHANNEL_CALLBACK.getCode(),
                GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE)),
            List.of()));
        assertTrue(refreshSnapshot());

        assertProblem(callback(CALLBACK_PATH, NOTIFICATION), 404, GatewayErrorCode.GATEWAY_ROUTE_NOT_FOUND.getCode());
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    /**
     * 评审稿只声明 CLIENT_IP 限流而无并发：Gateway 拒绝整份快照并保留上一份有效快照，
     * 因此块 133 必须同时声明 CLIENT_IP 并发规则。
     */
    @Test
    void test_unpairedClientIpGuardRejected() {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot(TARGET, GatewayRouteChannel.CHANNEL_CALLBACK.getCode(),
                GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE)),
            List.of(callbackRoute(List.of(rate(GatewayGuardDimension.SHARED_ROUTE, 600),
                concurrency(GatewayGuardDimension.SHARED_ROUTE, 50), rate(GatewayGuardDimension.CLIENT_IP, 120))))));
        assertFalse(refreshSnapshot(), "CLIENT_IP 缺少并发规则的快照必须被整体拒绝");

        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, OWNER_PATH,
            BffScenarioStore.Response.json(200, ACKNOWLEDGEMENT));
        callback(CALLBACK_PATH, NOTIFICATION).expectStatus().isOk();
    }

    /** 与块 133 一致的路由：不认证、无权限与 Scope、Cookie DROP/NONE、幂等 NONE、Body 64 KiB。 */
    private GatewayRouteSnapshot callbackRoute() {
        return callbackRoute(List.of(rate(GatewayGuardDimension.SHARED_ROUTE, 600),
            concurrency(GatewayGuardDimension.SHARED_ROUTE, 50), rate(GatewayGuardDimension.CLIENT_IP, 120),
            concurrency(GatewayGuardDimension.CLIENT_IP, 20)));
    }

    /** 按场景替换 Guard 规则的回调路由。 */
    private GatewayRouteSnapshot callbackRoute(List<GatewayGuardRuleSnapshot> guards) {
        return new GatewayRouteSnapshot(ROUTE, GatewayRouteChannel.CHANNEL_CALLBACK.getCode(),
            GatewayHttpMethod.POST.getCode(), EXTERNAL_PATH, DOWNSTREAM_PATH, TARGET,
            GatewaySecurityProfileCode.CHANNEL_CALLBACK.getCode(), GatewayAuthorizationMode.CHANNEL_CALLBACK.getCode(),
            null, null, GatewayRequestCookieMode.DROP.getCode(), GatewayResponseCookieMode.NONE.getCode(), List.of(),
            List.of("content-type", SIGN), List.of(), BODY_LIMIT_BYTES, 5_000, 5_000, guards,
            GatewayIdempotencyMode.NONE.getCode());
    }

    private GatewayGuardRuleSnapshot rate(GatewayGuardDimension dimension, long perMinute) {
        return new GatewayGuardRuleSnapshot(GatewayGuardRuleType.RATE_LIMIT.getCode(), dimension.getCode(), perMinute,
            perMinute, 60_000L, null);
    }

    private GatewayGuardRuleSnapshot concurrency(GatewayGuardDimension dimension, long limit) {
        return new GatewayGuardRuleSnapshot(GatewayGuardRuleType.CONCURRENCY.getCode(), dimension.getCode(), null, null,
            null, limit);
    }

    /** 以 Nginx 写入的 CHANNEL_CALLBACK 入口与可信客户端地址访问公开监听端口。 */
    private WebTestClient callbackClient() {
        return GatewayPublicTestClient.builder(port, List.of(GatewayRouteChannel.CHANNEL_CALLBACK.getCode()))
            .defaultHeader(GatewayProtocol.CLIENT_IP, "203.0.113.30").responseTimeout(Duration.ofSeconds(10)).build();
    }

    /** 携带签名头的渠道原始通知请求。 */
    private WebTestClient.ResponseSpec callback(String path, byte[] body) {
        return callbackClient().post().uri(path).header(SIGN, SIGNATURE).header(HttpHeaders.CONTENT_TYPE, CONTENT_TYPE)
            .bodyValue(body).exchange();
    }

    /** 本例 Owner 只应收到一次请求。 */
    private BffRequestJournal.Entry onlyOwnerRequest() {
        List<BffRequestJournal.Entry> entries = GatewayAutoTestRuntime.operationsBffSimulator().journal().entries();
        assertEquals(1, entries.size(), "Owner 请求次数");
        return entries.getFirst();
    }

    /** 不区分大小写读取 Owner 收到的 Header 值。 */
    private List<String> header(BffRequestJournal.Entry entry, String name) {
        return entry.headers().entrySet().stream().filter(item -> item.getKey().equalsIgnoreCase(name))
            .flatMap(item -> item.getValue().stream()).toList();
    }

    /** 指定长度的合法 JSON 字节，用于边界上限。 */
    private byte[] padded(int length) {
        byte[] prefix = "{\"p\":\"".getBytes(StandardCharsets.UTF_8);
        byte[] bytes = new byte[length];
        Arrays.fill(bytes, (byte) 'x');
        System.arraycopy(prefix, 0, bytes, 0, prefix.length);
        bytes[length - 2] = '"';
        bytes[length - 1] = '}';
        return bytes;
    }

    private String digest(byte[] body) {
        try {
            return java.util.HexFormat.of()
                .formatHex(java.security.MessageDigest.getInstance("SHA-256").digest(body));
        } catch (java.security.NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 摘要算法不可用", exception);
        }
    }
}
