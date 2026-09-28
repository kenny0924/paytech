package com.paytech.gateway.auto.security;

import com.paytech.gateway.test.GatewayPublicTestClient;

import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPublicKey;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.reactive.function.client.WebClient;

import com.nimbusds.jose.JWSAlgorithm;
import com.nimbusds.jose.JWSHeader;
import com.nimbusds.jose.jwk.*;
import com.nimbusds.jose.util.Base64URL;
import com.nimbusds.jose.util.JSONObjectUtils;
import com.nimbusds.jwt.JWTClaimsSet;
import com.nimbusds.jwt.SignedJWT;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.security.JwksUnavailableException;
import com.paytech.gateway.security.ReactiveJwksProvider;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration;
import com.paytech.gateway.test.probe.GatewayBoundaryTestConfiguration.GatewayBoundaryProbe;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;
import com.paytech.gateway.test.simulator.jwks.JwksScenarioStore.ChunkBarrier;
import com.paytech.gateway.test.simulator.jwks.JwksScenarioStore.RequestBarrier;
import com.paytech.gateway.test.simulator.jwks.JwksSimulator;
import com.paytech.gateway.test.time.MutableTestClock;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Meter;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;
import reactor.test.StepVerifier;
import reactor.test.scheduler.VirtualTimeScheduler;

/** Operations JWKS 有界刷新状态机与真实 HTTP 文档边界测试。 */
@Import(GatewayBoundaryTestConfiguration.class)
class OperationsJwksAvailabilityAutoTest extends GatewayAutoTestBase {
    private static final String ORIGIN = "https://operations-console.test";
    private static final String LOGOUT_PATH = OperationsTestPaths.LOGOUT;
    private static final String REFRESH_COOKIE = "__Secure-operations-refresh=opaque-refresh";
    private static final MutableTestClock TEST_CLOCK = new MutableTestClock(JwtFixtureBuilder.NOW, ZoneOffset.UTC);
    private static final VirtualTimeScheduler TEST_SCHEDULER = VirtualTimeScheduler.create();

    @LocalServerPort
    private int port;

    @Autowired
    private GatewayBoundaryProbe boundaryProbe;

    @TestBean(name = "operationsJwtClock", methodName = "mutableClock", enforceOverride = true)
    private Clock operationsJwtClock;

    @TestBean(name = "operationsJwksScheduler", methodName = "virtualScheduler")
    private Scheduler operationsJwksScheduler;

    private static SignedJWT unsignedJwt(JWSAlgorithm algorithm, String kid) {
        JWSHeader.Builder header = new JWSHeader.Builder(algorithm);
        if (kid != null) {
            header.keyID(kid);
        }
        return new SignedJWT(header.build(), new JWTClaimsSet.Builder().subject("fixture").build());
    }

    private static Stream<JWSAlgorithm> invalidAlgorithms() {
        return Stream.of(JWSAlgorithm.RS512, JWSAlgorithm.PS256, JWSAlgorithm.HS256);
    }

    private static Stream<String> invalidKids() {
        return Stream.of(null, "", " ", "a/b", "a\\b", "a\nb", "a\u0001b", "café", "ｋｉｄ", "kid😀", "kid\u200bvalue",
            "k".repeat(129));
    }

    private static Stream<String> validKids() {
        return Stream.of("a", "k".repeat(128), "Az09._:-");
    }

