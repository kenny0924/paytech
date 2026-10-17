package com.paytech.gateway.auto.security;

import java.time.Duration;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.ReactiveSecurityContextHolder;
import org.springframework.security.core.context.SecurityContextImpl;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.snapshot.GatewaySnapshotCompiler;
import com.paytech.gateway.security.GatewayHmacProperties;
import com.paytech.gateway.security.VersionedHmacDigester;
import com.paytech.gateway.security.identity.GatewayAuthenticatedIdentityGlobalFilter;
import com.paytech.gateway.security.identity.OperationsIdentity;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.web.*;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayPublicSnapshotResponseBody;
import com.paytech.platform.support.api.gatewayconfiguration.response.GatewayRouteSnapshot;

import reactor.core.publisher.Mono;

/** 直接执行生产 Header Filter 链，验证管理请求不会把浏览器材料变成可信上下文。 */
class OperationsTrustedContextAutoTest {
    private static final List<String> TENANT_HEADERS =
        List.of(GatewayProtocol.TENANT_ID, GatewayProtocol.TENANT_TYPE, GatewayProtocol.TENANT_MEMBER_ID);

    @Test
    void test_tenantManagementWriteUsesPinnedRouteAndValidatedIdentity() {
        ServerWebExchange forwarded = forward(token("TENANT"), true);
        HttpHeaders headers = forwarded.getRequest().getHeaders();

        Assertions.assertEquals("/iam/v1/accounts", forwarded.getRequest().getPath().value());
        Assertions.assertEquals("operations-account-create", headers.getFirst(GatewayProtocol.ROUTE_CODE));
        Assertions.assertEquals("gateway-request-id", headers.getFirst(GatewayProtocol.REQUEST_ID));
        Assertions.assertEquals(JwtFixtureBuilder.SUBJECT, headers.getFirst(GatewayProtocol.ACTOR_SUBJECT));
        Assertions.assertEquals(JwtFixtureBuilder.SESSION_ID, headers.getFirst(GatewayProtocol.SESSION_ID));
        Assertions.assertEquals("PLATFORM_USER", headers.getFirst(GatewayProtocol.ACTOR_TYPE));
        Assertions.assertEquals("OPERATIONS", headers.getFirst(GatewayProtocol.CHANNEL));
        Assertions.assertEquals("OPERATIONS", headers.getFirst(GatewayProtocol.REALM));
        Assertions.assertEquals("TENANT", headers.getFirst(GatewayProtocol.CONTEXT_TYPE));
        Assertions.assertEquals(JwtFixtureBuilder.TENANT_ID, headers.getFirst(GatewayProtocol.TENANT_ID));
        Assertions.assertEquals("INTERNAL", headers.getFirst(GatewayProtocol.TENANT_TYPE));
        Assertions.assertEquals(JwtFixtureBuilder.TENANT_MEMBER_ID, headers.getFirst(GatewayProtocol.TENANT_MEMBER_ID));
        Assertions.assertEquals("stable-command-key", headers.getFirst("Idempotency-Key"));
        Assertions.assertNull(headers.getFirst(GatewayProtocol.CSRF_TOKEN));
        Assertions.assertFalse(headers.containsHeader(HttpHeaders.AUTHORIZATION));
        Assertions.assertFalse(headers.containsHeader(HttpHeaders.COOKIE));
    }

    @Test
    void test_bootstrapIdentityDoesNotAcquireForgedTenantContext() {
        HttpHeaders headers = forward(token("BOOTSTRAP"), true).getRequest().getHeaders();

        Assertions.assertEquals("BOOTSTRAP", headers.getFirst(GatewayProtocol.CONTEXT_TYPE));
        TENANT_HEADERS.forEach(name -> Assertions.assertFalse(headers.containsHeader(name), name));
    }

    @Test
    void test_publicRouteCannotAcquireIdentityFromCallerOrSecurityContext() {
        HttpHeaders headers = forward(token("TENANT"), false).getRequest().getHeaders();

        for (String name : List.of(GatewayProtocol.ACTOR_SUBJECT, GatewayProtocol.SESSION_ID,
            GatewayProtocol.ACTOR_TYPE, GatewayProtocol.CHANNEL, GatewayProtocol.REALM, GatewayProtocol.CONTEXT_TYPE)) {
            Assertions.assertFalse(headers.containsHeader(name), name);
        }
        TENANT_HEADERS.forEach(name -> Assertions.assertFalse(headers.containsHeader(name), name));
        Assertions.assertEquals("operations-account-create", headers.getFirst(GatewayProtocol.ROUTE_CODE));
    }

