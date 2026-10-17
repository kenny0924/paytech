package com.paytech.gateway.auto.web;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.List;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.cloud.gateway.filter.NettyRoutingFilter;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.nimbusds.jwt.SignedJWT;
import com.paytech.gateway.security.ReactiveJwksProvider;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;
import com.paytech.gateway.web.GatewayResponseContractGlobalFilter;

/** Operations BFF 响应 Cookie 与终端 Header 契约的真实协议测试。 */
class OperationsResponseContractAutoTest extends GatewayAutoTestBase {
    private static final String ORIGIN = "https://operations-console.test";
    private static final String COOKIE_NAME = "__Secure-operations-refresh";
    private static final String COOKIE_PATH = OperationsTestPaths.AUTH_COOKIE_PATH;
    private static final String REQUEST_COOKIE = COOKIE_NAME + "=current-refresh";
    private static final String POSITIVE_COOKIE =
        COOKIE_NAME + "=next-refresh; Path=" + COOKIE_PATH + "; Max-Age=3600; Secure; HttpOnly; SameSite=Strict";
    private static final String CLEAR_COOKIE =
        COOKIE_NAME + "=; Path=" + COOKIE_PATH + "; Max-Age=0; Secure; HttpOnly; SameSite=Strict";
    private static final String CACHED_KID = "shared-regression-key";

    @LocalServerPort
    private int port;

    @Autowired
    private GatewayResponseContractGlobalFilter responseContractFilter;

    @Autowired
    private ReactiveJwksProvider reactiveJwksProvider;

    @TestBean(name = "operationsJwtClock", methodName = "fixedClock", enforceOverride = true)
    private Clock operationsJwtClock;

    private static Clock fixedClock() {
        return Clock.fixed(JwtFixtureBuilder.NOW, ZoneOffset.UTC);
    }

    private static JwtFixtureBuilder jwt() {
        return JwtFixtureBuilder.template().keyId(CACHED_KID);
    }

    private static Stream<Arguments> acceptedCookieScenarios() {
        return Stream.of(
            Arguments.of("login issues positive cookie", "login", "/auth/v1/login",
                COOKIE_NAME + "=next-refresh; samesite=Strict; HTTPONLY; secure; max-age=3600; path=" + COOKIE_PATH,
                POSITIVE_COOKIE),
            Arguments.of("refresh rotates positive cookie", "refresh", "/auth/v1/refresh", POSITIVE_COOKIE,
                POSITIVE_COOKIE),
            Arguments.of("refresh clears zero-age cookie", "refresh", "/auth/v1/refresh", CLEAR_COOKIE, CLEAR_COOKIE),
            Arguments.of("refresh preserves valid quoted opaque cookie", "refresh", "/auth/v1/refresh",
                COOKIE_NAME + "=\"next!#$%&'()*+-.:<>?[]^_`{|}~\"; Path=" + COOKIE_PATH
                    + "; Max-Age=3600; Secure; HttpOnly; SameSite=Strict",
                COOKIE_NAME + "=\"next!#$%&'()*+-.:<>?[]^_`{|}~\"; Path=" + COOKIE_PATH
                    + "; Max-Age=3600; Secure; HttpOnly; SameSite=Strict"),
            Arguments.of("refresh clears quoted empty cookie", "refresh", "/auth/v1/refresh",
                COOKIE_NAME + "=\"\"; Path=" + COOKIE_PATH + "; Max-Age=0; Secure; HttpOnly; SameSite=Strict",
                COOKIE_NAME + "=\"\"; Path=" + COOKIE_PATH + "; Max-Age=0; Secure; HttpOnly; SameSite=Strict"),
            Arguments.of("logout clears zero-age cookie", "logout", "/auth/v1/logout", CLEAR_COOKIE, CLEAR_COOKIE));
    }

    private static Stream<Arguments> optionalCookieScenarios() {
        return Stream.of(Arguments.of("login omits cookie", "login", "/auth/v1/login"),
            Arguments.of("refresh omits cookie", "refresh", "/auth/v1/refresh"),
            Arguments.of("logout omits cookie", "logout", "/auth/v1/logout"));
    }

