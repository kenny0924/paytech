package com.paytech.gateway.auto.web;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.platform.support.api.gatewayconfiguration.response.*;

/** CLIENT 认证入口经真实 Gateway HTTP、共享 Redis 和严格下游协议 Simulator。 */
@TestPropertySource(properties = "gateway.client.enabled=true")
class ClientConsoleGatewayHttpAutoTest extends GatewayAutoTestBase {
    private static final String ORIGIN = "https://client-console.test";
    private static final String PREFIX = "/api/client/auth/v1/";
    private static final String COOKIE = "__Secure-client-refresh";
    private static final String ATTRIBUTES = "; Path=/api/client/auth/v1; Max-Age=604800; Secure; HttpOnly; SameSite=Lax";
    @LocalServerPort private int port;

    /** 登录签发七天 CLIENT Cookie；Refresh 仅转发一个客户 Cookie，清除使用相同 Path。 */
    @Test
    void test_loginRefreshAndClearUseClientCookieContract() {
        publish(route("login", "POST", "PUBLIC", "DROP", "ISSUE_ONLY"),
            route("refresh", "POST", "PUBLIC", "REQUIRE_PROFILE_COOKIE", "ISSUE_OR_CLEAR"));
        respond("login", BffScenarioStore.Response.json(200, "{\"accessToken\":\"synthetic-access\"}")
            .withHeader("Set-Cookie", COOKIE + "=synthetic-refresh" + ATTRIBUTES));
        post("login", ORIGIN, null).expectStatus().isOk().expectHeader()
            .valueEquals("Set-Cookie", COOKIE + "=synthetic-refresh" + ATTRIBUTES)
            .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN).expectHeader().valueEquals("Cache-Control", "no-store").expectBody().jsonPath("accessToken").isEqualTo("synthetic-access");

        respond("refresh", BffScenarioStore.Response.json(200, "{}")
            .withHeader("Set-Cookie", COOKIE + "=rotated-refresh" + ATTRIBUTES));
        post("refresh", ORIGIN, "__Secure-operations-refresh=drop; " + COOKIE + "=synthetic-refresh; other=drop")
            .expectStatus().isOk().expectHeader().valueEquals("Set-Cookie", COOKIE + "=rotated-refresh" + ATTRIBUTES)
            .expectBody().json("{}");
        var forwarded = GatewayAutoTestRuntime.operationsBffSimulator().journal().entries().get(1).headers();
        assertEquals(List.of(COOKIE + "=synthetic-refresh"), header(forwarded, "cookie"));
        assertNull(header(forwarded, "authorization"));

