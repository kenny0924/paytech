package com.paytech.gateway.auto.web;

import static org.junit.jupiter.api.Assertions.*;
import java.time.Duration;
import java.util.*;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.springframework.test.web.reactive.server.WebTestClient;
import com.paytech.gateway.test.*;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.platform.support.api.gatewayconfiguration.response.*;

/** 配置驱动路由继续执行已有 Cookie 协议，全部请求经真实 Gateway HTTP 和下游 Simulator。 */
class GatewayConfiguredCookieHttpAutoTest extends GatewayAutoTestBase {
    private static final String COOKIE = "__Secure-operations-refresh";
    private static final String ATTRIBUTES = "; Path=/api/operations/auth/v1; Max-Age=60; Secure; HttpOnly; SameSite=Strict";
    private static final String PATH = "/api/operations/cookie-contract";
    @LocalServerPort private int port;

    /** 只转发配置指定的一个合法 Cookie，其他浏览器 Cookie 不进入下游。 */
    @ParameterizedTest
    @ValueSource(strings = {"synthetic", "\"synthetic\"", "!#$%&'()*+-./09:<=?AZ[]^_`az{|}~"})
    void test_requiredCookieForwardsOneOpaqueValueAndDropsOtherCookies(String value) {
        publish("REQUIRE_PROFILE_COOKIE", "NONE");
        respond(BffScenarioStore.Response.json(200, "{}"));
        get("unused=drop; " + COOKIE + "=" + value + "; another=drop").expectStatus().isOk().expectBody().json("{}");
        var request = GatewayAutoTestRuntime.operationsBffSimulator().journal().entries().getFirst();
        var cookies = request.headers().entrySet().stream().filter(e -> e.getKey().equalsIgnoreCase("cookie"))
            .findFirst().orElseThrow().getValue();
        assertEquals(List.of(COOKIE + "=" + value), cookies);
    }

    /** 缺失、重复或非法身份 Cookie 在访问 Owner 前拒绝。 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"unrelated=value", "__Secure-operations-refresh", "__Secure-operations-refresh=",
        "__Secure-operations-refresh=\"\"", "__Secure-operations-refresh=\"", "__Secure-operations-refresh=\"broken",
        "__Secure-operations-refresh=has space", "__Secure-operations-refresh=has,comma",
        "__Secure-operations-refresh=has\\backslash", "__Secure-operations-refresh=a; __Secure-operations-refresh=b"})
    void test_missingAmbiguousOrMalformedRequiredCookieNeverReachesOwner(String cookie) {
        publish("REQUIRE_PROFILE_COOKIE", "NONE");
        get(cookie).expectStatus().isUnauthorized().expectBody().jsonPath("code").isEqualTo("GATEWAY_UNAUTHORIZED");
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    /** 按配置接受签发或清除，并重建固定的安全属性。 */
    @ParameterizedTest
    @CsvSource({"ISSUE_ONLY,synthetic,28800", "ISSUE_ONLY,'\"synthetic\"',1", "ISSUE_OR_CLEAR,synthetic,60",
        "ISSUE_OR_CLEAR,'',0", "CLEAR_ONLY,'',0", "CLEAR_ONLY,'\"\"',0"})
    void test_allowedResponseCookieIsRebuiltInCanonicalAttributeOrder(String mode, String value, long age) {
        publish("DROP", mode);
        var cookie = COOKIE + "=" + value + "; SameSite=Strict; HttpOnly; Secure; Max-Age=" + age
            + "; Path=/api/operations/auth/v1";
        respond(BffScenarioStore.Response.json(200, "{}").withHeader("Set-Cookie", cookie));
        get(null).expectStatus().isOk().expectHeader().valueEquals("Set-Cookie", COOKIE + "=" + value
            + "; Path=/api/operations/auth/v1; Max-Age=" + age + "; Secure; HttpOnly; SameSite=Strict");
    }

    /** 上游 Cookie 违反契约时返回标准 502，且不外泄 Set-Cookie。 */
    @ParameterizedTest
    @MethodSource("invalidResponseCookies")
    void test_invalidResponseCookieBecomesSanitizedBadGateway(String cookie) {
        publish("DROP", "ISSUE_OR_CLEAR");
        respond(BffScenarioStore.Response.json(200, "{}").withHeader("Set-Cookie", cookie));
        get(null).expectStatus().isEqualTo(502).expectHeader().doesNotExist("Set-Cookie")
            .expectBody().jsonPath("code").isEqualTo("UPSTREAM_PROTOCOL_ERROR");
    }

