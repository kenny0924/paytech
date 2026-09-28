package com.paytech.gateway.auto.web;

import com.paytech.gateway.routing.ClientRoutingProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.routing.GatewayRouteConfiguration;
import org.springframework.cloud.gateway.config.GatewayProperties;
import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.RefreshCookieResponsePolicy;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.web.*;

import reactor.core.publisher.Mono;

/** HTTP 仅供显式 test localhost 部署；真实请求/响应边界共用精确 Cookie 模式。 @author Codex */
class OperationsHttpTestModeAutoTest {
    private static final String HTTP_ORIGIN = "http://localhost:28080";
    private static final String HTTP_COOKIE = "operations-refresh-test";
    private static final String HTTPS_COOKIE = "__Secure-operations-refresh";
    private static final String ATTRIBUTES = "; Path=/api/operations/auth/v1; Max-Age=3600; HttpOnly; SameSite=Strict";
    private static final OperationsRoutingProperties HTTP =
        new OperationsRoutingProperties(HTTP_ORIGIN, Duration.ofSeconds(1), true);
    private static final OperationsRoutingProperties HTTPS =
        new OperationsRoutingProperties("https://operations-console.test", Duration.ofSeconds(1));

    private static Stream<List<String>> invalidProfiles() {
        return Stream.of(List.of(), List.of("prod"), List.of("local"), List.of("auto"), List.of("TEST"),
            List.of("test", "prod"), List.of("test", "local"), List.of("test", "auto"));
    }

    private static Stream<String> invalidHttpResponseCookies() {
        String cookie = HTTP_COOKIE + "=opaque" + ATTRIBUTES;
        return Stream.of(cookie + "; Secure", cookie + "; Domain=localhost", cookie + "; Expires=ignored",
            cookie + "; HttpOnly", cookie.replace("; HttpOnly", ""), cookie.replace("SameSite=Strict", "SameSite=Lax"),
            cookie.replace("Path=/api/operations/auth/v1", "Path=/"), cookie.replace(HTTP_COOKIE, HTTPS_COOKIE),
            cookie.replace("Max-Age=3600", "Max-Age=28801"));
    }