        respond("refresh", BffScenarioStore.Response.json(401, """
            {"type":"about:blank","title":"Unauthorized","detail":"Session invalid","code":"CLIENT_SESSION_INVALID",
             "requestId":"%s","timestamp":"2026-09-15T08:00:00.000000Z"}
            """.formatted(BffScenarioStore.REQUEST_ID_TOKEN)).withContentTypes("application/problem+json")
            .withHeader("X-Request-Id", BffScenarioStore.REQUEST_ID_TOKEN).reflectingRequestId()
            .withHeader("Set-Cookie", COOKIE + "=" + ATTRIBUTES.replace("604800", "0")));
        post("refresh", ORIGIN, COOKIE + "=rotated-refresh").expectStatus().isUnauthorized()
            .expectHeader().valueEquals("Set-Cookie", COOKIE + "=" + ATTRIBUTES.replace("604800", "0"))
            .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN).expectBody().consumeWith(result -> assertNotNull(result.getResponseBody()));
    }

    /** 公开写入要求精确客户 Origin，不能接受 Operations 或任意外部站点。 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"https://operations-console.test", "https://untrusted.test", "null", "https://client-console.test/"})
    void test_untrustedWriteOriginNeverReachesBff(String origin) {
        publish(route("register", "POST", "PUBLIC", "DROP", "NONE"));
        post("register", origin, null).expectStatus().isForbidden()
            .expectHeader().doesNotExist("Access-Control-Allow-Origin").expectBody().jsonPath("code").isEqualTo("GATEWAY_FORBIDDEN");
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    /** 预检在 Gateway 终止，按当前 Route 白名单允许 Header，不访问身份或 BFF。 */
    @Test
    void test_preflightAllowsOnlyClientOriginAndConfiguredHeaders() {
        publish(route("register", "POST", "PUBLIC", "DROP", "NONE"));
        client().options().uri(PREFIX + "register").header("Origin", ORIGIN)
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "content-type")
            .exchange().expectStatus().isNoContent().expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN)
            .expectHeader().valueEquals("Access-Control-Allow-Credentials", "true").expectHeader().valueEquals("Vary", "Origin").expectBody().isEmpty();
        client().options().uri(PREFIX + "register").header("Origin", ORIGIN)
            .header("Access-Control-Request-Method", "POST").header("Access-Control-Request-Headers", "x-gateway-actor-subject")
            .exchange().expectStatus().isForbidden().expectHeader().doesNotExist("Access-Control-Allow-Origin").expectBody().jsonPath("code").isEqualTo("GATEWAY_FORBIDDEN");
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    /** 不可缺失或混用 Operations Cookie；重复客户 Cookie 不能选择性接受。 */
    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"__Secure-operations-refresh=wrong-channel", "__Secure-client-refresh=",
        "__Secure-client-refresh=first; __Secure-client-refresh=second", "__Secure-client-refresh=has space"})
    void test_invalidRefreshCookieNeverReachesBff(String cookie) {
        publish(route("refresh", "POST", "PUBLIC", "REQUIRE_PROFILE_COOKIE", "ISSUE_OR_CLEAR"));
        post("refresh", ORIGIN, cookie).expectStatus().isUnauthorized().expectBody().jsonPath("code").isEqualTo("GATEWAY_UNAUTHORIZED");
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    /** 下游不能扩大 Cookie 的域、路径、有效期或改变客户专属 Cookie 属性。 */
    @ParameterizedTest
    @MethodSource("invalidCookies")
    void test_invalidUpstreamCookieIsRejected(String cookie) {
        publish(route("login", "POST", "PUBLIC", "DROP", "ISSUE_ONLY"));
        respond("login", BffScenarioStore.Response.json(200, "{}").withHeader("Set-Cookie", cookie));
        post("login", ORIGIN, null).expectStatus().isEqualTo(502).expectHeader().doesNotExist("Set-Cookie")
            .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN).expectBody().consumeWith(result -> assertNotNull(result.getResponseBody()));
    }

    private static Stream<String> invalidCookies() {
        String valid = COOKIE + "=synthetic" + ATTRIBUTES;
        return Stream.of(valid.replace(COOKIE, "__Secure-operations-refresh"), valid.replace("Lax", "Strict"),
            valid.replace("/api/client/auth/v1", "/"), valid.replace("604800", "604801"),
            valid + "; Domain=client-console.test", valid.replace("; Secure", ""), valid + "; HttpOnly");
    }

    /** 共享 Redis 真正执行客户 IP 限流，第二次请求不会调用 BFF。 */
    @Test
    void test_publicRegistrationIsLimitedByClientIpInRedis() {
        var source = route("register", "POST", "PUBLIC", "DROP", "NONE");
        var guards = source.getGuardRules().stream().map(rule -> "CLIENT_IP".equals(rule.getDimension())
            && "RATE_LIMIT".equals(rule.getRuleType())
                ? new GatewayGuardRuleSnapshot("RATE_LIMIT", "CLIENT_IP", 1L, 1L, 60_000L, null) : rule).toList();
        publish(new GatewayRouteSnapshot(source.getRouteCode(), source.getChannel(), source.getHttpMethod(),
            source.getExternalPathTemplate(), source.getDownstreamPathTemplate(), source.getTargetServiceCode(),
            source.getSecurityProfileCode(), source.getAuthorizationMode(), null, null, source.getRequestCookieMode(),
            source.getResponseCookieMode(), source.getCorsRequestHeaderNames(), source.getForwardedRequestHeaderNames(),
            source.getQueryParameterNames(), source.getBodyLimitBytes(), source.getBodyReceiveTimeoutMillis(),
            source.getResponseTimeoutMillis(), guards));
        respond("register", BffScenarioStore.Response.json(202, "{\"accepted\":true}"));
        post("register", ORIGIN, null).expectStatus().isAccepted().expectBody().jsonPath("accepted").isEqualTo(true);
        post("register", ORIGIN, null).expectStatus().isEqualTo(429).expectHeader().exists("Retry-After")
            .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN).expectBody().jsonPath("code").exists();
        assertEquals(1, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
    }

    /** 缺少 Access Token 和超过 Header 预算均在 BFF 之前拒绝，可信客户 Origin 的错误仍可被页面读取。 */
    @Test
    void test_authenticationAndEarlyHeaderErrorsKeepClientCors() {
        publish(route("context", "GET", "CLIENT_JWT", "DROP", "NONE"));
        client().get().uri(PREFIX + "context").header("Origin", ORIGIN).exchange().expectStatus().isUnauthorized()
            .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN).expectBody().jsonPath("code").isEqualTo("GATEWAY_UNAUTHORIZED");
        client().get().uri(PREFIX + "context").header("Origin", ORIGIN)
            .header("Authorization", "Bearer " + "a".repeat(8200)).exchange().expectStatus().isEqualTo(431)
            .expectHeader().valueEquals("Access-Control-Allow-Origin", ORIGIN).expectBody().jsonPath("code")
            .isEqualTo("GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");
        assertEquals(0, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    /** 客户 JWT 经独立 CUSTOMER JWKS 验签后重建 ACCOUNT 身份，伪造 Header 与跨 Realm Token 均不能越权。 */
    @Test
    void test_customerAccountContextAndLogoutRebuildTrustedIdentity() {
        publish(route("context", "GET", "CLIENT_JWT", "DROP", "NONE"),
            route("logout", "POST", "CLIENT_JWT", "DROP", "CLEAR_ONLY"));
        String kid = "customer-http-" + UUID.randomUUID();
        var jwt = accountJwt(kid);
        GatewayAutoTestRuntime.jwksSimulator().scenarios().expectCustomerJwks(jwt.jwksJson());
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.GET, "/client-auth/v1/context",
            BffScenarioStore.Response.json(200, "{\"accountId\":\"101\"}"));
        client().get().uri(PREFIX + "context").header("Authorization", "Bearer " + jwt.build())
            .header("X-Gateway-Actor-Subject", "forged").header("X-Gateway-Tenant-Id", "forged")
            .exchange().expectStatus().isOk().expectBody().jsonPath("accountId").isEqualTo("101");
        var headers = GatewayAutoTestRuntime.operationsBffSimulator().journal().entries().getFirst().headers();
        assertEquals(List.of("101"), header(headers, "x-gateway-actor-subject"));
        assertEquals(List.of("201"), header(headers, "x-gateway-session-id"));
        assertEquals(List.of("CUSTOMER"), header(headers, "x-gateway-realm"));
        assertEquals(List.of("CLIENT"), header(headers, "x-gateway-channel"));
        assertEquals(List.of("ACCOUNT"), header(headers, "x-gateway-context-type"));
        assertNull(header(headers, "x-gateway-tenant-id"));
        assertNull(header(headers, "authorization"));
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyCustomerGets(1);

        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.GET, "/client-auth/v1/context",
            BffScenarioStore.Response.json(200, "{\"accountId\":\"101\"}"));
        client().get().uri(PREFIX + "context").header("Authorization", "Bearer " + accountJwt(kid)
            .claim("context_type", "TENANT").claim("tenant_type", "CLIENT")
            .claim("tenant_id", "4").claim("tenant_member_id", "5").build())
            .header("X-Gateway-Tenant-Id", "forged").exchange().expectStatus().isOk().expectBody().jsonPath("accountId").isEqualTo("101");
        var tenantHeaders = GatewayAutoTestRuntime.operationsBffSimulator().journal().entries().get(1).headers();
        assertEquals(List.of("4"), header(tenantHeaders, "x-gateway-tenant-id"));
        assertEquals(List.of("5"), header(tenantHeaders, "x-gateway-tenant-member-id"));
        assertEquals(List.of("CLIENT"), header(tenantHeaders, "x-gateway-tenant-type"));
        assertEquals(List.of("TENANT"), header(tenantHeaders, "x-gateway-context-type"));

        client().get().uri(PREFIX + "context").header("Authorization", "Bearer " + accountJwt(kid)
            .claim("realm", "OPERATIONS").claim("channel", "OPERATIONS").build()).exchange().expectStatus().isUnauthorized().expectBody().jsonPath("code").isEqualTo("GATEWAY_UNAUTHORIZED");
        assertEquals(2, GatewayAutoTestRuntime.operationsBffSimulator().journal().count());
        respond("logout", BffScenarioStore.Response.json(204, "")
            .withHeader("Set-Cookie", COOKIE + "=" + ATTRIBUTES.replace("604800", "0")));
        client().post().uri(PREFIX + "logout").header("Origin", ORIGIN).header("Content-Type", "application/json")
            .header("Authorization", "Bearer " + jwt.build()).bodyValue("{}").exchange().expectStatus().isNoContent()
            .expectHeader().valueEquals("Set-Cookie", COOKIE + "=" + ATTRIBUTES.replace("604800", "0")).expectBody().isEmpty();
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyCustomerGets(1);
    }

    private JwtFixtureBuilder accountJwt(String kid) {
        Instant now = Instant.now();
        return JwtFixtureBuilder.template().keyId(kid).claim("iss", "https://customer.platform-support.local.test")
            .claim("aud", List.of("client-console")).claim("sub", "101").claim("sid", "201").claim("jti", "301")
            .claim("realm", "CUSTOMER").claim("channel", "CLIENT").claim("context_type", "ACCOUNT")
            .withoutClaim("tenant_id").withoutClaim("tenant_type").withoutClaim("tenant_member_id")
            .times(now.minusSeconds(1), now.minusSeconds(1), now.plusSeconds(600));
    }

    private GatewayRouteSnapshot route(String action, String method, String profile, String requestCookie, String responseCookie) {
        var guards = new ArrayList<GatewayGuardRuleSnapshot>();
        for (String dimension : "PUBLIC".equals(profile) ? List.of("SHARED_ROUTE", "CLIENT_IP")
            : List.of("SHARED_ROUTE", "CLIENT_IP", "AUTHENTICATED_PRINCIPAL")) {
            guards.add(new GatewayGuardRuleSnapshot("RATE_LIMIT", dimension, 300L, 300L, 60_000L, null));
            guards.add(new GatewayGuardRuleSnapshot("CONCURRENCY", dimension, null, null, null, 20L));
        }
        return new GatewayRouteSnapshot("client-" + action, "CLIENT", method, PREFIX + action, "/client-auth/v1/" + action,
            "client-console-bff", profile, "PUBLIC".equals(profile) ? "PUBLIC" : "HUMAN_AUTHENTICATED", null, null,
            requestCookie, responseCookie, List.of("content-type", "origin", "authorization"),
            List.of("content-type", "origin"), List.of(), "GET".equals(method) ? 0 : 4096, 5000, 1000, guards);
    }

    private void publish(GatewayRouteSnapshot... routes) {
        GatewayAutoTestRuntime.platformSnapshotSimulator().publish(new GatewayPublicSnapshotResponseBody(1,
            List.of(new GatewayTargetSnapshot("client-console-bff", "CLIENT", GatewayAutoTestRuntime.FIRST_DYNAMIC_SERVICE)),
            List.of(routes)));
        assertTrue(refreshSnapshot());
    }

    private void respond(String action, BffScenarioStore.Response response) {
        GatewayAutoTestRuntime.operationsBffSimulator().scenarios().expect(HttpMethod.POST, "/client-auth/v1/" + action, response);
    }

    private WebTestClient client() {
        return GatewayPublicTestClient.builder(port, List.of("CLIENT")).defaultHeader("X-Real-IP", "203.0.113.19")
            .responseTimeout(Duration.ofSeconds(10)).build();
    }

    private WebTestClient.ResponseSpec post(String action, String origin, String cookie) {
        var request = client().post().uri(PREFIX + action).header("Content-Type", "application/json");
        if (origin != null) request.header("Origin", origin);
        if (cookie != null) request.header("Cookie", cookie);
        return request.bodyValue("{}").exchange();
    }

    private List<String> header(java.util.Map<String, List<String>> headers, String name) {
        return headers.entrySet().stream().filter(entry -> entry.getKey().equalsIgnoreCase(name))
            .map(java.util.Map.Entry::getValue).findFirst().orElse(null);
    }
}