    private static Stream<Arguments> invalidCookieScenarios() {
        String base = COOKIE_NAME + "=next-refresh; Path=" + COOKIE_PATH + "; Max-Age=3600";
        return Stream.of(Arguments.of("login rejects zero age", "login", "/auth/v1/login", List.of(CLEAR_COOKIE)),
            Arguments.of("logout rejects positive age", "logout", "/auth/v1/logout", List.of(POSITIVE_COOKIE)),
            Arguments.of("refresh rejects negative age", "refresh", "/auth/v1/refresh",
                List.of(base.replace("3600", "-1") + "; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("login rejects age beyond session ceiling", "login", "/auth/v1/login",
                List.of(base.replace("3600", "28801") + "; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("duplicate target cookies", "refresh", "/auth/v1/refresh",
                List.of(POSITIVE_COOKIE, POSITIVE_COOKIE)),
            Arguments.of("foreign cookie", "login", "/auth/v1/login", List.of("session=attacker")),
            Arguments.of("cookie pair without separator", "login", "/auth/v1/login", List.of(COOKIE_NAME)),
            Arguments.of("foreign cookie beside target", "refresh", "/auth/v1/refresh",
                List.of(POSITIVE_COOKIE, "session=attacker")),
            Arguments.of("domain attribute", "login", "/auth/v1/login",
                List.of(base + "; Domain=example.test; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("unknown attribute", "login", "/auth/v1/login",
                List.of(base + "; Priority=High; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("duplicate attribute", "login", "/auth/v1/login",
                List.of(base + "; Path=" + COOKIE_PATH + "; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("wrong path", "login", "/auth/v1/login",
                List.of(COOKIE_NAME + "=next-refresh; Path=/; Max-Age=3600; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("missing path", "login", "/auth/v1/login",
                List.of(COOKIE_NAME + "=next-refresh; Max-Age=3600; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("missing max age", "login", "/auth/v1/login",
                List.of(COOKIE_NAME + "=next-refresh; Path=" + COOKIE_PATH + "; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("missing secure", "login", "/auth/v1/login", List.of(base + "; HttpOnly; SameSite=Strict")),
            Arguments.of("missing http only", "login", "/auth/v1/login", List.of(base + "; Secure; SameSite=Strict")),
            Arguments.of("missing same site", "login", "/auth/v1/login", List.of(base + "; Secure; HttpOnly")),
            Arguments.of("empty attribute segment", "login", "/auth/v1/login",
                List.of(base + "; Secure; HttpOnly; SameSite=Strict;")),
            Arguments.of("max age flag has no value", "login", "/auth/v1/login",
                List.of(COOKIE_NAME + "=next-refresh; Path=" + COOKIE_PATH
                    + "; Max-Age; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("max age value is empty", "login", "/auth/v1/login",
                List.of(COOKIE_NAME + "=next-refresh; Path=" + COOKIE_PATH
                    + "; Max-Age=; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("max age overflows long", "login", "/auth/v1/login",
                List.of(COOKIE_NAME + "=next-refresh; Path=" + COOKIE_PATH
                    + "; Max-Age=999999999999999999999999; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("wrong same site", "login", "/auth/v1/login",
                List.of(base + "; Secure; HttpOnly; SameSite=Lax")),
            Arguments.of("same site value is exact", "login", "/auth/v1/login",
                List.of(base + "; Secure; HttpOnly; SameSite=strict")),
            Arguments.of("wrong cookie name case", "login", "/auth/v1/login",
                List.of("__secure-operations-refresh=next; Path=" + COOKIE_PATH
                    + "; Max-Age=3600; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("invalid opaque value", "login", "/auth/v1/login",
                List.of(COOKIE_NAME + "=opaque value; Path=" + COOKIE_PATH
                    + "; Max-Age=3600; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("invalid quoted opaque value", "login", "/auth/v1/login",
                List.of(COOKIE_NAME + "=\"opaque; Path=" + COOKIE_PATH
                    + "; Max-Age=3600; Secure; HttpOnly; SameSite=Strict")),
            Arguments.of("empty positive value", "login", "/auth/v1/login",
                List.of(COOKIE_NAME + "=; Path=" + COOKIE_PATH + "; Max-Age=3600; Secure; HttpOnly; SameSite=Strict")));
    }

    @BeforeEach
    void prewarmJwksCacheThroughRealHttpProvider() {
        jwksSimulator().scenarios().respondWithJwks(JwtFixtureBuilder.jwksJson(CACHED_KID));
        try {
            reactiveJwksProvider.select(SignedJWT.parse(jwt().build())).single().block(Duration.ofSeconds(1));
        } catch (java.text.ParseException exception) {
            throw new IllegalStateException("无法解析 JWKS 预热 JWT", exception);
        }
        jwksSimulator().reset();
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedCookieScenarios")
    void test_operationsRejectsEvenPreviouslyValidRefreshCookie(String scenario, String route, String bffPath,
        String upstreamCookie, String expectedCookie) {
        expect(bffPath, BffScenarioStore.Response.json(200, "{\"accepted\":true}").withHeader(HttpHeaders.SET_COOKIE,
            upstreamCookie));

        assertProblem(request(route).exchange().expectHeader().doesNotExist(HttpHeaders.SET_COOKIE), 502,
            "UPSTREAM_PROTOCOL_ERROR");

        verifyJwks(route);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("optionalCookieScenarios")
    void test_routePolicyAllowsBffToOmitRefreshCookie(String scenario, String route, String bffPath) {
        expect(bffPath, BffScenarioStore.Response.json(200, "{\"accepted\":true}"));

        EntityExchangeResult<byte[]> result = request(route).exchange().expectStatus().isOk().expectHeader()
            .doesNotExist(HttpHeaders.SET_COOKIE).expectBody().json("{\"accepted\":true}").returnResult();
        verifyJwks(route);
        assertTerminalHeaders(result.getResponseHeaders());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidCookieScenarios")
    void test_invalidUpstreamCookieReturnsLocal502BeforeCommit(String scenario, String route, String bffPath,
        List<String> upstreamCookies) {
        expect(bffPath, BffScenarioStore.Response.json(422, "UPSTREAM_BODY_CANARY").withHeaders(HttpHeaders.SET_COOKIE,
            upstreamCookies.toArray(String[]::new)));

        EntityExchangeResult<byte[]> result = request(route).exchange().expectStatus().isEqualTo(502).expectHeader()
            .doesNotExist(HttpHeaders.SET_COOKIE).expectHeader().valueEquals(HttpHeaders.CACHE_CONTROL, "no-store")
            .expectHeader().valueEquals(HttpHeaders.PRAGMA, "no-cache").expectHeader()
            .valueEquals(HttpHeaders.VARY, HttpHeaders.ORIGIN).expectBody().returnResult();
        assertProblem(result, 502, "UPSTREAM_PROTOCOL_ERROR");

        verifyJwks(route);
        assertTerminalHeaders(result.getResponseHeaders());
        Assertions.assertFalse(new String(result.getResponseBody(), java.nio.charset.StandardCharsets.UTF_8)
            .contains("UPSTREAM_BODY_CANARY"));
    }

    @org.junit.jupiter.api.Test
    void test_responseContractRunsAfterRoutingAndBeforeNettyBodyWrite() {
        Assertions.assertAll(
            () -> Assertions.assertEquals(GatewayGlobalFilterOrder.OPERATIONS_RESPONSE_CONTRACT,
                responseContractFilter.getOrder()),
            () -> Assertions
                .assertTrue(responseContractFilter.getOrder() > NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER),
            () -> Assertions.assertTrue(responseContractFilter.getOrder() < NettyRoutingFilter.ORDER));
    }

    private WebTestClient.RequestHeadersSpec<?> request(String route) {
        WebTestClient.RequestBodySpec request = gatewayClient().post().uri("/api/operations/auth/v1/" + route)
            .header(HttpHeaders.ORIGIN, ORIGIN).contentType(MediaType.APPLICATION_JSON);
        if ("refresh".equals(route)) {
            request.header(HttpHeaders.COOKIE, REQUEST_COOKIE);
        }
        if ("logout".equals(route)) {
            JwtFixtureBuilder jwt = jwt();
            request.header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.build()).header(HttpHeaders.COOKIE,
                REQUEST_COOKIE);
        }
        return request.bodyValue("{\"refreshToken\":\"json-refresh\"}");
    }

    private void expect(String path, BffScenarioStore.Response response) {
        simulator().scenarios().expect(HttpMethod.POST, path, response);
    }

    private WebTestClient gatewayClient() {
        return GatewayPublicTestClient.operations(port)
            .defaultHeader("X-Real-IP", "203.0.113.17").build();
    }

    private com.paytech.gateway.test.simulator.bff.OperationsBffSimulator simulator() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }

    private com.paytech.gateway.test.simulator.jwks.JwksSimulator jwksSimulator() {
        return GatewayAutoTestRuntime.jwksSimulator();
    }

    private void verifyJwks(String route) {
        jwksSimulator().journal().verifyNoRequests();
    }

    private void assertTerminalHeaders(HttpHeaders headers) {
        Assertions.assertAll(
            () -> Assertions.assertEquals(List.of("no-store"), headers.getOrEmpty(HttpHeaders.CACHE_CONTROL)),
            () -> Assertions.assertEquals(List.of("no-cache"), headers.getOrEmpty(HttpHeaders.PRAGMA)),
            () -> Assertions.assertEquals(List.of(HttpHeaders.ORIGIN), headers.getOrEmpty(HttpHeaders.VARY)),
            () -> Assertions.assertEquals(List.of(ORIGIN), headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN)),
            () -> Assertions.assertEquals(List.of("true"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS)),
            () -> Assertions.assertEquals(List.of("POST, OPTIONS"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS)),
            () -> Assertions.assertEquals(List.of("X-Request-Id, Retry-After"),
                headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS)));
    }
}