    /** 覆盖属性缺失、重复、越权、非法值及会话有效期边界。 */
    private static Stream<String> invalidResponseCookies() {
        String valid = COOKIE + "=synthetic" + ATTRIBUTES;
        return Stream.of("missing-separator", "other=synthetic" + ATTRIBUTES,
            COOKIE + "=\"" + ATTRIBUTES, COOKIE + "=\"broken" + ATTRIBUTES,
            COOKIE + "=has space" + ATTRIBUTES, COOKIE + "=has,comma" + ATTRIBUTES,
            COOKIE + "=has\\slash" + ATTRIBUTES, valid + ";", valid + "; Domain=example.test",
            valid + "; Secure", valid.replace("; Secure", ""), valid.replace("Secure", "Secure=true"),
            valid.replace("HttpOnly", "HttpOnly=true"), valid.replace("SameSite=Strict", "SameSite=Lax"),
            valid.replace("Path=/api/operations/auth/v1", "Path=/"), valid.replace("Max-Age=60", "Max-Age"),
            valid.replace("Max-Age=60", "Max-Age="), valid.replace("Max-Age=60", "Max-Age=-1"),
            valid.replace("Max-Age=60", "Max-Age=99999999999999999999999"),
            valid.replace("Max-Age=60", "Max-Age=28801"), COOKIE + "=" + ATTRIBUTES,
            COOKIE + "=\"\"" + ATTRIBUTES);
    }

    /** 清除与签发语义必须遵守当前路由的响应策略。 */
    @ParameterizedTest
    @CsvSource({"ISSUE_ONLY,0", "CLEAR_ONLY,1", "NONE,60"})
    void test_responseCookieCannotViolateConfiguredIssueOrClearMode(String mode, long age) {
        publish("DROP", mode);
        respond(BffScenarioStore.Response.json(200, "{}").withHeader("Set-Cookie",
            (COOKIE + "=synthetic" + ATTRIBUTES).replace("Max-Age=60", "Max-Age=" + age)));
        get(null).expectStatus().isEqualTo(502).expectHeader().doesNotExist("Set-Cookie");
    }

    /** 多条 Set-Cookie 不能绕过单身份响应合同。 */
    @Test
    void test_multipleResponseCookiesAreNotAccepted() {
        publish("DROP", "ISSUE_OR_CLEAR");
        respond(BffScenarioStore.Response.json(200, "{}").withHeaders("Set-Cookie",
            COOKIE + "=first" + ATTRIBUTES, COOKIE + "=second" + ATTRIBUTES));
        get(null).expectStatus().isEqualTo(502).expectHeader().doesNotExist("Set-Cookie");
    }

    /** Cookie 合法可打印字符必须按原值保留，不能被重写为宽松或不同的身份值。 */
    @Test
    void test_responseOpaqueCookieOctetsArePreserved() {
        publish("DROP", "ISSUE_ONLY");
        String value = "!#$%&'()*+-./09:<=?AZ[]^_`az{|}~";
        respond(BffScenarioStore.Response.json(200, "{}").withHeader("Set-Cookie", COOKIE + "=" + value + ATTRIBUTES));
        get(null).expectStatus().isOk().expectHeader().valueEquals("Set-Cookie", COOKIE + "=" + value + ATTRIBUTES);
    }

    /** 通过控制面 HTTP 发布完整 Cookie 场景配置并等待刷新。 */
    private void publish(String requestCookie, String responseCookie) {
        var source = GatewaySnapshotFixture.operationsSnapshot();
        var route = new GatewayRouteSnapshot("configured-cookie", "OPERATIONS", "GET", PATH, "/cookie-contract",
            "operations-console-bff", "PUBLIC", "PUBLIC", null, null, requestCookie, responseCookie,
            List.of(), List.of("origin", "x-optional"), List.of(), 0, 5_000, 1_000,
            List.of(new GatewayGuardRuleSnapshot("RATE_LIMIT", "SHARED_ROUTE", 300L, 300L, 60_000L, null),
                new GatewayGuardRuleSnapshot("CONCURRENCY", "SHARED_ROUTE", null, null, null, 20L)));
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(new GatewayPublicSnapshotResponseBody(1, source.getTargets(), List.of(route)));
        assertTrue(refreshSnapshot());
    }

    /** 声明下游本次应收到的精确请求及协议响应。 */
    private void respond(BffScenarioStore.Response response) {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.GET, "/cookie-contract", response);
    }

    /** 经真实公网过滤链发送仅含合成身份材料的 HTTP 请求。 */
    private WebTestClient.ResponseSpec get(String cookie) {
        var request = GatewayPublicTestClient.operations(port).defaultHeader("X-Real-IP", "203.0.113.17")
            .defaultHeader("Origin", "https://operations-console.test").responseTimeout(Duration.ofSeconds(10))
            .build().get().uri(PATH);
        if (cookie != null) request.header("Cookie", cookie);
        return request.exchange();
    }
}
