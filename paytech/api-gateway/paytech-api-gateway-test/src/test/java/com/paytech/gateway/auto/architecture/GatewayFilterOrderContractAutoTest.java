package com.paytech.gateway.auto.architecture;

import com.paytech.gateway.routing.ClientRoutingProperties;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.core.Ordered;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.cloud.gateway.filter.NettyWriteResponseFilter;
import org.springframework.cloud.gateway.support.ServerWebExchangeUtils;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.core.io.buffer.NettyDataBufferFactory;
import org.springframework.core.io.buffer.PooledDataBuffer;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import com.paytech.gateway.guard.GatewayAuthenticatedGuardGlobalFilter;
import com.paytech.gateway.guard.GatewayGuardAdmission;
import com.paytech.gateway.guard.concurrency.ConcurrencyCommandFactory;
import com.paytech.gateway.guard.concurrency.RedisReactiveConcurrencyLimiter;
import com.paytech.gateway.guard.ratelimit.RateLimitCommandFactory;
import com.paytech.gateway.guard.ratelimit.RedisReactiveRateLimiter;
import com.paytech.gateway.guard.redis.GuardRedisProperties;
import com.paytech.gateway.guard.redis.ReactiveRedisScriptExecutor;
import com.paytech.gateway.guard.redis.RedisGuardKeyFactory;
import com.paytech.gateway.observability.GatewayMetrics;
import com.paytech.gateway.security.GatewayHmacProperties;
import com.paytech.gateway.security.VersionedHmacDigester;
import com.paytech.gateway.security.identity.GatewayAuthenticatedIdentityGlobalFilter;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.OperationsBffSimulator;
import com.paytech.gateway.web.AuthenticatedOperationsIdentityGlobalFilter;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayGlobalFilterOrder;
import com.paytech.gateway.web.GatewayUpstreamProtocolException;
import com.paytech.gateway.web.OwnerProblemResponseGlobalFilter;
import com.paytech.gateway.idempotency.IdempotencyGlobalFilter;
import com.paytech.gateway.idempotency.IdempotencyKeyPolicy;
import com.paytech.gateway.idempotency.IdempotencyProperties;
import com.paytech.gateway.idempotency.IdempotencyReplayWriter;
import com.paytech.gateway.idempotency.IdempotencyRequestDigest;
import com.paytech.gateway.idempotency.RedisIdempotencyRecordStore;
import com.paytech.gateway.web.OwnerProblemValidator;
import com.paytech.gateway.web.TrustedTerminalResponseHeaders;
import com.paytech.gateway.routing.OperationsRoutingProperties;
import com.paytech.gateway.routing.runtime.GatewayRuntimeRoute;
import com.paytech.gateway.routing.runtime.GatewayRuntimeTarget;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.platform.support.api.enums.GatewayIdempotencyMode;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

import io.netty.buffer.PooledByteBufAllocator;
import io.netty.channel.embedded.EmbeddedChannel;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.netty.Connection;
import reactor.netty.http.client.HttpClient;
import reactor.netty.http.client.HttpClientResponse;
import reactor.test.StepVerifier;

/** Gateway 认证、Guard、Body 与可信下游身份的固定顺序契约。 */
class GatewayFilterOrderContractAutoTest {
    private static final String REQUEST_ID = "request-123";
    private static final OperationsBffSimulator NATIVE_OWNER = new OperationsBffSimulator();
    private static final String VALID_PROBLEM = """
        {"type":"about:blank","title":"Unprocessable Content","detail":"rejected","code":"REJECTED",
         "requestId":"request-123","timestamp":"2026-09-06T10:11:12.123456Z",
         "extension":{"nested":[1,true,{"value":"kept"}]}}
        """;

    @BeforeAll
    static void startNativeOwner() {
        NATIVE_OWNER.start();
    }

    @AfterAll
    static void stopNativeOwner() {
        NATIVE_OWNER.stop();
    }