    private static Stream<Arguments> invalidJwksPayloads() {
        RSAKey valid = JwtFixtureBuilder.publicJwk("requested-kid");
        return Stream.of(Arguments.of("zero keys", "{\"keys\":[]}"),
            Arguments.of("seventeen keys",
                JwtFixtureBuilder.jwksJson(IntStream.range(0, 17).mapToObj(index -> "key-" + index).toList())),
            Arguments.of("duplicate kid", jwkSetJson(valid, new RSAKey.Builder(valid).build())),
            Arguments.of("slash kid", jwkSetJson(JwtFixtureBuilder.publicJwk("bad/kid"))),
            Arguments.of("unicode kid", jwkSetJson(JwtFixtureBuilder.publicJwk("clé"))),
            Arguments.of("129-character kid", jwkSetJson(JwtFixtureBuilder.publicJwk("k".repeat(129)))),
            Arguments.of("non-RSA key",
                jwkSetJson(new OctetSequenceKey.Builder(new byte[32]).keyID("oct-key").keyUse(KeyUse.SIGNATURE)
                    .algorithm(JWSAlgorithm.HS256).build())),
            Arguments.of("private RSA material", privateJwkSetJson()),
            Arguments.of("RSA below 2048 bits", jwkSetJson(smallRsaKey())),
            Arguments.of("explicit encryption use",
                rsaJsonWith("use", "enc")),
            Arguments.of("key operations without verify",
                jwkSetJson(new RSAKey.Builder(valid).keyUse(null).keyOperations(Set.of(KeyOperation.SIGN)).build())),
            Arguments.of("explicit RS512", jwkSetJson(new RSAKey.Builder(valid).algorithm(JWSAlgorithm.RS512).build())),
            Arguments.of("unknown key member", rsaJsonWith("future", "poison")),
            Arguments.of("unknown top-level member", "{\"keys\":[" + valid.toJSONString() + "],\"future\":true}"),
            Arguments.of("unsupported key type poisons document",
                rawJwksJson(List.of(valid.toJSONObject(), unsupportedJwk()))),
            Arguments.of("seventeen raw keys count before parsing", seventeenRawKeys()),
            Arguments.of("non-object key poisons document", rawJwksJson(List.of(valid.toJSONObject(), "poison"))),
            Arguments.of("missing modulus", rsaJsonWithout("n")), Arguments.of("empty modulus", rsaJsonWith("n", "")),
            Arguments.of("invalid modulus", rsaJsonWith("n", "***")),
            Arguments.of("zero modulus", rsaJsonWith("n", "AA")),
            Arguments.of("leading-zero modulus", rsaJsonWithLeadingZero("n")),
            Arguments.of("missing exponent", rsaJsonWithout("e")), Arguments.of("empty exponent", rsaJsonWith("e", "")),
            Arguments.of("invalid exponent", rsaJsonWith("e", "***")),
            Arguments.of("zero exponent", rsaJsonWith("e", "AA")),
            Arguments.of("leading-zero exponent", rsaJsonWithLeadingZero("e")), Arguments
                .of("one invalid entry poisons document", jwkSetJson(valid, JwtFixtureBuilder.publicJwk("bad/kid"))));
    }

    private static Stream<Arguments> nonStrictJsonJwksPayloads() {
        String key = JSONObjectUtils.toJSONString(JwtFixtureBuilder.publicJwk("requested-kid").toJSONObject());
        String validDocument = "{\"keys\":[" + key + "]}";
        return Stream.of(Arguments.of("unquoted member name", "{keys:[" + key + "]}"),
            Arguments.of("Java comment", "{\"keys\":[/* poison */" + key + "]}"),
            Arguments.of("trailing token", validDocument + " true"),
            Arguments.of("duplicate top-level keys", "{\"keys\":null,\"keys\":[" + key + "]}"));
    }