    @Test
    void test_propertyBindingRequiresExplicitFlagAndRetainsHttpsDefault() {
        var properties =
            bind(Map.of("console-origin", HTTP_ORIGIN, "response-timeout", "PT1S", "test-http-enabled", "true"));
        Assertions.assertTrue(properties.testHttpEnabled());
        Assertions
            .assertFalse(bind(Map.of("console-origin", "https://operations-console.test", "response-timeout", "PT1S"))
                .testHttpEnabled());
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new OperationsRoutingProperties(HTTP_ORIGIN, Duration.ofSeconds(1)));
    }

    @Test
    void test_onlySingleTestProfileCanEnableHttp() {
        Assertions.assertDoesNotThrow(() -> new GatewayRouteConfiguration(HTTP,
            new MockEnvironment().withProperty("spring.profiles.active", "test"), new GatewayProperties()));
        Assertions.assertDoesNotThrow(() -> new GatewayRouteConfiguration(HTTPS,
            new MockEnvironment().withProperty("spring.profiles.active", "prod"), new GatewayProperties()));
    }

    @Test
    void test_perRequestRouteCannotEnableUnboundedFrameworkFilterCache() {
        var properties = new GatewayProperties();
        properties.setRouteFilterCacheEnabled(true);
        Assertions.assertThrows(IllegalStateException.class,
            () -> new GatewayRouteConfiguration(HTTPS, new MockEnvironment(), properties));
    }

    @ParameterizedTest
    @MethodSource("invalidProfiles")
    void test_httpFlagFailsStartupForOtherProfileSets(List<String> profiles) {
        var environment = new MockEnvironment();
        environment.setActiveProfiles(profiles.toArray(String[]::new));
        Assertions.assertThrows(IllegalStateException.class, () -> new GatewayRouteConfiguration(HTTP, environment, new GatewayProperties()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://localhost", "http://localhost:0", "http://localhost:65536",
        "http://localhost:028080", "http://127.0.0.1:28080", "http://[::1]:28080", "http://localhost.evil:28080",
        "http://LOCALHOST:28080", "https://localhost:28080", "http://localhost:28080/", "http://localhost:28080?x=1",
        "http://localhost:28080#x", "http://user@localhost:28080", "http://localhost:28080/path"})
    void test_httpFlagRejectsEveryOriginOutsideExactLocalhostPort(String origin) {
        Assertions.assertThrows(IllegalArgumentException.class,
            () -> new OperationsRoutingProperties(origin, Duration.ofSeconds(1), true));
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 28080, 65535})
    void test_httpAcceptsOnlyValidExplicitPort(int port) {
        Assertions.assertDoesNotThrow(
            () -> new OperationsRoutingProperties("http://localhost:" + port, Duration.ofSeconds(1), true));
    }

    @Test
    void test_httpWriteStillRequiresExactOrigin() {
        for (String origin : List.of(HTTP_ORIGIN, "http://localhost:28081", "https://localhost:28080", "")) {
            var exchange = exchange(origin, HTTP_COOKIE + "=opaque");
            new ConsoleBoundaryWebFilter(HTTP, errorWriter(), new ClientRoutingProperties(false, null)).filter(exchange, ignored -> Mono.empty()).block();
            Assertions.assertEquals(origin.equals(HTTP_ORIGIN) ? null : HttpStatus.FORBIDDEN,
                exchange.getResponse().getStatusCode());
        }
    }

    @Test
    void test_httpRequestsDropAllCookies() {
        var exchange = exchange(HTTP_ORIGIN, HTTPS_COOKIE + "=old; unrelated=value; " + HTTP_COOKIE + "=opaque");
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();
        new UpstreamRequestGlobalFilter(errorWriter(), new ConsoleRefreshCookiePolicy(HTTP))
            .filter(exchange, next -> {
                forwarded.set(next);
                return Mono.empty();
            }).block();
        Assertions.assertFalse(forwarded.get().getRequest().getHeaders().containsHeader(HttpHeaders.COOKIE));
    }

    @ParameterizedTest
    @ValueSource(strings = {"__Secure-operations-refresh=legacy", "operations-refresh-test=", "operations-refresh-test",
        "operations-refresh-test=one; operations-refresh-test=two", "operations-refresh-test=not opaque"})
    void test_httpRequestsIgnoreMalformedLegacyCookie(String cookie) {
        var exchange = exchange(HTTP_ORIGIN, cookie);
        new UpstreamRequestGlobalFilter(errorWriter(), new ConsoleRefreshCookiePolicy(HTTP))
            .filter(exchange, forwarded -> {
                Assertions.assertFalse(forwarded.getRequest().getHeaders().containsHeader(HttpHeaders.COOKIE));
                return Mono.empty();
            }).block();
        Assertions.assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void test_httpsRequestsDropHttpTestCookie() {
        var exchange = exchange(HTTPS.consoleOrigin(), HTTP_COOKIE + "=opaque");
        new UpstreamRequestGlobalFilter(errorWriter(), new ConsoleRefreshCookiePolicy(HTTPS))
            .filter(exchange, forwarded -> {
                Assertions.assertFalse(forwarded.getRequest().getHeaders().containsHeader(HttpHeaders.COOKIE));
                return Mono.empty();
            }).block();
        Assertions.assertNull(exchange.getResponse().getStatusCode());
    }

    @Test
    void test_sharedCookiePolicyStillSupportsExplicitHttpCookieModes() {
        var policy = new ConsoleRefreshCookiePolicy(HTTP);
        String issued = HTTP_COOKIE + "=opaque" + ATTRIBUTES;
        Assertions.assertEquals(issued, normalize(policy, issued, RefreshCookieResponsePolicy.ISSUE_ONLY));
        String cleared = HTTP_COOKIE + "=" + ATTRIBUTES.replace("Max-Age=3600", "Max-Age=0");
        Assertions.assertEquals(cleared, normalize(policy, cleared, RefreshCookieResponsePolicy.CLEAR_ONLY));
        Assertions.assertThrows(InvalidUpstreamResponseException.class,
            () -> normalize(policy, cleared, RefreshCookieResponsePolicy.ISSUE_ONLY));
        Assertions.assertThrows(InvalidUpstreamResponseException.class,
            () -> normalize(policy, issued, RefreshCookieResponsePolicy.CLEAR_ONLY));
    }

    @ParameterizedTest
    @MethodSource("invalidHttpResponseCookies")
    void test_sharedCookiePolicyStillRejectsInvalidHttpCookie(String cookie) {
        Assertions.assertThrows(InvalidUpstreamResponseException.class,
            () -> normalize(new ConsoleRefreshCookiePolicy(HTTP), cookie, RefreshCookieResponsePolicy.ISSUE_ONLY));
    }

    @Test
    void test_sharedCookiePolicyStillRequiresSecureInHttpsMode() {
        var policy = new ConsoleRefreshCookiePolicy(HTTPS);
        String cookie = HTTPS_COOKIE + "=opaque" + ATTRIBUTES.replace("; HttpOnly", "; Secure; HttpOnly");
        Assertions.assertEquals(cookie, normalize(policy, cookie, RefreshCookieResponsePolicy.ISSUE_ONLY));
        Assertions.assertThrows(InvalidUpstreamResponseException.class,
            () -> normalize(policy, HTTPS_COOKIE + "=opaque" + ATTRIBUTES, RefreshCookieResponsePolicy.ISSUE_ONLY));
        Assertions.assertThrows(InvalidUpstreamResponseException.class,
            () -> normalize(policy, cookie.replace(HTTPS_COOKIE, HTTP_COOKIE), RefreshCookieResponsePolicy.ISSUE_ONLY));
    }

    private OperationsRoutingProperties bind(Map<String, String> values) {
        var source = new MapConfigurationPropertySource();
        values.forEach((key, value) -> source.put("gateway.operations." + key, value));
        return new Binder(source).bind("gateway.operations", Bindable.of(OperationsRoutingProperties.class)).get();
    }

    private String normalize(ConsoleRefreshCookiePolicy policy, String value, RefreshCookieResponsePolicy mode) {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.SET_COOKIE, value);
        policy.validateAndRewrite(headers, mode, com.paytech.platform.support.api.enums.GatewayRouteChannel.OPERATIONS);
        return headers.getFirst(HttpHeaders.SET_COOKIE);
    }

    private MockServerWebExchange exchange(String origin, String cookie) {
        GatewayRuntimeRoute route = new GatewaySnapshotCompiler()
            .compile(GatewaySnapshotFixture.operationsSnapshot()).catalog().all().stream()
            .filter(value -> value.routeCode().equals("operations-auth-refresh")).findFirst().orElseThrow();
        var exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath())
            .header(HttpHeaders.ORIGIN, origin).header(HttpHeaders.COOKIE, cookie));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        exchange.getAttributes().put(GatewayExchangeAttributes.DOWNSTREAM_PATH, route.downstreamPath());
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "test-request");
        exchange.getAttributes().put(GatewayExchangeAttributes.ACTUAL_REQUEST_BODY_LENGTH, 0L);
        return exchange;
    }

    private GatewayErrorWriter errorWriter() {
        return new GatewayErrorWriter(ServerCodecConfigurer.create());
    }
}
