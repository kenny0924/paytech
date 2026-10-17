package com.paytech.gateway.auto.security;

import com.paytech.gateway.test.GatewayPublicTestClient;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;

import com.nimbusds.jose.Algorithm;
import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.security.ReactiveJwksProvider;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.GatewaySnapshotFixture;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration.GatewayBoundaryProbe;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration.PostIdentityContinuationProbe;
import com.paytech.gateway.test.simulator.bff.BffRequestJournal;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;
import com.paytech.gateway.test.simulator.jwks.JwksSimulator;

/** Operations Access JWT、Logout 与可信身份的真实 HTTP 请求级矩阵。 */
@Import(GatewayBoundaryTestConfiguration.class)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class OperationsJwtValidationAutoTest extends GatewayAutoTestBase {
    private static final String ORIGIN = "https://operations-console.test";
    private static final String LOGOUT_PATH = OperationsTestPaths.LOGOUT;
    private static final String REFRESH_COOKIE_NAME = "__Secure-operations-refresh";
    private static final String REFRESH_COOKIE = REFRESH_COOKIE_NAME + "=opaque-refresh";
    private static final String CACHED_KID = "shared-regression-key";

    @LocalServerPort
    private int port;

    @Autowired
    private GatewayBoundaryProbe boundaryProbe;

    @Autowired
    private PostIdentityContinuationProbe postIdentityContinuationProbe;

    @Autowired
    private ReactiveJwksProvider reactiveJwksProvider;

    @TestBean(name = "operationsJwtClock", methodName = "fixedClock", enforceOverride = true)
    private Clock operationsJwtClock;

    private static Stream<Arguments> invalidAuthenticationClaims() {
        return Stream.of(claimCase("missing iss", builder -> builder.withoutClaim("iss")),
            claimCase("empty iss", builder -> builder.claim("iss", "")),
            claimCase("wrong iss", builder -> builder.claim("iss", "https://attacker.test")),
            claimCase("missing sub", builder -> builder.withoutClaim("sub")),
            claimCase("empty sub", builder -> builder.claim("sub", "")),
            claimCase("blank sub", builder -> builder.claim("sub", Character.toString(0x2003))),
            claimCase("control sub", builder -> builder.claim("sub", "operations" + Character.toString(0x85) + "user")),
            claimCase("oversized sub", builder -> builder.claim("sub", "s".repeat(513))),
            claimCase("missing aud", builder -> builder.withoutClaim("aud")),
            claimCase("empty aud", builder -> builder.claim("aud", List.of())),
            claimCase("wrong aud", builder -> builder.claim("aud", List.of("client-console"))),
            claimCase("missing token_use", builder -> builder.withoutClaim("token_use")),
            claimCase("empty token_use", builder -> builder.claim("token_use", "")),
            claimCase("wrong token_use", builder -> builder.claim("token_use", "REFRESH")),
            claimCase("missing realm", builder -> builder.withoutClaim("realm")),
            claimCase("empty realm", builder -> builder.claim("realm", "")),
            claimCase("wrong realm", builder -> builder.claim("realm", "CUSTOMER")),
            claimCase("numeric realm", builder -> builder.claim("realm", 1001)),
            claimCase("structured realm", builder -> builder.claim("realm", Map.of("value", "OPERATIONS"))),
            claimCase("mixed audience",
                builder -> builder.claim("aud", List.of("operations-console", "client-console"))),
            claimCase("missing context", builder -> builder.withoutClaim("context_type")),
            claimCase("account context", builder -> builder.claim("context_type", "ACCOUNT")),
            claimCase("bootstrap with tenant", builder -> builder.claim("context_type", "BOOTSTRAP")),
            claimCase("missing tenant", builder -> builder.withoutClaim("tenant_id")),
            claimCase("wrong tenant type", builder -> builder.claim("tenant_type", "CLIENT")),
            claimCase("missing member", builder -> builder.withoutClaim("tenant_member_id")),
            claimCase("numeric member", builder -> builder.claim("tenant_member_id", 4001)),
            claimCase("control member", builder -> builder.claim("tenant_member_id", "member\nvalue")),
            claimCase("missing sid", builder -> builder.withoutClaim("sid")),
            claimCase("empty sid", builder -> builder.claim("sid", "")),
            claimCase("numeric sid", builder -> builder.claim("sid", 2001)),
            claimCase("structured sid", builder -> builder.claim("sid", Map.of("value", "session-2001"))),
            claimCase("missing jti", builder -> builder.withoutClaim("jti")),
            claimCase("empty jti", builder -> builder.claim("jti", "")),
            claimCase("missing iat", builder -> builder.withoutClaim("iat")),
            claimCase("missing nbf", builder -> builder.withoutClaim("nbf")),
            claimCase("missing exp", builder -> builder.withoutClaim("exp")));
    }

    private static Stream<Arguments> invalidAuthorizationClaims() {
        return Stream.of(claimCase("missing channel", builder -> builder.withoutClaim("channel")),
            claimCase("empty channel", builder -> builder.claim("channel", "")),
            claimCase("wrong channel", builder -> builder.claim("channel", "CLIENT")),
            claimCase("missing actor_type", builder -> builder.withoutClaim("actor_type")),
            claimCase("empty actor_type", builder -> builder.claim("actor_type", "")),
            claimCase("wrong actor_type", builder -> builder.claim("actor_type", "CLIENT_USER")));
    }

    private static Stream<Arguments> invalidTimeRanges() {
        Instant now = JwtFixtureBuilder.NOW;
        return Stream.of(
            claimCase("iat later than nbf", builder -> builder.times(now, now.minusSeconds(1), now.plusSeconds(60))),
            claimCase("nbf equal to exp", builder -> builder.times(now.minusSeconds(60), now, now)),
            claimCase("lifetime is 901 seconds",
                builder -> builder.times(now.minusSeconds(60), now.minusSeconds(60),
                    now.minusSeconds(60).plusSeconds(901))),
            claimCase("iat is 31 seconds in future",
                builder -> builder.times(now.plusSeconds(31), now.plusSeconds(31), now.plusSeconds(90))),
            claimCase("token is beyond expiration skew",
                builder -> builder.times(now.minusSeconds(120), now.minusSeconds(120), now.minusSeconds(31))));
    }

    private static Stream<Arguments> acceptedTimeBoundaries() {
        Instant now = JwtFixtureBuilder.NOW;
        return Stream.of(
            claimCase("lifetime is exactly 900 seconds",
                builder -> builder.times(now.minusSeconds(60), now.minusSeconds(60),
                    now.minusSeconds(60).plusSeconds(900))),
            claimCase("iat is exactly 30 seconds in future",
                builder -> builder.times(now.plusSeconds(30), now.plusSeconds(30), now.plusSeconds(90))),
            claimCase("nbf is exactly 30 seconds in future",
                builder -> builder.times(now, now.plusSeconds(30), now.plusSeconds(90))),
            claimCase("exp is exactly 30 seconds in past",
                builder -> builder.times(now.minusSeconds(120), now.minusSeconds(120), now.minusSeconds(30))));
    }

    private static Stream<Arguments> ignoredAccessTokenCandidates() {
        return Stream.of(Arguments.of("form access_token", AccessTokenLocation.FORM),
            Arguments.of("Cookie access_token", AccessTokenLocation.COOKIE));
    }

    private static Stream<Arguments> invalidRefreshCookies() {
        return Stream.of(Arguments.of("missing refresh Cookie", (CookieMutation)request -> {
        }), Arguments.of("empty refresh Cookie",
            (CookieMutation)request -> request.header(HttpHeaders.COOKIE, REFRESH_COOKIE_NAME + "=")),
            Arguments.of("duplicate refresh Cookie",
                (CookieMutation)request -> request.header(HttpHeaders.COOKIE,
                    REFRESH_COOKIE_NAME + "=one; " + REFRESH_COOKIE_NAME + "=two")),
            Arguments.of("malformed refresh Cookie",
                (CookieMutation)request -> request.header(HttpHeaders.COOKIE, REFRESH_COOKIE_NAME + "=opaque value")));
    }

    private static Arguments claimCase(String name, Consumer<JwtFixtureBuilder> mutation) {
        return Arguments.of(name, mutation);
    }

    private static Stream<JWSAlgorithm> invalidSignedAlgorithms() {
        return Stream.of(JWSAlgorithm.RS512, JWSAlgorithm.PS256);
    }

    private static Stream<String> invalidKeyIds() {
        return Stream.of(" ", "bad/kid", "bad\\kid", "bad\nkid", "clé", "ｋｉｄ", "kid😀", "kid\u200bvalue",
            "k".repeat(129));
    }

    private static JwtFixtureBuilder jwt() {
        return JwtFixtureBuilder.template().keyId(CACHED_KID);
    }

    private static Clock fixedClock() {
        return Clock.fixed(JwtFixtureBuilder.NOW, ZoneOffset.UTC);
    }

    @BeforeEach
    void resetBoundaryProbe() {
        boundaryProbe.reset();
        postIdentityContinuationProbe.reset();
        jwksSimulator().scenarios().respondWithJwks(JwtFixtureBuilder.jwksJson(CACHED_KID));
        try {
            reactiveJwksProvider.select(SignedJWT.parse(jwt().build())).single().block(Duration.ofSeconds(1));
        } catch (java.text.ParseException exception) {
            throw new IllegalStateException("无法解析 JWKS 预热 JWT", exception);
        }
        jwksSimulator().reset();
    }

    @Test
    void test_validRs256AccessTokenForwardsOnlyTrustedJwtIdentity() {
        assertAuthorizedLogout(jwt());
    }

    @Test
    void test_sameOriginGetWithoutOriginStillRequiresJwtAndForwardsTrustedContext() {
        GatewayAutoTestRuntime.platformSnapshotSimulator()
            .publish(GatewaySnapshotFixture.operationsSnapshotWithContextQuery());
        Assertions.assertTrue(refreshSnapshot());
        simulator().scenarios().expect(HttpMethod.GET, "/auth/v1/context", BffScenarioStore.Response.json(200, "{}"));

        gatewayClient().get().uri("/api/operations/auth/v1/context").exchange().expectStatus().isUnauthorized();
        Assertions.assertEquals(0, simulator().journal().count());
        gatewayClient().get().uri("/api/operations/auth/v1/context")
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt().build()).exchange().expectStatus().isOk()
            .expectHeader().doesNotExist(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN);

        BffRequestJournal.Entry entry = onlyBffEntry();
        Assertions.assertEquals(List.of("operations-auth-context"), headerValues(entry, "X-Gateway-Route-Code"));
        Assertions.assertEquals(List.of(JwtFixtureBuilder.SUBJECT), headerValues(entry, "X-Gateway-Actor-Subject"));
        Assertions.assertTrue(headerValues(entry, HttpHeaders.ORIGIN).isEmpty());
    }

    @Test
    void test_bootstrapContextForwardsWithoutForgedTenantHeaders() {
        JwtFixtureBuilder builder = jwt().claim("context_type", "BOOTSTRAP").withoutClaim("tenant_id")
            .withoutClaim("tenant_type").withoutClaim("tenant_member_id");
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout", BffScenarioStore.Response.json(200, "{}"));

        logoutRequest(builder.build(), REFRESH_COOKIE).header("X-Gateway-Tenant-Id", "attacker-tenant")
            .header("X-Gateway-Tenant-Type", "INTERNAL").header("X-Gateway-Tenant-Member-Id", "attacker-member")
            .bodyValue("{}").exchange().expectStatus().isOk();

        BffRequestJournal.Entry entry = onlyBffEntry();
        Assertions.assertEquals(List.of("BOOTSTRAP"), headerValues(entry, "X-Gateway-Context-Type"));
        Assertions.assertEquals(List.of("OPERATIONS"), headerValues(entry, "X-Gateway-Realm"));
        for (String header : List.of("X-Gateway-Tenant-Id", "X-Gateway-Tenant-Type", "X-Gateway-Tenant-Member-Id")) {
            Assertions.assertTrue(headerValues(entry, header).isEmpty(), header);
        }
    }

    @Test
    void test_authenticatedLogoutInvokesPostIdentityContinuationOnce() {
        assertAuthorizedLogout(jwt());

        Assertions.assertEquals(1, postIdentityContinuationProbe.count());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAuthenticationClaims")
    void test_invalidAuthenticationClaimReturnsStable401(String ignored, Consumer<JwtFixtureBuilder> mutation) {
        JwtFixtureBuilder builder = jwt();
        mutation.accept(builder);

        assertLocalSecurityFailure(logoutRequest(builder.build(), REFRESH_COOKIE), 401, "GATEWAY_UNAUTHORIZED",
            GatewayOutcome.AUTHENTICATION_FAILED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidAuthorizationClaims")
    void test_invalidChannelIdentityReturnsStable403(String ignored, Consumer<JwtFixtureBuilder> mutation) {
        JwtFixtureBuilder builder = jwt();
        mutation.accept(builder);

        assertLocalSecurityFailure(logoutRequest(builder.build(), REFRESH_COOKIE), 403, "GATEWAY_FORBIDDEN",
            GatewayOutcome.AUTHORIZATION_DENIED);
    }

    @Test
    void test_noneAlgorithmIsRejectedBeforeJwksFetch() {
        JwtFixtureBuilder builder = jwt().algorithm(Algorithm.NONE);

        assertLocalSecurityFailure(logoutRequest(builder.build(), REFRESH_COOKIE), 401, "GATEWAY_UNAUTHORIZED",
            GatewayOutcome.AUTHENTICATION_FAILED);
    }

    @ParameterizedTest(name = "{0} is rejected before JWKS")
    @MethodSource("invalidSignedAlgorithms")
    void test_nonRs256AlgorithmIsRejectedBeforeJwks(JWSAlgorithm algorithm) {
        JwtFixtureBuilder builder = jwt().algorithm(algorithm);

        assertLocalSecurityFailure(logoutRequest(builder.build(), REFRESH_COOKIE), 401, "GATEWAY_UNAUTHORIZED",
            GatewayOutcome.AUTHENTICATION_FAILED);
    }

    @Test
    void test_missingKidIsRejectedBeforeJwksFetch() {
        JwtFixtureBuilder builder = jwt().withoutKeyId();

        assertLocalSecurityFailure(logoutRequest(builder.build(), REFRESH_COOKIE), 401, "GATEWAY_UNAUTHORIZED",
            GatewayOutcome.AUTHENTICATION_FAILED);
    }

    @ParameterizedTest(name = "invalid kid [{0}]")
    @MethodSource("invalidKeyIds")
    void test_invalidKidIsRejectedBeforeJwks(String kid) {
        JwtFixtureBuilder builder = jwt().keyId(kid);

        assertLocalSecurityFailure(logoutRequest(builder.build(), REFRESH_COOKIE), 401, "GATEWAY_UNAUTHORIZED",
            GatewayOutcome.AUTHENTICATION_FAILED);
    }

    @Test
    void test_wrongSigningKeyIsRejectedAfterRealHttpFetch() {
        JwtFixtureBuilder builder = jwt().signWithWrongKey();

        assertLocalSecurityFailure(logoutRequest(builder.build(), REFRESH_COOKIE), 401, "GATEWAY_UNAUTHORIZED",
            GatewayOutcome.AUTHENTICATION_FAILED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidTimeRanges")
    void test_invalidTimeRangeReturnsStable401(String ignored, Consumer<JwtFixtureBuilder> mutation) {
        JwtFixtureBuilder builder = jwt();
        mutation.accept(builder);

        assertLocalSecurityFailure(logoutRequest(builder.build(), REFRESH_COOKIE), 401, "GATEWAY_UNAUTHORIZED",
            GatewayOutcome.AUTHENTICATION_FAILED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("acceptedTimeBoundaries")
    void test_exactTimeBoundaryIsAccepted(String ignored, Consumer<JwtFixtureBuilder> mutation) {
        JwtFixtureBuilder builder = jwt();
        mutation.accept(builder);

        assertAuthorizedLogout(builder);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("ignoredAccessTokenCandidates")
    void test_nonBearerAccessTokenCandidateIsIgnored(String ignored, AccessTokenLocation location) {
        JwtFixtureBuilder builder = jwt();
        WebTestClient.RequestBodySpec request = gatewayClient().post().uri(LOGOUT_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.COOKIE, REFRESH_COOKIE);
        if (location == AccessTokenLocation.FORM) {
            request.contentType(MediaType.APPLICATION_FORM_URLENCODED).bodyValue("access_token=" + builder.build());
        }
        if (location == AccessTokenLocation.COOKIE) {
            request.header(HttpHeaders.COOKIE, "access_token=" + builder.build());
        }

        assertLocalSecurityFailure(request, 401, "GATEWAY_UNAUTHORIZED", GatewayOutcome.AUTHENTICATION_FAILED);
    }

    @Test
    void test_loginAndRefreshIgnoreInvalidBearerAndReceiveOnlyTrustedRequestId() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login", BffScenarioStore.Response.json(200, "{}"));
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/refresh", BffScenarioStore.Response.json(200, "{}"));

        gatewayClient().post().uri(OperationsTestPaths.LOGIN).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt").header("X-Gateway-Actor-Subject", "attacker")
            .bodyValue("{}").exchange().expectStatus().isOk();
        gatewayClient().post().uri(OperationsTestPaths.REFRESH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt").header(HttpHeaders.COOKIE, REFRESH_COOKIE)
            .header("X-Gateway-Session-Id", "attacker-session").bodyValue("{}").exchange().expectStatus().isOk();

        Assertions.assertEquals(2, simulator().journal().count());
        assertOnlyAnonymousTrustedHeaders(simulator().journal().entries().get(0), false);
        assertOnlyAnonymousTrustedHeaders(simulator().journal().entries().get(1), true);
        jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_logoutRequiresBearerAndReturnsStableJson401WithoutBffCall() {
        WebTestClient.RequestBodySpec request = gatewayClient().post().uri(LOGOUT_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.COOKIE, REFRESH_COOKIE);

        assertLocalSecurityFailure(request, 401, "GATEWAY_UNAUTHORIZED", GatewayOutcome.AUTHENTICATION_FAILED);
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidRefreshCookies")
    void test_authenticatedLogoutAcceptsJsonAndIgnoresEveryCookieShape(String ignored, CookieMutation mutation) {
        JwtFixtureBuilder builder = jwt();
        WebTestClient.RequestBodySpec request = gatewayClient().post().uri(LOGOUT_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.AUTHORIZATION, "Bearer " + builder.build());
        mutation.apply(request);

        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout", BffScenarioStore.Response.json(204, ""));
        String body = "{\"refreshToken\":\"json-refresh\"}";
        request.contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isNoContent();
        BffRequestJournal.Entry entry = onlyBffEntry();
        Assertions.assertTrue(headerValues(entry, HttpHeaders.COOKIE).isEmpty());
        Assertions.assertEquals(entry.bodyDigestOf(body), entry.bodyDigest());
    }

    @Test
    void test_logoutOptionsIsHandledLocallyWithoutJwtOrBffCall() {
        EntityExchangeResult<byte[]> result = gatewayClient().options().uri(LOGOUT_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST")
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_HEADERS, "Authorization, Content-Type")
            .header(HttpHeaders.AUTHORIZATION, "Bearer not-a-jwt").exchange().expectStatus().isNoContent()
            .expectHeader().valueEquals(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, ORIGIN).expectBody().returnResult();

        assertTrustedRequestId(result);
        assertTerminal("operations-auth-logout", GatewayOutcome.UPSTREAM_RESPONSE);
        Assertions.assertEquals(0, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    private void assertAuthorizedLogout(JwtFixtureBuilder builder) {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout", BffScenarioStore.Response.json(200, "{}"));

        EntityExchangeResult<byte[]> result =
            logoutRequest(builder.build(), REFRESH_COOKIE).header("X-Gateway-Actor-Subject", "attacker")
                .header("X-Gateway-Session-Id", "attacker-session").header("X-Gateway-Actor-Type", "CLIENT")
                .header("X-Gateway-Channel", "CLIENT").header("X-Gateway-Realm", "CUSTOMER")
                .header("X-Gateway-Context-Type", "BOOTSTRAP").header("X-Gateway-Tenant-Id", "attacker-tenant")
                .header("X-Gateway-Tenant-Type", "CLIENT").header("X-Gateway-Tenant-Member-Id", "attacker-member")
                .bodyValue("{}").exchange().expectStatus().isOk().expectBody().returnResult();

        String requestId = assertTrustedRequestId(result);
        assertCorsHeaders(result);
        BffRequestJournal.Entry entry = onlyBffEntry();
        Assertions.assertEquals(List.of(requestId), headerValues(entry, "X-Request-Id"));
        Assertions.assertEquals(List.of(JwtFixtureBuilder.SUBJECT), headerValues(entry, "X-Gateway-Actor-Subject"));
        Assertions.assertEquals(List.of(JwtFixtureBuilder.SESSION_ID), headerValues(entry, "X-Gateway-Session-Id"));
        Assertions.assertEquals(List.of("PLATFORM_USER"), headerValues(entry, "X-Gateway-Actor-Type"));
        Assertions.assertEquals(List.of("OPERATIONS"), headerValues(entry, "X-Gateway-Channel"));
        Assertions.assertEquals(List.of("OPERATIONS"), headerValues(entry, "X-Gateway-Realm"));
        Assertions.assertEquals(List.of("TENANT"), headerValues(entry, "X-Gateway-Context-Type"));
        Assertions.assertEquals(List.of(JwtFixtureBuilder.TENANT_ID), headerValues(entry, "X-Gateway-Tenant-Id"));
        Assertions.assertEquals(List.of("INTERNAL"), headerValues(entry, "X-Gateway-Tenant-Type"));
        Assertions.assertEquals(List.of(JwtFixtureBuilder.TENANT_MEMBER_ID),
            headerValues(entry, "X-Gateway-Tenant-Member-Id"));
        Assertions.assertTrue(headerValues(entry, HttpHeaders.COOKIE).isEmpty());
        Assertions.assertTrue(headerValues(entry, HttpHeaders.AUTHORIZATION).isEmpty());
        jwksSimulator().journal().verifyNoRequests();
    }

    private void assertLocalSecurityFailure(WebTestClient.RequestBodySpec request, int status, String code,
        GatewayOutcome outcome) {
        EntityExchangeResult<byte[]> result = request.exchange().expectStatus().isEqualTo(status).expectBody()
            .jsonPath("$.code").isEqualTo(code).returnResult();

        assertTrustedRequestId(result);
        assertCorsHeaders(result);
        assertTerminal("operations-auth-logout", outcome);
        Assertions.assertEquals(0, simulator().journal().count());
        jwksSimulator().journal().verifyNoRequests();
    }

    private WebTestClient.RequestBodySpec logoutRequest(String token, String cookie) {
        return gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + token).header(HttpHeaders.COOKIE, cookie);
    }

    private void assertOnlyAnonymousTrustedHeaders(BffRequestJournal.Entry entry, boolean expectsRefreshCookie) {
        Assertions.assertEquals(1, headerValues(entry, "X-Request-Id").size());
        Assertions.assertDoesNotThrow(() -> UUID.fromString(headerValues(entry, "X-Request-Id").getFirst()));
        Assertions.assertTrue(headerValues(entry, "X-Gateway-Actor-Subject").isEmpty());
        Assertions.assertTrue(headerValues(entry, "X-Gateway-Session-Id").isEmpty());
        Assertions.assertTrue(headerValues(entry, "X-Gateway-Actor-Type").isEmpty());
        Assertions.assertTrue(headerValues(entry, "X-Gateway-Channel").isEmpty());
        for (String header : List.of("X-Gateway-Realm", "X-Gateway-Context-Type", "X-Gateway-Tenant-Id",
            "X-Gateway-Tenant-Type", "X-Gateway-Tenant-Member-Id")) {
            Assertions.assertTrue(headerValues(entry, header).isEmpty(), header);
        }
        Assertions.assertTrue(headerValues(entry, HttpHeaders.AUTHORIZATION).isEmpty());
        Assertions.assertTrue(headerValues(entry, HttpHeaders.COOKIE).isEmpty());
    }

    private String assertTrustedRequestId(EntityExchangeResult<byte[]> result) {
        String requestId = result.getResponseHeaders().getFirst("X-Request-Id");
        Assertions.assertNotNull(requestId);
        Assertions.assertDoesNotThrow(() -> UUID.fromString(requestId));
        return requestId;
    }

    private void assertCorsHeaders(EntityExchangeResult<byte[]> result) {
        HttpHeaders headers = result.getResponseHeaders();
        Assertions.assertEquals(List.of(ORIGIN), headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN));
        Assertions.assertEquals(List.of("true"), headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS));
        Assertions.assertEquals(List.of("POST, OPTIONS"), headers.get(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS));
        Assertions.assertEquals(List.of("authorization, content-type"),
            headers.getOrEmpty(HttpHeaders.ACCESS_CONTROL_ALLOW_HEADERS).stream()
                .map(value -> value.toLowerCase(java.util.Locale.ROOT)).toList());
        Assertions.assertEquals(List.of("X-Request-Id, Retry-After"),
            headers.get(HttpHeaders.ACCESS_CONTROL_EXPOSE_HEADERS));
    }

    private void assertTerminal(String routeCode, GatewayOutcome outcome) {
        boundaryProbe.awaitTerminal(Duration.ofSeconds(1));
        Assertions.assertEquals(1, boundaryProbe.entries().size());
        Assertions.assertEquals(routeCode, boundaryProbe.entries().getFirst().routeCode());
        Assertions.assertEquals(outcome, boundaryProbe.entries().getFirst().outcome());
    }

    private BffRequestJournal.Entry onlyBffEntry() {
        Assertions.assertEquals(1, simulator().journal().count());
        return simulator().journal().entries().getFirst();
    }

    private List<String> headerValues(BffRequestJournal.Entry entry, String name) {
        return entry.headers().entrySet().stream().filter(header -> header.getKey().equalsIgnoreCase(name))
            .map(Map.Entry::getValue).findFirst().orElse(List.of());
    }

    private OperationsBffSimulator simulator() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }

    private JwksSimulator jwksSimulator() {
        return GatewayAutoTestRuntime.jwksSimulator();
    }

    private WebTestClient gatewayClient() {
        return GatewayPublicTestClient.operations(port)
            .defaultHeader("X-Real-IP", "203.0.113.17").build();
    }

    private enum AccessTokenLocation {
        FORM, COOKIE
    }

    @FunctionalInterface
    private interface CookieMutation {
        void apply(WebTestClient.RequestBodySpec request);
    }

}