    private static RSAKey smallRsaKey() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(1024);
            return new RSAKey.Builder((RSAPublicKey)generator.generateKeyPair().getPublic()).keyID("small-key")
                .keyUse(KeyUse.SIGNATURE).keyOperations(Set.of(KeyOperation.VERIFY)).algorithm(JWSAlgorithm.RS256)
                .build();
        } catch (Exception exception) {
            throw new IllegalStateException("无法构建小于 2048 位的 RSA Fixture", exception);
        }
    }

    private static String rsaJsonWithout(String field) {
        Map<String, Object> key = new LinkedHashMap<>(JwtFixtureBuilder.publicJwk("requested-kid").toJSONObject());
        key.remove(field);
        return jwksJson(key);
    }

    private static String rsaJsonWith(String field, String value) {
        Map<String, Object> key = new LinkedHashMap<>(JwtFixtureBuilder.publicJwk("requested-kid").toJSONObject());
        key.put(field, value);
        return jwksJson(key);
    }

    private static String rsaJsonWithLeadingZero(String field) {
        Map<String, Object> key = new LinkedHashMap<>(JwtFixtureBuilder.publicJwk("requested-kid").toJSONObject());
        byte[] value = new Base64URL((String)key.get(field)).decode();
        byte[] padded = new byte[value.length + 1];
        System.arraycopy(value, 0, padded, 1, value.length);
        key.put(field, Base64URL.encode(padded).toString());
        return jwksJson(key);
    }

    private static String jwksJson(Map<String, Object> key) {
        return JSONObjectUtils.toJSONString(Collections.singletonMap("keys", List.of(key)));
    }

    private static String rawJwksJson(List<?> keys) {
        return JSONObjectUtils.toJSONString(Collections.singletonMap("keys", keys));
    }

    private static Map<String, Object> unsupportedJwk() {
        return Map.of("kty", "FUTURE", "kid", "poison");
    }

    private static String seventeenRawKeys() {
        List<Object> keys = new ArrayList<>(IntStream.range(0, 16)
            .mapToObj(index -> JwtFixtureBuilder.publicJwk("raw-key-" + index).toJSONObject()).toList());
        keys.add(unsupportedJwk());
        return rawJwksJson(keys);
    }

    private static String jwkSetJson(JWK... keys) {
        return new JWKSet(new ArrayList<>(List.of(keys))).toString();
    }

    private static String privateJwkSetJson() {
        String payload = "{\"keys\":[" + JwtFixtureBuilder.privateJwk("private-key").toJSONString() + "]}";
        Assertions.assertTrue(payload.contains("\"d\""));
        return payload;
    }

    private static String padToSize(String payload, int size) {
        Assertions.assertTrue(payload.getBytes(StandardCharsets.UTF_8).length <= size);
        return " ".repeat(size - payload.length()) + payload;
    }

    private static void assertRefreshMetrics(ProviderFixture fixture, double success, double failure,
        double suppressed) {
        Assertions.assertEquals(success, counter(fixture, "success"));
        Assertions.assertEquals(failure, counter(fixture, "failure"));
        Assertions.assertEquals(suppressed, counter(fixture, "suppressed"));
    }

    private static double counter(ProviderFixture fixture, String outcome) {
        var counter = fixture.registry().find(GatewayMetrics.JWKS_REFRESH_COUNTER).tag("outcome", outcome).counter();
        return counter == null ? 0 : counter.count();
    }

    private static Clock mutableClock() {
        return TEST_CLOCK;
    }

    private static Scheduler virtualScheduler() {
        return TEST_SCHEDULER;
    }

    @BeforeEach
    void resetDeterministicTimeAndProbe() {
        TEST_CLOCK.reset(JwtFixtureBuilder.NOW);
        boundaryProbe.reset();
    }

    @Test
    void test_gatewayMapsMissingKeyAndUnavailableRefreshWithoutCallingBff() {
        String kid = "gateway-missing-key";
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("different-key"));
        assertGatewayFailure(JwtFixtureBuilder.template().keyId(kid).build(), 401, "GATEWAY_UNAUTHORIZED",
            GatewayOutcome.AUTHENTICATION_FAILED);

        TEST_CLOCK.advance(Duration.ofSeconds(5));
        jwks().scenarios().expectStatus(503);
        assertGatewayFailure(JwtFixtureBuilder.template().keyId("gateway-failure-key").build(), 503,
            "GATEWAY_DEPENDENCY_UNAVAILABLE", GatewayOutcome.DEPENDENCY_UNAVAILABLE);
        jwks().journal().verifyGets(2);
        Assertions.assertEquals(0, bff().journal().count());
    }

    @ParameterizedTest(name = "invalid algorithm {0}")
    @MethodSource("invalidAlgorithms")
    void test_invalidSignedAlgorithmStopsBeforeCacheAndNetwork(JWSAlgorithm algorithm) {
        ProviderFixture fixture = freshProvider();

        assertMissing(fixture.provider(), unsignedJwt(algorithm, "valid-kid"));

        jwks().journal().verifyNoRequests();
        assertRefreshMetrics(fixture, 0, 0, 0);
    }

    @ParameterizedTest(name = "invalid kid [{0}]")
    @MethodSource("invalidKids")
    void test_invalidKidStopsBeforeCacheAndNetwork(String kid) {
        ProviderFixture fixture = freshProvider();

        assertMissing(fixture.provider(), unsignedJwt(JWSAlgorithm.RS256, kid));

        jwks().journal().verifyNoRequests();
        assertRefreshMetrics(fixture, 0, 0, 0);
    }

    @ParameterizedTest(name = "valid kid [{0}]")
    @MethodSource("validKids")
    void test_validKidPassesLocalGate(String kid) {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson(kid));

        assertSelectedKid(fixture.provider(), kid);

        jwks().journal().verifySingleGet();
        assertRefreshMetrics(fixture, 1, 0, 0);
    }

    @Test
    void test_successCooldownIsGlobalAndUsesExactBoundary() throws Exception {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("first-kid"));
        assertSelectedKid(fixture.provider(), "first-kid");

        assertMissing(fixture.provider(), unsignedJwt(JWSAlgorithm.RS256, "second-kid"));
        assertSelectedKid(fixture.provider(), "first-kid");
        fixture.clock().advance(Duration.ofSeconds(5).minusNanos(1));
        assertMissing(fixture.provider(), unsignedJwt(JWSAlgorithm.RS256, "third-kid"));
        assertSelectedKid(fixture.provider(), "first-kid");
        jwks().journal().verifySingleGet();

        fixture.clock().advance(Duration.ofNanos(1));
        RequestBarrier barrier =
            jwks().scenarios().expectJwksBehindBarrier(JwtFixtureBuilder.jwksJson(List.of("first-kid", "third-kid")));
        CompletableFuture<List<JWK>> refreshed =
            fixture.provider().select(unsignedJwt(JWSAlgorithm.RS256, "third-kid")).collectList().toFuture();
        try {
            barrier.awaitRequest(Duration.ofSeconds(1));
            assertSelectedKid(fixture.provider(), "first-kid");
        } finally {
            barrier.release();
        }
        Assertions.assertEquals(List.of("third-kid"),
            refreshed.get(1, TimeUnit.SECONDS).stream().map(JWK::getKeyID).toList());
        assertSelectedKid(fixture.provider(), "first-kid");
        jwks().journal().verifyGets(2);
        assertRefreshMetrics(fixture, 2, 0, 2);
    }

    @Test
    void test_failureCooldownIsGlobalAndUsesExactBoundary() {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectStatus(503);
        assertUnavailable(fixture.provider(), "first-failure-kid");

        assertUnavailable(fixture.provider(), "second-failure-kid");
        fixture.clock().advance(Duration.ofSeconds(5).minusNanos(1));
        assertUnavailable(fixture.provider(), "third-failure-kid");
        jwks().journal().verifySingleGet();

        fixture.clock().advance(Duration.ofNanos(1));
        jwks().scenarios().expectStatus(503);
        assertUnavailable(fixture.provider(), "third-failure-kid");
        jwks().journal().verifyGets(2);
        assertRefreshMetrics(fixture, 0, 2, 2);
    }

    @Test
    void test_eightDifferentKidsShareOneRefreshAndOnePublishedMap() throws Exception {
        ProviderFixture fixture = freshProvider();
        List<String> kids = IntStream.range(0, 8).mapToObj(index -> "parallel-kid-" + index).toList();
        RequestBarrier barrier = jwks().scenarios().expectJwksBehindBarrier(JwtFixtureBuilder.jwksJson(kids));
        List<Mono<JWK>> selections =
            kids.stream().map(kid -> fixture.provider().select(unsignedJwt(JWSAlgorithm.RS256, kid)).single()).toList();

        CompletableFuture<List<JWK>> result = Flux.merge(selections).collectList().toFuture();
        try {
            barrier.awaitRequest(Duration.ofSeconds(1));
            jwks().journal().verifySingleGet();
        } finally {
            barrier.release();
        }

        Assertions.assertEquals(Set.copyOf(kids),
            result.get(1, TimeUnit.SECONDS).stream().map(JWK::getKeyID).collect(java.util.stream.Collectors.toSet()));
        assertRefreshMetrics(fixture, 1, 0, 0);
    }

    @Test
    void test_allRequestSubscribersCanCancelWithoutCancellingSharedRefresh() throws Exception {
        ProviderFixture fixture = freshProvider();
        RequestBarrier barrier = jwks().scenarios()
            .expectJwksBehindBarrier(JwtFixtureBuilder.jwksJson(List.of("cancel-a", "cancel-b", "late-joiner")));
        Disposable first = fixture.provider().select(unsignedJwt(JWSAlgorithm.RS256, "cancel-a")).subscribe();
        Disposable second = fixture.provider().select(unsignedJwt(JWSAlgorithm.RS256, "cancel-b")).subscribe();
        CompletableFuture<List<JWK>> joined;
        try {
            barrier.awaitRequest(Duration.ofSeconds(1));
            first.dispose();
            second.dispose();
            joined = fixture.provider().select(unsignedJwt(JWSAlgorithm.RS256, "late-joiner")).collectList().toFuture();
        } finally {
            first.dispose();
            second.dispose();
            barrier.release();
        }

        Assertions.assertEquals(List.of("late-joiner"),
            joined.get(1, TimeUnit.SECONDS).stream().map(JWK::getKeyID).toList());
        assertSelectedKid(fixture.provider(), "cancel-a");
        jwks().journal().verifySingleGet();
        assertRefreshMetrics(fixture, 1, 0, 0);
    }

    @Test
    void test_knownCachedKeyWorksDuringFailureFlightAndFailureCooldown() throws Exception {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("known-kid"));
        assertSelectedKid(fixture.provider(), "known-kid");
        fixture.clock().advance(Duration.ofSeconds(5));
        RequestBarrier barrier = jwks().scenarios().expectStatusBehindBarrier(503);

        CompletableFuture<List<JWK>> missing =
            fixture.provider().select(unsignedJwt(JWSAlgorithm.RS256, "unknown-kid")).collectList().toFuture();
        try {
            barrier.awaitRequest(Duration.ofSeconds(1));
            assertSelectedKid(fixture.provider(), "known-kid");
        } finally {
            barrier.release();
        }
        ExecutionException failure =
            Assertions.assertThrows(ExecutionException.class, () -> missing.get(1, TimeUnit.SECONDS));
        Assertions.assertInstanceOf(JwksUnavailableException.class, failure.getCause());
        assertSelectedKid(fixture.provider(), "known-kid");
        assertUnavailable(fixture.provider(), "another-unknown-kid");
        jwks().journal().verifyGets(2);
        assertRefreshMetrics(fixture, 1, 1, 1);
    }

    @Test
    void test_refreshReplacesFullMapAndCacheExpiresAtExactBoundary() {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("revoked-kid"));
        assertSelectedKid(fixture.provider(), "revoked-kid");

        fixture.clock().advance(Duration.ofSeconds(5));
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("replacement-kid"));
        assertSelectedKid(fixture.provider(), "replacement-kid");
        assertMissing(fixture.provider(), unsignedJwt(JWSAlgorithm.RS256, "revoked-kid"));

        fixture.clock().advance(Duration.ofMinutes(5).minusNanos(1));
        assertSelectedKid(fixture.provider(), "replacement-kid");
        fixture.clock().advance(Duration.ofNanos(1));
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("replacement-kid"));
        assertSelectedKid(fixture.provider(), "replacement-kid");
        jwks().journal().verifyGets(3);
    }

    @Test
    void test_clockRollbackDuringCompletionDoesNotShortenCooldown() throws Exception {
        ProviderFixture fixture = freshProvider();
        RequestBarrier barrier = jwks().scenarios().expectJwksBehindBarrier(JwtFixtureBuilder.jwksJson("first-kid"));
        CompletableFuture<List<JWK>> first =
            fixture.provider().select(unsignedJwt(JWSAlgorithm.RS256, "first-kid")).collectList().toFuture();
        try {
            barrier.awaitRequest(Duration.ofSeconds(1));
            fixture.clock().reset(JwtFixtureBuilder.NOW.minus(Duration.ofHours(1)));
        } finally {
            barrier.release();
        }
        Assertions.assertEquals(1, first.get(1, TimeUnit.SECONDS).size());

        fixture.clock().reset(JwtFixtureBuilder.NOW.plusSeconds(4));
        List<JWK> suppressed = fixture.provider().select(unsignedJwt(JWSAlgorithm.RS256, "second-kid")).collectList()
            .block(Duration.ofSeconds(1));
        Assertions.assertNotNull(suppressed);
        Assertions.assertTrue(suppressed.isEmpty());
        jwks().journal().verifySingleGet();
        fixture.clock().advance(Duration.ofSeconds(1));
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("second-kid"));
        assertSelectedKid(fixture.provider(), "second-kid");
        jwks().journal().verifyGets(2);
    }

    @ParameterizedTest(name = "valid JWKS with {0} keys")
    @ValueSource(ints = {1, 16})
    void test_oneThroughSixteenValidKeysAreAccepted(int count) {
        ProviderFixture fixture = freshProvider();
        List<String> kids = IntStream.range(0, count).mapToObj(index -> "valid-key-" + index).toList();
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson(kids));

        assertSelectedKid(fixture.provider(), kids.getLast());

        jwks().journal().verifySingleGet();
    }

    @ParameterizedTest(name = "valid JWKS body {0} bytes")
    @ValueSource(ints = {64 * 1024})
    void test_exact64KibValidDocumentIsAccepted(int bodySize) {
        ProviderFixture fixture = freshProvider();
        String kid = "exact-body-kid";
        jwks().scenarios().expectJwks(padToSize(JwtFixtureBuilder.jwksJson(kid), bodySize));

        assertSelectedKid(fixture.provider(), kid);

        jwks().journal().verifySingleGet();
    }

    @Test
    void test_bodyLargerThan64KibFailsClosed() {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectJwks(padToSize(JwtFixtureBuilder.jwksJson("oversize-kid"), 64 * 1024 + 1));

        assertUnavailable(fixture.provider(), "oversize-kid");

        jwks().journal().verifySingleGet();
        assertRefreshMetrics(fixture, 0, 1, 0);
    }

    @Test
    void test_totalBodyDeadlineDoesNotRestartBetweenChunks() {
        ProviderFixture fixture = freshProvider();
        String document = JwtFixtureBuilder.jwksJson("chunked-kid");
        int split = document.length() / 2;
        ChunkBarrier barrier =
            jwks().scenarios().expectChunkedJwks(document.substring(0, split), document.substring(split));
        try {
            StepVerifier.create(fixture.provider().select(unsignedJwt(JWSAlgorithm.RS256, "chunked-kid"))).then(() -> {
                barrier.awaitFirstChunk(Duration.ofSeconds(1));
                fixture.scheduler().advanceTimeBy(Duration.ofSeconds(3));
            }).expectError(JwksUnavailableException.class).verify(Duration.ofSeconds(1));
        } finally {
            barrier.releaseRemaining();
        }
        jwks().journal().verifySingleGet();
    }

    @Test
    void test_redirectIsNotFollowed() {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectRedirect(jwks().uri().resolve("/redirect-target").toString());

        assertUnavailable(fixture.provider(), "redirect-kid");

        jwks().journal().verifySingleGet();
    }

    @ParameterizedTest(name = "HTTP {0} is rejected")
    @ValueSource(ints = {204, 400, 404, 500})
    void test_onlyHttp200IsAccepted(int status) {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectStatus(status);

        assertUnavailable(fixture.provider(), "status-kid");

        jwks().journal().verifySingleGet();
    }

    @ParameterizedTest(name = "invalid document: {0}")
    @MethodSource("invalidJwksPayloads")
    void test_invalidJwksPoisonsWholeDocument(String ignored, String payload) {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectJwks(payload);

        assertUnavailable(fixture.provider(), "requested-kid");

        jwks().journal().verifySingleGet();
    }

    @ParameterizedTest(name = "non-strict JSON: {0}")
    @MethodSource("nonStrictJsonJwksPayloads")
    void test_nonStrictJsonFailsClosedWithoutCacheAndRecoversAtExactBoundary(String ignored, String payload) {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectJwks(payload);

        assertUnavailable(fixture.provider(), "requested-kid");
        assertUnavailable(fixture.provider(), "requested-kid");
        jwks().journal().verifySingleGet();

        fixture.clock().advance(Duration.ofSeconds(5));
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("requested-kid"));
        assertSelectedKid(fixture.provider(), "requested-kid");
        jwks().journal().verifyGets(2);
        assertRefreshMetrics(fixture, 1, 1, 1);
    }

    @Test
    void test_missingRequiredMetadataFailsClosed() {
        ProviderFixture fixture = freshProvider();
        RSAKey key = new RSAKey.Builder(JwtFixtureBuilder.publicJwk("metadata-required")).keyUse(null)
            .keyOperations(null).algorithm(null).build();
        jwks().scenarios().expectJwks(jwkSetJson(key));

        assertUnavailable(fixture.provider(), "metadata-required");

        jwks().journal().verifySingleGet();
    }

    @Test
    void test_empty200DisconnectAndMalformedPayloadFailClosed() {
        assertSingleUnavailableScenario(() -> jwks().scenarios().expectEmpty(200));
        jwks().reset();
        assertSingleUnavailableScenario(() -> jwks().scenarios().expectJwks("{\"keys\":["));
    }

    @Test
    void test_disconnectIsOneActualRefreshWithoutTransportRetry() {
        ProviderFixture fixture = freshProvider();
        jwks().scenarios().expectDisconnect();

        assertUnavailable(fixture.provider(), "disconnect-kid");

        jwks().journal().verifySingleGet();
        assertRefreshMetrics(fixture, 0, 1, 0);
    }

    @Test
    void test_successMetricCallbackFailureCannotChangeSignalOrRetainFlight() {
        ThrowingJwksMeterRegistry registry = new ThrowingJwksMeterRegistry("success");
        ProviderFixture fixture = freshProvider(registry);
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("metric-success-first"));

        Selection first = select(fixture.provider(), "metric-success-first");

        fixture.clock().advance(Duration.ofSeconds(5));
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("metric-success-second"));
        Selection second = select(fixture.provider(), "metric-success-second");
        Assertions.assertAll(() -> Assertions.assertNull(first.failure()),
            () -> Assertions.assertEquals(List.of("metric-success-first"), first.kids()),
            () -> Assertions.assertNull(second.failure()),
            () -> Assertions.assertEquals(List.of("metric-success-second"), second.kids()),
            () -> Assertions.assertEquals(2, registry.callbackAttempts("success")),
            () -> Assertions.assertEquals(0, registry.callbackAttempts("failure")));
        jwks().journal().verifyGets(2);
    }

    @Test
    void test_failureMetricCallbackFailureCannotChangeSignalOrRetainFlight() {
        ThrowingJwksMeterRegistry registry = new ThrowingJwksMeterRegistry("failure");
        ProviderFixture fixture = freshProvider(registry);
        jwks().scenarios().expectStatus(503);

        Selection first = select(fixture.provider(), "metric-failure-first");

        fixture.clock().advance(Duration.ofSeconds(5));
        jwks().scenarios().expectJwks(JwtFixtureBuilder.jwksJson("metric-failure-recovery"));
        Selection recovered = select(fixture.provider(), "metric-failure-recovery");
        Assertions.assertAll(() -> Assertions.assertInstanceOf(JwksUnavailableException.class, first.failure()),
            () -> Assertions.assertNull(recovered.failure()),
            () -> Assertions.assertEquals(List.of("metric-failure-recovery"), recovered.kids()),
            () -> Assertions.assertEquals(1, registry.callbackAttempts("failure")),
            () -> Assertions.assertEquals(1, registry.callbackAttempts("success")));
        jwks().journal().verifyGets(2);
    }

    private void assertSingleUnavailableScenario(Runnable arrange) {
        ProviderFixture fixture = freshProvider();
        arrange.run();
        assertUnavailable(fixture.provider(), "scenario-kid");
        jwks().journal().verifySingleGet();
        jwks().scenarios().verifyComplete();
    }

    private ProviderFixture freshProvider() {
        return freshProvider(new SimpleMeterRegistry());
    }

    private ProviderFixture freshProvider(SimpleMeterRegistry registry) {
        MutableTestClock clock = new MutableTestClock(JwtFixtureBuilder.NOW, ZoneOffset.UTC);
        VirtualTimeScheduler scheduler = VirtualTimeScheduler.create();
        WebClient webClient = WebClient.builder().baseUrl(jwks().uri().toString())
            .codecs(configurer -> configurer.defaultCodecs().maxInMemorySize(64 * 1024)).build();
        ReactiveJwksProvider provider =
            new ReactiveJwksProvider(webClient, clock, scheduler, new GatewayMetrics(registry));
        return new ProviderFixture(provider, clock, scheduler, registry);
    }

    private Selection select(ReactiveJwksProvider provider, String kid) {
        try {
            List<JWK> keys =
                provider.select(unsignedJwt(JWSAlgorithm.RS256, kid)).collectList().block(Duration.ofSeconds(1));
            Assertions.assertNotNull(keys);
            return new Selection(keys.stream().map(JWK::getKeyID).toList(), null);
        } catch (RuntimeException exception) {
            return new Selection(List.of(), reactor.core.Exceptions.unwrap(exception));
        }
    }

    private void assertSelectedKid(ReactiveJwksProvider provider, String kid) {
        StepVerifier.create(provider.select(unsignedJwt(JWSAlgorithm.RS256, kid)))
            .assertNext(key -> Assertions.assertEquals(kid, key.getKeyID())).verifyComplete();
    }

    private void assertMissing(ReactiveJwksProvider provider, SignedJWT signedJwt) {
        StepVerifier.create(provider.select(signedJwt)).verifyComplete();
    }

    private void assertUnavailable(ReactiveJwksProvider provider, String kid) {
        StepVerifier.create(provider.select(unsignedJwt(JWSAlgorithm.RS256, kid)))
            .expectError(JwksUnavailableException.class).verify();
    }

    private void assertGatewayFailure(String token, int status, String code, GatewayOutcome outcome) {
        EntityExchangeResult<byte[]> result = GatewayPublicTestClient.operations(port)
            .defaultHeader("X-Real-IP", "203.0.113.17").build().post().uri(LOGOUT_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
            .header(HttpHeaders.COOKIE, REFRESH_COOKIE).bodyValue("{}").exchange().expectStatus().isEqualTo(status)
            .expectBody().jsonPath("$.code").isEqualTo(code).returnResult();
        String requestId = result.getResponseHeaders().getFirst("X-Request-Id");
        Assertions.assertNotNull(requestId);
        Assertions.assertDoesNotThrow(() -> java.util.UUID.fromString(requestId));
        boundaryProbe.awaitTerminal(Duration.ofSeconds(1));
        Assertions.assertEquals(1, boundaryProbe.entries().size());
        Assertions.assertEquals(outcome, boundaryProbe.entries().getFirst().outcome());
        boundaryProbe.reset();
    }

    private OperationsBffSimulator bff() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }

    private JwksSimulator jwks() {
        return GatewayAutoTestRuntime.jwksSimulator();
    }

    private record ProviderFixture(ReactiveJwksProvider provider, MutableTestClock clock,
        VirtualTimeScheduler scheduler, SimpleMeterRegistry registry) {
    }

    private record Selection(List<String> kids, Throwable failure) {
    }

    private static final class ThrowingJwksMeterRegistry extends SimpleMeterRegistry {
        private final String throwingOutcome;
        private final AtomicBoolean throwOnce = new AtomicBoolean(true);
        private final Map<String, AtomicInteger> callbackAttempts = new ConcurrentHashMap<>();

        private ThrowingJwksMeterRegistry(String throwingOutcome) {
            this.throwingOutcome = throwingOutcome;
        }

        @Override
        protected Counter newCounter(Meter.Id id) {
            Counter delegate = super.newCounter(id);
            if (!GatewayMetrics.JWKS_REFRESH_COUNTER.equals(id.getName())) {
                return delegate;
            }
            String outcome = id.getTag("outcome");
            return new Counter() {
                @Override
                public void increment(double amount) {
                    callbackAttempts.computeIfAbsent(outcome, ignored -> new AtomicInteger()).incrementAndGet();
                    delegate.increment(amount);
                    if (throwingOutcome.equals(outcome) && throwOnce.compareAndSet(true, false)) {
                        throw new IllegalStateException("controlled metric callback failure");
                    }
                }

                @Override
                public double count() {
                    return delegate.count();
                }

                @Override
                public Meter.Id getId() {
                    return delegate.getId();
                }
            };
        }

        private int callbackAttempts(String outcome) {
            AtomicInteger attempts = callbackAttempts.get(outcome);
            return attempts == null ? 0 : attempts.get();
        }
    }
}