    @Test
    void test_typedIdentityMapperStoresImmutableIdentityAndDigestWithoutChangingHeaders() {
        GatewayRuntimeRoute route = route(true);
        Jwt jwt = token("TENANT");
        JwtAuthenticationToken authentication = new JwtAuthenticationToken(jwt, List.of());
        Assertions.assertEquals("OPERATIONS_JWT", route.securityProfileCode());
        Assertions.assertTrue(authentication.isAuthenticated());
        Assertions.assertDoesNotThrow(() -> new OperationsIdentity(jwt.getSubject(), jwt.getClaimAsString("sid"),
            jwt.getClaimAsString("context_type"), jwt.getClaimAsString("tenant_id"),
            jwt.getClaimAsString("tenant_member_id")));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath())
            .header(HttpHeaders.AUTHORIZATION, "Bearer transport-token")
            .header(GatewayProtocol.ACTOR_SUBJECT, "caller-forged-subject"));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        AtomicReference<ServerWebExchange> observed = new AtomicReference<>();
        AtomicInteger chainCalls = new AtomicInteger();

        GatewayAuthenticatedIdentityGlobalFilter mapper = new GatewayAuthenticatedIdentityGlobalFilter(digester());
        Assertions.assertEquals(GatewayGlobalFilterOrder.TYPED_AUTHENTICATED_IDENTITY, mapper.getOrder());
        Assertions.assertEquals(GatewayGlobalFilterOrder.TRUSTED_DOWNSTREAM_IDENTITY,
            new AuthenticatedOperationsIdentityGlobalFilter().getOrder());
        mapper.filter(exchange, mapped -> {
            chainCalls.incrementAndGet();
            observed.set(mapped);
            return Mono.empty();
        }).contextWrite(ReactiveSecurityContextHolder.withAuthentication(authentication))
            .block(Duration.ofSeconds(1));

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertSame(exchange.getRequest(), observed.get().getRequest());
        OperationsIdentity identity = Assertions.assertInstanceOf(OperationsIdentity.class,
            observed.get().getAttribute(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY));
        VersionedHmacDigester.VersionedDigest expectedDigest =
            digester().accessSubjectDigest(JwtFixtureBuilder.SUBJECT);
        Assertions.assertAll(() -> Assertions.assertEquals(JwtFixtureBuilder.SUBJECT, identity.principalKey()),
            () -> Assertions.assertEquals(Optional.of(JwtFixtureBuilder.TENANT_ID), identity.tenantKey()),
            () -> Assertions.assertEquals(JwtFixtureBuilder.SUBJECT, identity.accessSubject()),
            () -> Assertions.assertFalse(identity.toString().contains(JwtFixtureBuilder.SUBJECT)),
            () -> Assertions.assertEquals(expectedDigest.version() + ":" + expectedDigest.digest(),
                observed.get().getAttribute(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST)));
    }

    @Test
    void test_typedIdentityMapperDoesNotReplaceExistingAuthorizationFailureDigest() {
        GatewayRuntimeRoute route = route(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath()));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        exchange.getAttributes().put(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST, "existing-safe-digest");
        AtomicInteger chainCalls = new AtomicInteger();

        new GatewayAuthenticatedIdentityGlobalFilter(digester())
            .filter(exchange, ignored -> Mono.fromRunnable(chainCalls::incrementAndGet))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(token("TENANT"), List.of())))
            .block(Duration.ofSeconds(1));

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertInstanceOf(OperationsIdentity.class,
            exchange.getAttribute(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY));
        Assertions.assertEquals("existing-safe-digest",
            exchange.getAttribute(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST));
    }

    @Test
    void test_typedIdentityMapperCallsChainOnceWhenNoSupportedIdentityExists() {
        GatewayRuntimeRoute route = route(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath()));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        AtomicInteger chainCalls = new AtomicInteger();

        new GatewayAuthenticatedIdentityGlobalFilter(digester())
            .filter(exchange, ignored -> Mono.fromRunnable(chainCalls::incrementAndGet)).block(Duration.ofSeconds(1));

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY));
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST));
    }

    @Test
    void test_typedIdentityMapperCallsChainOnceWhenSecurityContextHasNoAuthentication() {
        GatewayRuntimeRoute route = route(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath()));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        AtomicInteger chainCalls = new AtomicInteger();

        new GatewayAuthenticatedIdentityGlobalFilter(digester())
            .filter(exchange, ignored -> Mono.fromRunnable(chainCalls::incrementAndGet))
            .contextWrite(ReactiveSecurityContextHolder.withSecurityContext(Mono.just(new SecurityContextImpl())))
            .block(Duration.ofSeconds(1));

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY));
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST));
    }

    @Test
    void test_typedIdentityMapperIgnoresAuthenticatedNonJwtIdentity() {
        GatewayRuntimeRoute route = route(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath()));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        AtomicInteger chainCalls = new AtomicInteger();

        new GatewayAuthenticatedIdentityGlobalFilter(digester())
            .filter(exchange, ignored -> Mono.fromRunnable(chainCalls::incrementAndGet))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new TestingAuthenticationToken("not-jwt", "not-retained", List.of())))
            .block(Duration.ofSeconds(1));

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY));
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST));
    }

    @Test
    void test_typedIdentityMapperIgnoresUnauthenticatedJwtAndCallsChainOnce() {
        GatewayRuntimeRoute route = route(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath()));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        AtomicInteger chainCalls = new AtomicInteger();
        JwtAuthenticationToken unauthenticated = new JwtAuthenticationToken(token("TENANT"));

        Assertions.assertFalse(unauthenticated.isAuthenticated());
        new GatewayAuthenticatedIdentityGlobalFilter(digester())
            .filter(exchange, ignored -> Mono.fromRunnable(chainCalls::incrementAndGet))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(unauthenticated))
            .block(Duration.ofSeconds(1));

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY));
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST));
    }

    @Test
    void test_publicRouteNeverCreatesTypedIdentityOrDigestFromJwt() {
        GatewayRuntimeRoute route = route(false);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath()));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        AtomicInteger chainCalls = new AtomicInteger();

        new GatewayAuthenticatedIdentityGlobalFilter(digester())
            .filter(exchange, ignored -> Mono.fromRunnable(chainCalls::incrementAndGet))
            .contextWrite(ReactiveSecurityContextHolder.withAuthentication(
                new JwtAuthenticationToken(token("TENANT"), List.of())))
            .block(Duration.ofSeconds(1));

        Assertions.assertEquals(1, chainCalls.get());
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.AUTHENTICATED_IDENTITY));
        Assertions.assertNull(exchange.getAttribute(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST));
    }

    @Test
    void test_downstreamIdentityInjectorDoesNotReadJwtWhenTypedIdentityIsMissing() {
        GatewayRuntimeRoute route = route(true);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath())
            .header(HttpHeaders.AUTHORIZATION, "Bearer caller-value")
            .header(GatewayProtocol.ACTOR_SUBJECT, "caller-forged-subject")
            .header(GatewayProtocol.TENANT_ID, "caller-forged-tenant"));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "gateway-request-id");
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        exchange.getAttributes().put(GatewayExchangeAttributes.DOWNSTREAM_PATH, route.downstreamPath());
        exchange.getAttributes().put(GatewayExchangeAttributes.ACTUAL_REQUEST_BODY_LENGTH, 0L);
        UpstreamRequestGlobalFilter upstream = new UpstreamRequestGlobalFilter(
            new GatewayErrorWriter(ServerCodecConfigurer.create()), new ConsoleRefreshCookiePolicy(
                new OperationsRoutingProperties("https://operations-console.test", Duration.ofSeconds(1))));
        AuthenticatedOperationsIdentityGlobalFilter downstreamIdentity =
            new AuthenticatedOperationsIdentityGlobalFilter();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        upstream.filter(exchange, sanitized -> downstreamIdentity.filter(sanitized, observed -> {
            forwarded.set(observed);
            return Mono.empty();
        })).contextWrite(ReactiveSecurityContextHolder.withAuthentication(
            new JwtAuthenticationToken(token("TENANT"), List.of())))
            .block(Duration.ofSeconds(1));

        HttpHeaders headers = forwarded.get().getRequest().getHeaders();
        Assertions.assertNull(headers.getFirst(GatewayProtocol.ACTOR_SUBJECT));
        Assertions.assertNull(headers.getFirst(GatewayProtocol.SESSION_ID));
        Assertions.assertNull(headers.getFirst(GatewayProtocol.TENANT_ID));
        Assertions.assertNull(headers.getFirst(HttpHeaders.AUTHORIZATION));
    }

    private ServerWebExchange forward(Jwt token, boolean authenticated) {
        GatewayRuntimeRoute route = route(authenticated);
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(route.externalPath())
            .header(HttpHeaders.AUTHORIZATION, "Bearer caller-value").header(HttpHeaders.COOKIE, "caller=value")
            .header(GatewayProtocol.ROUTE_CODE, "caller-selected-route").header(GatewayProtocol.REALM, "CUSTOMER")
            .header(GatewayProtocol.CONTEXT_TYPE, "TENANT").header(GatewayProtocol.TENANT_ID, "caller-tenant")
            .header(GatewayProtocol.TENANT_TYPE, "CLIENT").header(GatewayProtocol.TENANT_MEMBER_ID, "caller-member")
            .header(GatewayProtocol.CSRF_TOKEN, "browser-csrf-nonce").header("Idempotency-Key", "stable-command-key"));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "gateway-request-id");
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE, route);
        exchange.getAttributes().put(GatewayExchangeAttributes.DOWNSTREAM_PATH, route.downstreamPath());
        exchange.getAttributes().put(GatewayExchangeAttributes.ACTUAL_REQUEST_BODY_LENGTH, 0L);
        var upstream = new UpstreamRequestGlobalFilter(new GatewayErrorWriter(ServerCodecConfigurer.create()),
            new ConsoleRefreshCookiePolicy(
                new OperationsRoutingProperties("https://operations-console.test", Duration.ofSeconds(1))));
        var mapper = new GatewayAuthenticatedIdentityGlobalFilter(digester());
        var downstreamIdentity = new AuthenticatedOperationsIdentityGlobalFilter();
        AtomicReference<ServerWebExchange> forwarded = new AtomicReference<>();

        mapper.filter(exchange,
            mapped -> upstream.filter(mapped, sanitized -> downstreamIdentity.filter(sanitized, observed -> {
                forwarded.set(observed);
                return Mono.empty();
            }))).contextWrite(ReactiveSecurityContextHolder.withAuthentication(new JwtAuthenticationToken(token, List.of())))
            .block(Duration.ofSeconds(1));
        return forwarded.get();
    }

    private VersionedHmacDigester digester() {
        byte[] bytes = new byte[32];
        Arrays.fill(bytes, (byte)7);
        return new VersionedHmacDigester(new GatewayHmacProperties(
            new GatewayHmacProperties.Secret("test-v1", Base64.getEncoder().encodeToString(bytes))));
    }

    private GatewayRuntimeRoute route(boolean authenticated) {
        var initial = GatewaySnapshotFixture.operationsSnapshot();
        GatewayRouteSnapshot guardSource = initial.getRoutes().get(authenticated ? 2 : 0);
        GatewayRouteSnapshot management = new GatewayRouteSnapshot("operations-account-create", "OPERATIONS", "POST",
            "/api/operations/iam/v1/accounts", "/iam/v1/accounts", "operations-console-bff",
            authenticated ? "OPERATIONS_JWT" : "PUBLIC", authenticated ? "HUMAN_PERMISSION" : "PUBLIC",
            authenticated ? "OPERATIONS_ACCOUNT_CREATE" : null, null, "DROP", "NONE",
            List.of("authorization", "content-type", "idempotency-key"),
            List.of("origin", "content-type", "idempotency-key"), List.of(), 16_384, 5_000, 10_000,
            guardSource.getGuardRules());
        return new GatewaySnapshotCompiler()
            .compile(new GatewayPublicSnapshotResponseBody(1, initial.getTargets(), List.of(management))).catalog().all().getFirst();
    }

    private Jwt token(String context) {
        Jwt.Builder token =
            Jwt.withTokenValue("test-access-token").header("alg", "RS256").subject(JwtFixtureBuilder.SUBJECT)
                .claim("sid", JwtFixtureBuilder.SESSION_ID).claim("context_type", context);
        if ("TENANT".equals(context)) {
            token.claim("tenant_id", JwtFixtureBuilder.TENANT_ID).claim("tenant_type", "INTERNAL")
                .claim("tenant_member_id", JwtFixtureBuilder.TENANT_MEMBER_ID);
        }
        return token.build();
    }
}