    @Test
    void test_authenticatedPipelineUsesDistinctIdentityMappingAndDownstreamInjectionBoundaries() {
        Assertions.assertAll(
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 4,
                GatewayGlobalFilterOrder.TRUSTED_REQUEST_CONTEXT),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 5,
                GatewayGlobalFilterOrder.REQUEST_HEADER_LIMIT),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 12, GatewayGlobalFilterOrder.DYNAMIC_ROUTE),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 18, GatewayGlobalFilterOrder.REQUEST_POLICY),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 20,
                GatewayGlobalFilterOrder.OPERATIONS_BOUNDARY),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 30, GatewayGlobalFilterOrder.INGRESS_GUARD),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 40,
                GatewayGlobalFilterOrder.TYPED_AUTHENTICATED_IDENTITY),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 50,
                GatewayGlobalFilterOrder.AUTHENTICATED_GUARD),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 60, GatewayGlobalFilterOrder.CLIENT_API_SCOPE),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 100,
                GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 150, GatewayGlobalFilterOrder.IDEMPOTENCY),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 200,
                GatewayGlobalFilterOrder.UPSTREAM_TIMEOUT),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 300,
                GatewayGlobalFilterOrder.UPSTREAM_REQUEST),
            () -> Assertions.assertEquals(Ordered.HIGHEST_PRECEDENCE + 400,
                GatewayGlobalFilterOrder.TRUSTED_DOWNSTREAM_IDENTITY),
            () -> Assertions.assertEquals(NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER - 1,
                GatewayGlobalFilterOrder.OWNER_PROBLEM_RESPONSE),
            () -> Assertions.assertEquals(-2, GatewayGlobalFilterOrder.OWNER_PROBLEM_RESPONSE),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.INGRESS_GUARD
                < GatewayGlobalFilterOrder.TYPED_AUTHENTICATED_IDENTITY),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.TYPED_AUTHENTICATED_IDENTITY
                < GatewayGlobalFilterOrder.AUTHENTICATED_GUARD),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.AUTHENTICATED_GUARD
                < GatewayGlobalFilterOrder.CLIENT_API_SCOPE),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.CLIENT_API_SCOPE
                < GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.REQUEST_BODY_LIMIT
                < GatewayGlobalFilterOrder.IDEMPOTENCY),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.IDEMPOTENCY
                < GatewayGlobalFilterOrder.UPSTREAM_TIMEOUT),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.UPSTREAM_TIMEOUT
                < GatewayGlobalFilterOrder.UPSTREAM_REQUEST),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.UPSTREAM_REQUEST
                < GatewayGlobalFilterOrder.TRUSTED_DOWNSTREAM_IDENTITY),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.TRUSTED_DOWNSTREAM_IDENTITY
                < GatewayGlobalFilterOrder.NETTY_ROUTING),
            () -> Assertions.assertTrue(GatewayGlobalFilterOrder.OWNER_PROBLEM_RESPONSE
                < NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER),
            () -> Assertions.assertTrue(NettyWriteResponseFilter.WRITE_RESPONSE_FILTER_ORDER
                < GatewayGlobalFilterOrder.OPERATIONS_RESPONSE_CONTRACT),
            () -> Assertions.assertNotEquals(GatewayGlobalFilterOrder.TYPED_AUTHENTICATED_IDENTITY,
                GatewayGlobalFilterOrder.TRUSTED_DOWNSTREAM_IDENTITY));
    }

    @Test
    void test_productionIdentityAndGuardFiltersReportTheirExactSlots() {
        byte[] hmacMaterial = new byte[32];
        Arrays.fill(hmacMaterial, (byte)7);
        GatewayAuthenticatedIdentityGlobalFilter mapper = new GatewayAuthenticatedIdentityGlobalFilter(
            new VersionedHmacDigester(new GatewayHmacProperties(new GatewayHmacProperties.Secret("order-test-v1",
                Base64.getEncoder().encodeToString(hmacMaterial)))));
        GuardRedisProperties redisProperties = new GuardRedisProperties("gateway:test:order");
        RedisGuardKeyFactory keyFactory = new RedisGuardKeyFactory(redisProperties);
        ReactiveRedisScriptExecutor scriptExecutor =
            new ReactiveRedisScriptExecutor(new LettuceConnectionFactory("127.0.0.1", 1));
        GatewayGuardAdmission admission = new GatewayGuardAdmission(
            new RedisReactiveRateLimiter(scriptExecutor, redisProperties),
            new RedisReactiveConcurrencyLimiter(scriptExecutor, redisProperties),
            new GatewayErrorWriter(ServerCodecConfigurer.create()), new GatewayMetrics(new SimpleMeterRegistry()));
        GatewayAuthenticatedGuardGlobalFilter guard = new GatewayAuthenticatedGuardGlobalFilter(
            new RateLimitCommandFactory(keyFactory), new ConcurrencyCommandFactory(keyFactory), admission);
        AuthenticatedOperationsIdentityGlobalFilter downstream = new AuthenticatedOperationsIdentityGlobalFilter();
        OwnerProblemResponseGlobalFilter ownerProblems = new OwnerProblemResponseGlobalFilter(new OwnerProblemValidator(),
            new TrustedTerminalResponseHeaders(new OperationsRoutingProperties("https://operations-console.test",
                Duration.ofSeconds(1)), new ClientRoutingProperties(false, null)));

        IdempotencyProperties idempotencyProperties =
            new IdempotencyProperties(Duration.ofHours(72), Duration.ofSeconds(10), 65_536, null);
        IdempotencyGlobalFilter idempotency = new IdempotencyGlobalFilter(new IdempotencyKeyPolicy(),
            new IdempotencyRequestDigest(),
            new RedisIdempotencyRecordStore(scriptExecutor, idempotencyProperties, redisProperties),
            new IdempotencyReplayWriter(), idempotencyProperties, new GatewayErrorWriter(ServerCodecConfigurer.create()));

        Assertions.assertAll(
            () -> Assertions.assertEquals(GatewayGlobalFilterOrder.TYPED_AUTHENTICATED_IDENTITY, mapper.getOrder()),
            () -> Assertions.assertEquals(GatewayGlobalFilterOrder.IDEMPOTENCY, idempotency.getOrder()),
            () -> Assertions.assertEquals(GatewayGlobalFilterOrder.AUTHENTICATED_GUARD, guard.getOrder()),
            () -> Assertions.assertEquals(GatewayGlobalFilterOrder.TRUSTED_DOWNSTREAM_IDENTITY,
                downstream.getOrder()),
            () -> Assertions.assertEquals(GatewayGlobalFilterOrder.OWNER_PROBLEM_RESPONSE,
                ownerProblems.getOrder()));
    }

    @Test
    void test_validatorAcceptsStrictProblemAndOnlyTheOperationsConsoleLegacyTarget() {
        OwnerProblemValidator validator = new OwnerProblemValidator();
        HttpHeaders problemHeaders = headers("application/problem+json; charset=UTF-8", REQUEST_ID);
        HttpHeaders legacyHeaders = headers("application/json", REQUEST_ID);
        String legacy = "{\"code\":\"OLD\",\"message\":\"old\",\"requestId\":\"request-123\","
            + "\"timestamp\":\"2026-09-06T10:11:12.123456789Z\"}";

        Assertions.assertDoesNotThrow(() -> validator.validate(HttpStatus.UNPROCESSABLE_ENTITY, problemHeaders,
            VALID_PROBLEM.getBytes(java.nio.charset.StandardCharsets.UTF_8), REQUEST_ID, false));
        Assertions.assertDoesNotThrow(() -> validator.validate(HttpStatus.UNPROCESSABLE_ENTITY, problemHeaders,
            insertBeforeEnd(VALID_PROBLEM,
                ",\"errors\":[{\"field\":\"name\",\"message\":\"is required\"}]")
                .getBytes(java.nio.charset.StandardCharsets.UTF_8), REQUEST_ID, false));
        Assertions.assertDoesNotThrow(() -> validator.validate(HttpStatus.BAD_GATEWAY, legacyHeaders,
            legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8), REQUEST_ID, true));
        assertSanitizedFailure(() -> validator.validate(HttpStatus.BAD_GATEWAY, legacyHeaders,
            legacy.getBytes(java.nio.charset.StandardCharsets.UTF_8), REQUEST_ID, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProblemBodies")
    void test_validatorRejectsEveryInvalidProblemShape(String scenario, String body) {
        OwnerProblemValidator validator = new OwnerProblemValidator();
        assertSanitizedFailure(() -> validator.validate(HttpStatus.UNPROCESSABLE_ENTITY,
            headers("application/problem+json", REQUEST_ID),
            body.getBytes(java.nio.charset.StandardCharsets.UTF_8), REQUEST_ID, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidProblemHeaders")
    void test_validatorRejectsInvalidMediaAndResponseIds(String scenario, HttpHeaders headers) {
        OwnerProblemValidator validator = new OwnerProblemValidator();
        assertSanitizedFailure(() -> validator.validate(HttpStatus.UNPROCESSABLE_ENTITY, headers,
            VALID_PROBLEM.getBytes(java.nio.charset.StandardCharsets.UTF_8), REQUEST_ID, false));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("invalidLegacyBodies")
    void test_validatorRejectsInvalidLegacyShapes(String scenario, String body) {
        OwnerProblemValidator validator = new OwnerProblemValidator();
        assertSanitizedFailure(() -> validator.validate(HttpStatus.BAD_GATEWAY,
            headers("application/json", REQUEST_ID), body.getBytes(java.nio.charset.StandardCharsets.UTF_8),
            REQUEST_ID, true));
    }

    @Test
    void test_headValidatesOnlyProblemMediaAndMatchingRequestId() {
        OwnerProblemValidator validator = new OwnerProblemValidator();

        Assertions.assertDoesNotThrow(() -> validator.validateHead(HttpStatus.NOT_FOUND,
            headers("APPLICATION/PROBLEM+JSON", REQUEST_ID), REQUEST_ID));
        assertSanitizedFailure(() -> validator.validateHead(HttpStatus.NOT_FOUND,
            headers("application/json", REQUEST_ID), REQUEST_ID));
        assertSanitizedFailure(() -> validator.validateHead(HttpStatus.NOT_FOUND,
            headers("application/problem+json", "other-request"), REQUEST_ID));
    }

    @Test
    void test_validatorRejectsInvalidUtf8AndOwnerBodyPairThatDiffersFromTrustedId() {
        OwnerProblemValidator validator = new OwnerProblemValidator();
        byte[] invalidUtf8 = VALID_PROBLEM.getBytes(StandardCharsets.UTF_8);
        int detail = indexOf(invalidUtf8, "rejected".getBytes(StandardCharsets.UTF_8));
        invalidUtf8[detail] = (byte)0xC3;
        assertSanitizedFailure(() -> validator.validate(HttpStatus.UNPROCESSABLE_ENTITY,
            headers("application/problem+json", REQUEST_ID), invalidUtf8, REQUEST_ID, false));

        byte[] utf16Content = VALID_PROBLEM.getBytes(StandardCharsets.UTF_16LE);
        byte[] utf16 = new byte[utf16Content.length + 2];
        utf16[0] = (byte)0xff;
        utf16[1] = (byte)0xfe;
        System.arraycopy(utf16Content, 0, utf16, 2, utf16Content.length);
        assertSanitizedFailure(() -> validator.validate(HttpStatus.UNPROCESSABLE_ENTITY,
            headers("application/problem+json", REQUEST_ID), utf16, REQUEST_ID, false));

        String ownerRequestId = "other-request";
        assertSanitizedFailure(() -> validator.validate(HttpStatus.UNPROCESSABLE_ENTITY,
            headers("application/problem+json", ownerRequestId),
            VALID_PROBLEM.replace(REQUEST_ID, ownerRequestId).getBytes(StandardCharsets.UTF_8), REQUEST_ID, false));
    }

    @Test
    void test_trustedTerminalRequestIdAccessorSharesTheProfileInvariantAndKeepsMissingCompatibility() {
        TrustedTerminalResponseHeaders trusted = new TrustedTerminalResponseHeaders(
            new OperationsRoutingProperties("https://operations-console.test", Duration.ofSeconds(1)), new ClientRoutingProperties(false, null));
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/local"));
        Assertions.assertTrue(trusted.trustedRequestId(exchange).isEmpty());
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, REQUEST_ID);
        Assertions.assertEquals(REQUEST_ID, trusted.trustedRequestId(exchange).orElseThrow());
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "bad/request");
        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> trusted.trustedRequestId(exchange));
        Assertions.assertFalse(failure.getMessage().contains("bad/request"));
    }

    @Test
    void test_filterAcceptsExactCeilingFromMonoReplaysBytesAndReleasesPooledInput() {
        byte[] body = sizedProblem(OwnerProblemResponseGlobalFilter.MAX_OWNER_ERROR_BODY_BYTES);
        PooledDataBuffer input = pooled(body);
        MockServerWebExchange exchange = ownerExchange(HttpMethod.POST, HttpStatus.BAD_GATEWAY);

        ownerFilter().filter(exchange,
            filtered -> filtered.getResponse().writeWith(Mono.just(input))).block(Duration.ofSeconds(2));

        Assertions.assertAll(() -> Assertions.assertFalse(input.isAllocated()),
            () -> Assertions.assertArrayEquals(body,
                exchange.getResponse().getBodyAsString().block().getBytes(StandardCharsets.UTF_8)),
            () -> Assertions.assertEquals(HttpStatus.BAD_GATEWAY, exchange.getResponse().getStatusCode()));
    }

    @Test
    void test_filterRejectsOneByteOverCeilingFromMonoAndReleasesPooledInput() {
        byte[] body = sizedProblem(OwnerProblemResponseGlobalFilter.MAX_OWNER_ERROR_BODY_BYTES + 1);
        PooledDataBuffer input = pooled(body);
        MockServerWebExchange exchange = ownerExchange(HttpMethod.POST, HttpStatus.BAD_GATEWAY);

        assertSanitizedFailure(() -> ownerFilter().filter(exchange,
            filtered -> filtered.getResponse().writeWith(Mono.just(input))).block(Duration.ofSeconds(2)));

        Assertions.assertFalse(input.isAllocated());
    }

    @Test
    void test_filterValidatesWriteAndFlushWithAndReleasesEveryPooledChunk() {
        byte[] body = VALID_PROBLEM.getBytes(StandardCharsets.UTF_8);
        int split = body.length / 2;
        PooledDataBuffer first = pooled(Arrays.copyOfRange(body, 0, split));
        PooledDataBuffer second = pooled(Arrays.copyOfRange(body, split, body.length));
        MockServerWebExchange exchange = ownerExchange(HttpMethod.POST, HttpStatus.UNPROCESSABLE_ENTITY);

        ownerFilter().filter(exchange, filtered -> filtered.getResponse()
            .writeAndFlushWith(Flux.just(Flux.just(first), Flux.just(second)))).block(Duration.ofSeconds(2));

        Assertions.assertAll(() -> Assertions.assertFalse(first.isAllocated()),
            () -> Assertions.assertFalse(second.isAllocated()),
            () -> Assertions.assertArrayEquals(body,
                exchange.getResponse().getBodyAsString().block().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void test_filterUsesOnlyNativeOwnerHeadersWhenGatewayAndOwnerIdsWereMerged() {
        byte[] body = VALID_PROBLEM.getBytes(StandardCharsets.UTF_8);
        PooledDataBuffer input = pooled(body);
        MockServerWebExchange exchange = ownerExchange(HttpMethod.POST, HttpStatus.UNPROCESSABLE_ENTITY);
        exchange.getResponse().getHeaders().put("X-Request-Id",
            new ArrayList<>(List.of(REQUEST_ID, REQUEST_ID)));

        ownerFilter().filter(exchange,
            filtered -> filtered.getResponse().writeWith(Mono.just(input))).block(Duration.ofSeconds(2));

        Assertions.assertAll(() -> Assertions.assertFalse(input.isAllocated()),
            () -> Assertions.assertEquals(List.of(REQUEST_ID, REQUEST_ID),
                exchange.getResponse().getHeaders().get("X-Request-Id")),
            () -> Assertions.assertArrayEquals(body,
                exchange.getResponse().getBodyAsString().block().getBytes(StandardCharsets.UTF_8)));
    }

    @Test
    void test_filterRejectsMissingNativeOwnerIdEvenWhenGatewayHasATrustedId() {
        PooledDataBuffer input = pooled(VALID_PROBLEM.getBytes(StandardCharsets.UTF_8));
        HttpClientResponse nativeResponse = nativeResponse(HttpStatus.UNPROCESSABLE_ENTITY,
            headers("application/problem+json", null));
        MockServerWebExchange exchange = ownerExchange(HttpMethod.POST, HttpStatus.UNPROCESSABLE_ENTITY,
            nativeResponse);

        assertSanitizedFailure(() -> ownerFilter().filter(exchange,
            filtered -> filtered.getResponse().writeWith(Mono.just(input))).block(Duration.ofSeconds(2)));

        Assertions.assertFalse(input.isAllocated());
    }

    @Test
    void test_filterUsesNativeOwnerStatusForProblemTitleValidation() {
        byte[] body = VALID_PROBLEM.replace("Unprocessable Content", "Conflict")
            .getBytes(StandardCharsets.UTF_8);
        PooledDataBuffer input = pooled(body);
        HttpClientResponse nativeResponse = nativeResponse(HttpStatus.CONFLICT,
            headers("application/problem+json", REQUEST_ID));
        MockServerWebExchange exchange = ownerExchange(HttpMethod.POST, HttpStatus.UNPROCESSABLE_ENTITY,
            nativeResponse);

        ownerFilter().filter(exchange,
            filtered -> filtered.getResponse().writeWith(Mono.just(input))).block(Duration.ofSeconds(2));

        Assertions.assertAll(() -> Assertions.assertFalse(input.isAllocated()),
            () -> Assertions.assertArrayEquals(body,
                exchange.getResponse().getBodyAsString().block().getBytes(StandardCharsets.UTF_8)));

        PooledDataBuffer invalidForNativeStatus = pooled(VALID_PROBLEM.getBytes(StandardCharsets.UTF_8));
        MockServerWebExchange misleadingSuccess = ownerExchange(HttpMethod.POST, HttpStatus.OK,
            nativeResponse);
        assertSanitizedFailure(() -> ownerFilter().filter(misleadingSuccess,
            filtered -> filtered.getResponse().writeWith(Mono.just(invalidForNativeStatus)))
            .block(Duration.ofSeconds(2)));
        Assertions.assertFalse(invalidForNativeStatus.isAllocated());
    }

    @Test
    void test_filterFailsClosedForMissingOrWrongNativeOwnerResponseOnMergedErrors() {
        MockServerWebExchange missing = ownerExchange(HttpMethod.POST, HttpStatus.BAD_GATEWAY);
        missing.getAttributes().remove(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR);
        MockServerWebExchange wrongType = ownerExchange(HttpMethod.POST, HttpStatus.BAD_GATEWAY);
        wrongType.getAttributes().put(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR, "not-a-native-response");

        Assertions.assertAll(
            () -> assertSanitizedFailure(() -> ownerFilter().filter(missing,
                filtered -> filtered.getResponse().writeWith(Mono.just(filtered.getResponse().bufferFactory()
                    .wrap(VALID_PROBLEM.getBytes(StandardCharsets.UTF_8))))).block(Duration.ofSeconds(2))),
            () -> assertSanitizedFailure(() -> ownerFilter().filter(wrongType,
                filtered -> filtered.getResponse().writeWith(Mono.just(filtered.getResponse().bufferFactory()
                    .wrap(VALID_PROBLEM.getBytes(StandardCharsets.UTF_8))))).block(Duration.ofSeconds(2))));
    }

    @Test
    void test_filterSanitizesWrongNativeOwnerResponseBeforeNettyWriteCastsIt() {
        MockServerWebExchange exchange = ownerExchange(HttpMethod.POST, HttpStatus.BAD_GATEWAY);
        exchange.getAttributes().put(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR, "not-a-native-response");
        EmbeddedChannel channel = new EmbeddedChannel();
        exchange.getAttributes().put(ServerWebExchangeUtils.CLIENT_RESPONSE_CONN_ATTR, Connection.from(channel));
        NettyWriteResponseFilter nettyWrite = new NettyWriteResponseFilter(List.of(), null);

        try {
            assertSanitizedFailure(() -> ownerFilter().filter(exchange,
                filtered -> nettyWrite.filter(filtered, routed -> Mono.empty())).block(Duration.ofSeconds(2)));
            Assertions.assertEquals("not-a-native-response",
                exchange.getAttributes().get(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR));
        } finally {
            channel.finishAndReleaseAll();
        }
    }

    @Test
    void test_writeAndFlushReleasesReplayWhenCommitFailsBeforeDelegateBodySubscription() {
        TrackingNettyDataBufferFactory factory = new TrackingNettyDataBufferFactory();
        MockServerHttpResponse delegate = new MockServerHttpResponse(factory);
        AtomicBoolean delegateBodySubscribed = new AtomicBoolean();
        delegate.setWriteHandler(body -> Mono.defer(() -> {
            delegateBodySubscribed.set(true);
            return body.then();
        }));
        IllegalStateException commitFailure = new IllegalStateException("expected commit failure");
        delegate.beforeCommit(() -> Mono.error(commitFailure));
        delegate.setStatusCode(HttpStatus.UNPROCESSABLE_ENTITY);
        delegate.getHeaders().put(HttpHeaders.CONTENT_TYPE, List.of("application/problem+json"));
        delegate.getHeaders().put("X-Request-Id", List.of(REQUEST_ID));
        MockServerWebExchange base = ownerExchange(HttpMethod.POST, HttpStatus.UNPROCESSABLE_ENTITY);
        ServerWebExchange exchange = base.mutate().response(delegate).build();
        PooledDataBuffer input = pooled(VALID_PROBLEM.getBytes(StandardCharsets.UTF_8));

        IllegalStateException failure = Assertions.assertThrows(IllegalStateException.class,
            () -> ownerFilter().filter(exchange, filtered -> filtered.getResponse()
                .writeAndFlushWith(Mono.just(Mono.just(input)))).block(Duration.ofSeconds(2)));
        boolean everyReplayBufferReleased = factory.everyWrappedBufferReleased();
        int replayBufferCount = factory.wrappedBufferCount();
        factory.releaseAll();

        Assertions.assertAll(() -> Assertions.assertSame(commitFailure, failure),
            () -> Assertions.assertFalse(input.isAllocated()),
            () -> Assertions.assertFalse(delegateBodySubscribed.get()),
            () -> Assertions.assertEquals(1, replayBufferCount),
            () -> Assertions.assertTrue(everyReplayBufferReleased));
    }

    @Test
    void test_filterHeadDrainsZeroBytesKeepsMetadataAndRejectsActualContent() {
        MockServerWebExchange valid = ownerExchange(HttpMethod.HEAD, HttpStatus.NOT_FOUND);
        valid.getResponse().getHeaders().setContentLength(37);
        PooledDataBuffer empty = pooledEmpty();

        ownerFilter().filter(valid,
            filtered -> filtered.getResponse().writeWith(Mono.just(empty))).block(Duration.ofSeconds(2));

        Assertions.assertAll(() -> Assertions.assertFalse(empty.isAllocated()),
            () -> Assertions.assertEquals(37, valid.getResponse().getHeaders().getContentLength()),
            () -> Assertions.assertEquals(HttpStatus.NOT_FOUND, valid.getResponse().getStatusCode()));

        MockServerWebExchange invalidMetadata = ownerExchange(HttpMethod.HEAD, HttpStatus.NOT_FOUND);
        invalidMetadata.getAttributes().put(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR,
            nativeResponse(HttpStatus.NOT_FOUND, headers("application/json", REQUEST_ID)));
        PooledDataBuffer invalidMetadataBody = pooledEmpty();
        assertSanitizedFailure(() -> ownerFilter().filter(invalidMetadata,
            filtered -> filtered.getResponse().writeWith(Mono.just(invalidMetadataBody)))
            .block(Duration.ofSeconds(2)));
        Assertions.assertFalse(invalidMetadataBody.isAllocated());

        MockServerWebExchange invalid = ownerExchange(HttpMethod.HEAD, HttpStatus.NOT_FOUND);
        PooledDataBuffer actualContent = pooled(new byte[] {1});
        assertSanitizedFailure(() -> ownerFilter().filter(invalid,
            filtered -> filtered.getResponse().writeAndFlushWith(Mono.just(Mono.just(actualContent))))
            .block(Duration.ofSeconds(2)));
        Assertions.assertFalse(actualContent.isAllocated());
    }

    @Test
    void test_filterCancellationReleasesAccumulatedPooledInputWithoutConvertingCancellationToFailure() {
        MockServerWebExchange exchange = ownerExchange(HttpMethod.POST, HttpStatus.BAD_GATEWAY);
        reactor.test.publisher.TestPublisher<org.springframework.core.io.buffer.DataBuffer> source =
            reactor.test.publisher.TestPublisher.create();
        PooledDataBuffer input = pooled("partial".getBytes(StandardCharsets.UTF_8));
        Mono<Void> result = ownerFilter().filter(exchange,
            filtered -> filtered.getResponse().writeWith(source.flux()));

        StepVerifier.create(result).then(() -> source.next(input)).thenCancel().verify(Duration.ofSeconds(2));

        Assertions.assertFalse(input.isAllocated());
    }

    @Test
    void test_filterLeavesLocalErrorsAndSuccessesUnbuffered() {
        MockServerWebExchange local = MockServerWebExchange.from(MockServerHttpRequest.post("/local"));
        local.getResponse().setStatusCode(HttpStatus.INTERNAL_SERVER_ERROR);
        byte[] invalidLocalBody = "local-not-problem".getBytes(StandardCharsets.UTF_8);
        ownerFilter().filter(local, filtered -> filtered.getResponse()
            .writeWith(Mono.just(filtered.getResponse().bufferFactory().wrap(invalidLocalBody))))
            .block(Duration.ofSeconds(2));

        MockServerWebExchange success = ownerExchange(HttpMethod.POST, HttpStatus.OK);
        byte[] successBody = "streaming-success".getBytes(StandardCharsets.UTF_8);
        ownerFilter().filter(success, filtered -> filtered.getResponse()
            .writeAndFlushWith(Flux.just(Mono.just(filtered.getResponse().bufferFactory().wrap(successBody)))))
            .block(Duration.ofSeconds(2));

        Assertions.assertAll(() -> Assertions.assertEquals("local-not-problem",
            local.getResponse().getBodyAsString().block()), () -> Assertions.assertEquals("streaming-success",
                success.getResponse().getBodyAsString().block()));
    }

    private static Stream<Arguments> invalidProblemBodies() {
        Stream<Arguments> structural = Stream.of(
            Arguments.of("empty", ""), Arguments.of("non object", "[]"), Arguments.of("malformed", "{"),
            Arguments.of("trailing", VALID_PROBLEM + "{}"),
            Arguments.of("duplicate", insertAfterObjectStart(VALID_PROBLEM, "\"code\":\"first\",")),
            Arguments.of("escaped duplicate", insertAfterObjectStart(VALID_PROBLEM, "\"c\\u006fde\":\"first\",")),
            Arguments.of("bad request id", VALID_PROBLEM.replace(REQUEST_ID, "bad/request")),
            Arguments.of("mismatched body id", VALID_PROBLEM.replace(REQUEST_ID, "other-request")),
            Arguments.of("bad type", VALID_PROBLEM.replace("about:blank", "https://owner.test/problem")),
            Arguments.of("bad title", VALID_PROBLEM.replace("Unprocessable Content", "Bad Request")),
            Arguments.of("bad timestamp fraction", VALID_PROBLEM.replace(".123456Z", ".123Z")),
            Arguments.of("bad timestamp calendar", VALID_PROBLEM.replace("2026-09-06", "2026-02-30")),
            Arguments.of("forbidden status", insertBeforeEnd(VALID_PROBLEM, ",\"status\":422")),
            Arguments.of("forbidden instance", insertBeforeEnd(VALID_PROBLEM, ",\"instance\":\"/x\"")),
            Arguments.of("forbidden message", insertBeforeEnd(VALID_PROBLEM, ",\"message\":\"leak\"")),
            Arguments.of("null errors", insertBeforeEnd(VALID_PROBLEM, ",\"errors\":null")),
            Arguments.of("empty errors", insertBeforeEnd(VALID_PROBLEM, ",\"errors\":[]")),
            Arguments.of("errors not array", insertBeforeEnd(VALID_PROBLEM, ",\"errors\":{}")),
            Arguments.of("null error item", insertBeforeEnd(VALID_PROBLEM, ",\"errors\":[null]")),
            Arguments.of("missing error field", insertBeforeEnd(VALID_PROBLEM,
                ",\"errors\":[{\"message\":\"bad\"}]")),
            Arguments.of("blank error message", insertBeforeEnd(VALID_PROBLEM,
                ",\"errors\":[{\"field\":\"name\",\"message\":\" \"}]")),
            Arguments.of("unknown error member", insertBeforeEnd(VALID_PROBLEM,
                ",\"errors\":[{\"field\":\"name\",\"message\":\"bad\",\"value\":\"leak\"}]")));
        return Stream.concat(structural, requiredMemberFailures());
    }

    private static Stream<Arguments> requiredMemberFailures() {
        Map<String, String> values = Map.of("type", "about:blank", "title", "Unprocessable Content", "detail",
            "rejected", "code", "REJECTED", "requestId", REQUEST_ID, "timestamp",
            "2026-09-06T10:11:12.123456Z");
        return values.entrySet().stream().flatMap(entry -> {
            String exact = "\"" + entry.getKey() + "\":\"" + entry.getValue() + "\",";
            return Stream.of(Arguments.of("missing required " + entry.getKey(), VALID_PROBLEM.replace(exact, "")),
                Arguments.of("null required " + entry.getKey(),
                    VALID_PROBLEM.replace(exact, "\"" + entry.getKey() + "\":null,")),
                Arguments.of("wrong required " + entry.getKey(),
                    VALID_PROBLEM.replace(exact, "\"" + entry.getKey() + "\":1,")),
                Arguments.of("blank required " + entry.getKey(),
                    VALID_PROBLEM.replace(exact, "\"" + entry.getKey() + "\":\" \",")));
        });
    }

    private static Stream<Arguments> invalidProblemHeaders() {
        return Stream.of(Arguments.of("missing media", headers(null, REQUEST_ID)),
            Arguments.of("duplicate media", headers(List.of("application/problem+json", "application/problem+json"),
                List.of(REQUEST_ID))),
            Arguments.of("coalesced media", headers("application/problem+json, application/problem+json", REQUEST_ID)),
            Arguments.of("wrong media", headers("application/json", REQUEST_ID)),
            Arguments.of("malformed media", headers("application/problem+json; charset=", REQUEST_ID)),
            Arguments.of("ignored bare media parameter", headers("application/problem+json;garbage", REQUEST_ID)),
            Arguments.of("duplicate media parameter",
                headers("application/problem+json;charset=UTF-8;charset=UTF-8", REQUEST_ID)),
            Arguments.of("non utf8 media", headers("application/problem+json;charset=ISO-8859-1", REQUEST_ID)),
            Arguments.of("unknown media parameter", headers("application/problem+json;profile=x", REQUEST_ID)),
            Arguments.of("missing id", headers(List.of("application/problem+json"), List.of())),
            Arguments.of("duplicate id", headers(List.of("application/problem+json"), List.of(REQUEST_ID, REQUEST_ID))),
            Arguments.of("coalesced id", headers("application/problem+json", REQUEST_ID + ",other")),
            Arguments.of("bad id", headers("application/problem+json", "bad/id")),
            Arguments.of("mismatched id", headers("application/problem+json", "other-request")));
    }

    private static Stream<Arguments> invalidLegacyBodies() {
        String valid = "{\"code\":\"OLD\",\"message\":\"old\",\"requestId\":\"request-123\","
            + "\"timestamp\":\"2026-09-06T10:11:12Z\"}";
        return Stream.of(Arguments.of("unknown field", insertBeforeEnd(valid, ",\"detail\":\"no\"")),
            Arguments.of("missing field", removeMember(valid, "\"message\":\"old\",")),
            Arguments.of("null field", valid.replace("\"message\":\"old\"", "\"message\":null")),
            Arguments.of("blank field", valid.replace("\"code\":\"OLD\"", "\"code\":\" \"")),
            Arguments.of("duplicate", insertAfterObjectStart(valid, "\"code\":\"first\",")),
            Arguments.of("trailing", valid + "{}"),
            Arguments.of("offset time", valid.replace("Z", "+00:00")),
            Arguments.of("lowercase z", valid.replace("Z", "z")),
            Arguments.of("bad fraction", valid.replace("12Z", "12.1234567890Z")),
            Arguments.of("bad calendar", valid.replace("2026-09-06", "2026-02-30")),
            Arguments.of("body id mismatch", valid.replace(REQUEST_ID, "other-request")));
    }

    private static HttpHeaders headers(String contentType, String requestId) {
        return headers(contentType == null ? List.of() : List.of(contentType),
            requestId == null ? List.of() : List.of(requestId));
    }

    private static HttpHeaders headers(List<String> contentTypes, List<String> requestIds) {
        HttpHeaders headers = new HttpHeaders();
        headers.put(HttpHeaders.CONTENT_TYPE, contentTypes);
        headers.put("X-Request-Id", requestIds);
        return headers;
    }

    private static String removeMember(String body, String member) {
        return body.replace(member, "");
    }

    private static String insertBeforeEnd(String body, String member) {
        int end = body.lastIndexOf('}');
        return body.substring(0, end) + member + body.substring(end);
    }

    private static String insertAfterObjectStart(String body, String member) {
        int start = body.indexOf('{') + 1;
        return body.substring(0, start) + member + body.substring(start);
    }

    private static int indexOf(byte[] bytes, byte[] marker) {
        for (int offset = 0; offset <= bytes.length - marker.length; offset++) {
            if (Arrays.equals(bytes, offset, offset + marker.length, marker, 0, marker.length)) {
                return offset;
            }
        }
        throw new IllegalArgumentException("marker missing");
    }

    private static void assertSanitizedFailure(org.junit.jupiter.api.function.Executable action) {
        GatewayUpstreamProtocolException failure =
            Assertions.assertThrows(GatewayUpstreamProtocolException.class, action);
        Assertions.assertNull(failure.getCause());
        Assertions.assertFalse(failure.getMessage().contains("request-123"));
    }

    private static OwnerProblemResponseGlobalFilter ownerFilter() {
        return new OwnerProblemResponseGlobalFilter(new OwnerProblemValidator(),
            new TrustedTerminalResponseHeaders(new OperationsRoutingProperties("https://operations-console.test",
                Duration.ofSeconds(1)), new ClientRoutingProperties(false, null)));
    }

    private static MockServerWebExchange ownerExchange(HttpMethod method, HttpStatus status) {
        return ownerExchange(method, status, nativeResponse(status,
            headers("application/problem+json", REQUEST_ID)));
    }

    private static MockServerWebExchange ownerExchange(HttpMethod method, HttpStatus status,
        HttpClientResponse nativeResponse) {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.method(method, "/owner"));
        exchange.getAttributes().put(GatewayExchangeAttributes.RUNTIME_ROUTE,
            new GatewayRuntimeRoute("owner-test", GatewayRouteChannel.OPERATIONS, method, "/owner", "/owner",
                new GatewayRuntimeTarget("operations-console-bff", GatewayRouteChannel.OPERATIONS, "operations-console-bff"), "PUBLIC", "PUBLIC", null, null, "DROP", "NONE", Set.of(), Set.of(),
                Set.of(), 0, Duration.ofSeconds(1), Duration.ofSeconds(1), null, null, GatewayIdempotencyMode.NONE));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, REQUEST_ID);
        exchange.getAttributes().put(ServerWebExchangeUtils.CLIENT_RESPONSE_ATTR, nativeResponse);
        exchange.getResponse().setStatusCode(status);
        exchange.getResponse().getHeaders().put(HttpHeaders.CONTENT_TYPE, List.of("application/problem+json"));
        exchange.getResponse().getHeaders().put("X-Request-Id", List.of(REQUEST_ID));
        return exchange;
    }

    private static synchronized HttpClientResponse nativeResponse(HttpStatus status, HttpHeaders headers) {
        NATIVE_OWNER.reset();
        String[] contentTypes = headers.getOrEmpty(HttpHeaders.CONTENT_TYPE).toArray(String[]::new);
        BffScenarioStore.Response scenario = BffScenarioStore.Response.raw(status.value(), new byte[0], contentTypes);
        for (Map.Entry<String, List<String>> entry : headers.headerSet()) {
            if (!entry.getKey().equalsIgnoreCase(HttpHeaders.CONTENT_TYPE)) {
                scenario = scenario.withHeaders(entry.getKey(), entry.getValue().toArray(String[]::new));
            }
        }
        NATIVE_OWNER.scenarios().expect(HttpMethod.GET, "/native-owner", scenario);
        HttpClientResponse response = HttpClient.create().baseUrl(NATIVE_OWNER.uri().toString()).get()
            .uri("/native-owner").responseSingle((head, body) -> body.thenReturn(head))
            .block(Duration.ofSeconds(3));
        NATIVE_OWNER.verifyComplete();
        return Objects.requireNonNull(response, "native Owner response must be captured");
    }

    private static PooledDataBuffer pooled(byte[] bytes) {
        return (PooledDataBuffer)new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT).wrap(bytes);
    }

    private static PooledDataBuffer pooledEmpty() {
        return (PooledDataBuffer)new NettyDataBufferFactory(PooledByteBufAllocator.DEFAULT).allocateBuffer(1);
    }

    private static byte[] sizedProblem(int size) {
        String prefix = "{\"type\":\"about:blank\",\"title\":\"Bad Gateway\",\"detail\":\"failed\","
            + "\"code\":\"OWNER_FAILED\",\"requestId\":\"" + REQUEST_ID
            + "\",\"timestamp\":\"2026-09-06T10:11:12.123456Z\",\"padding\":\"";
        String suffix = "\"}";
        int padding = size - prefix.getBytes(StandardCharsets.UTF_8).length
            - suffix.getBytes(StandardCharsets.UTF_8).length;
        Assertions.assertTrue(padding >= 0);
        return (prefix + "x".repeat(padding) + suffix).getBytes(StandardCharsets.UTF_8);
    }

    private static final class TrackingNettyDataBufferFactory extends NettyDataBufferFactory {
        private final List<PooledDataBuffer> wrappedBuffers = new ArrayList<>();

        private TrackingNettyDataBufferFactory() {
            super(PooledByteBufAllocator.DEFAULT);
        }

        @Override
        public DataBuffer wrap(byte[] bytes) {
            DataBuffer buffer = super.wrap(bytes);
            wrappedBuffers.add((PooledDataBuffer)buffer);
            return buffer;
        }

        private int wrappedBufferCount() {
            return wrappedBuffers.size();
        }

        private boolean everyWrappedBufferReleased() {
            return wrappedBuffers.stream().noneMatch(PooledDataBuffer::isAllocated);
        }

        private void releaseAll() {
            wrappedBuffers.forEach(DataBufferUtils::release);
        }
    }
}
