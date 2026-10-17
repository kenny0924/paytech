package com.paytech.gateway.auto.observability;

import static com.paytech.gateway.test.GatewayProblemChecker.assertProblem;

import java.io.IOException;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneOffset;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.boot.webflux.autoconfigure.WebHttpHandlerBuilderCustomizer;
import org.springframework.core.io.buffer.DataBufferUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.HttpHandler;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.http.server.reactive.MockServerHttpResponse;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.test.context.bean.override.convention.TestBean;
import org.springframework.test.web.reactive.server.EntityExchangeResult;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.springframework.web.server.adapter.WebHttpHandlerBuilder;

import com.nimbusds.jwt.SignedJWT;
import com.paytech.gateway.guard.policy.GuardContext;
import com.paytech.gateway.guard.ratelimit.RateLimitCommandFactory;
import com.paytech.gateway.guard.ratelimit.ReactiveRateLimiter;
import com.paytech.gateway.observability.GatewayAccessLogWebFilter;
import com.paytech.gateway.observability.GatewayOutcome;
import com.paytech.gateway.routing.OperationsRouteCatalog;
import com.paytech.gateway.routing.OperationsRouteCode;
import com.paytech.gateway.security.ReactiveJwksProvider;
import com.paytech.gateway.security.VersionedHmacDigester;
import com.paytech.gateway.security.VersionedHmacDigester.VersionedDigest;
import com.paytech.gateway.test.GatewayAutoTestBase;
import com.paytech.gateway.test.GatewayAutoTestRuntime;
import com.paytech.gateway.test.GatewayPublicTestClient;
import com.paytech.gateway.test.OperationsTestPaths;
import com.paytech.gateway.test.builder.JwtFixtureBuilder;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore;
import com.paytech.gateway.test.simulator.bff.BffScenarioStore.RequestBarrier;
import com.paytech.gateway.web.GatewayExceptionWebHandler;
import com.paytech.gateway.web.GatewayExchangeAttributes;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.AppenderBase;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.netty.ByteBufFlux;
import reactor.netty.http.client.HttpClient;

/** Gateway 单一终态结构化访问事件及脱敏边界测试。 */
class GatewayAccessLogAutoTest extends GatewayAutoTestBase {
    private static final String ACCESS_LOGGER = "gateway.access";
    private static final String ORIGIN = "https://operations-console.test";
    private static final String LOGIN_PATH = OperationsTestPaths.LOGIN;
    private static final String LOGOUT_PATH = OperationsTestPaths.LOGOUT;
    private static final String CLIENT_IP = "X-Real-IP";
    private static final String DEFAULT_CLIENT_IP = "203.0.113.17";
    private static final String CACHED_KID = "shared-regression-key";
    private static final Set<String> BASE_FIELDS =
        Set.of("route", "method", "status", "status_family", "duration_ms", "outcome", "request_id", "ip_digest");

    @LocalServerPort
    private int port;

    @Autowired
    private VersionedHmacDigester digester;

    @Autowired
    private GatewayAccessLogWebFilter accessLogFilter;

    @Autowired
    private GatewayExceptionWebHandler exceptionHandler;

    @Autowired
    private RateLimitCommandFactory rateCommandFactory;

    @Autowired
    private ReactiveRateLimiter rateLimiter;

    @Autowired
    private OperationsRouteCatalog routeCatalog;

    @Autowired
    private ReactiveJwksProvider reactiveJwksProvider;

    @TestBean(name = "operationsJwtClock", methodName = "fixedClock", enforceOverride = true)
    private Clock operationsJwtClock;

    private Logger accessLogger;
    private Level originalLevel;
    private CapturingAppender appender;

    private static Set<String> union(Set<String> values, String additional) {
        java.util.HashSet<String> result = new java.util.HashSet<>(values);
        result.add(additional);
        return Set.copyOf(result);
    }

    private static String encoded(VersionedDigest digest) {
        return digest.version() + ":" + digest.digest();
    }

    private static JwtFixtureBuilder jwt() {
        return JwtFixtureBuilder.template().keyId(CACHED_KID);
    }

    private static Clock fixedClock() {
        return Clock.fixed(JwtFixtureBuilder.NOW, ZoneOffset.UTC);
    }

    private static void await(CountDownLatch latch, String message) {
        try {
            Assertions.assertTrue(latch.await(3, TimeUnit.SECONDS), message);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("等待已提交响应分块时被中断", exception);
        }
    }

    @BeforeEach
    void captureDedicatedAccessLogger() {
        GatewayAutoTestRuntime.jwksSimulator().scenarios().respondWithJwks(JwtFixtureBuilder.jwksJson(CACHED_KID));
        try {
            reactiveJwksProvider.select(SignedJWT.parse(jwt().build())).single().block(Duration.ofSeconds(1));
        } catch (java.text.ParseException exception) {
            throw new IllegalStateException("无法解析 JWKS 预热 JWT", exception);
        }
        GatewayAutoTestRuntime.jwksSimulator().reset();
        accessLogger = (Logger)LoggerFactory.getLogger(ACCESS_LOGGER);
        originalLevel = accessLogger.getLevel();
        accessLogger.setLevel(Level.INFO);
        appender = new CapturingAppender();
        appender.start();
        accessLogger.addAppender(appender);
    }

    @AfterEach
    void detachDedicatedAccessLogger() {
        if (accessLogger != null && appender != null) {
            accessLogger.detachAppender(appender);
            appender.stop();
            accessLogger.setLevel(originalLevel);
        }
    }

    @Test
    void test_authenticatedTerminalEventContainsOnlySafeFieldsAndAccessDigests() throws Exception {
        String trustedClientIp = "203.0.113.77";
        String subjectCanary = "subject-canary-sensitive";
        String sessionCanary = "sid-canary-sensitive";
        String jtiCanary = "jti-canary-sensitive";
        String passwordCanary = "password-canary-sensitive";
        String cookieCanary = "cookie-canary-sensitive";
        String csrfCanary = "csrf-canary-sensitive";
        String requestIdCanary = "request-id-canary-sensitive";
        String forwardedIpCanary = "198.51.100.77";
        JwtFixtureBuilder jwt = jwt().claim("sub", subjectCanary).claim("sid", sessionCanary).claim("jti", jtiCanary);
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/logout", BffScenarioStore.Response.json(200, "{}"));

        String body = "{\"password\":\"" + passwordCanary + "\"}";
        EntityExchangeResult<byte[]> response = gatewayClient(trustedClientIp).post().uri(LOGOUT_PATH)
            .header(HttpHeaders.ORIGIN, ORIGIN).header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.build())
            .header(HttpHeaders.COOKIE, "__Secure-operations-refresh=" + cookieCanary)
            .header("X-CSRF-Token", csrfCanary).header("X-Forwarded-For", forwardedIpCanary)
            .header("X-Request-Id", requestIdCanary)
            .header("traceparent", "00-0123456789abcdef0123456789abcdef-0123456789abcdef-01")
            .contentType(MediaType.APPLICATION_JSON).bodyValue(body).exchange().expectStatus().isOk().expectBody()
            .returnResult();

        ILoggingEvent event = onlyEvent();
        Map<String, String> fields = fields(event);
        Assertions.assertEquals("Gateway request completed", event.getFormattedMessage());
        Assertions.assertNull(event.getThrowableProxy());
        Assertions.assertEquals(union(BASE_FIELDS, "subject_digest"), fields.keySet());
        Assertions.assertEquals("operations-auth-logout", fields.get("route"));
        Assertions.assertEquals("POST", fields.get("method"));
        Assertions.assertEquals("200", fields.get("status"));
        Assertions.assertEquals("2xx", fields.get("status_family"));
        Assertions.assertEquals("UPSTREAM_RESPONSE", fields.get("outcome"));
        Assertions.assertEquals(response.getResponseHeaders().getFirst("X-Request-Id"), fields.get("request_id"));
        Assertions.assertEquals(encoded(digester.accessIpDigest(InetAddress.getByName(trustedClientIp))),
            fields.get("ip_digest"));
        Assertions.assertEquals(encoded(digester.accessSubjectDigest(subjectCanary)), fields.get("subject_digest"));
        Assertions.assertTrue(Long.parseLong(fields.get("duration_ms")) >= 0);
        Assertions.assertFalse(fields.containsKey("trace_id"), "调用方 traceparent 不得自行成为可信 Trace ID");

        assertAbsent(event, subjectCanary, sessionCanary, jtiCanary, passwordCanary, cookieCanary, csrfCanary,
            requestIdCanary, forwardedIpCanary, trustedClientIp, "127.0.0.1", jwt.build(), LOGOUT_PATH,
            simulator().uri().toString(), GatewayAutoTestRuntime.redisNamespace().value());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_transportErrorsLogFinalMappedStatusAndOutcomeExactlyOnce() {
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.immediateDisconnect());
        assertProblem(loginRequest().exchange(), 502, "UPSTREAM_PROTOCOL_ERROR");
        assertTerminalEvent(0, "502", "5xx", "UPSTREAM_INVALID_RESPONSE");

        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.delayed(200, "{}", Duration.ofSeconds(2)));
        assertProblem(loginRequest().exchange(), 504, "GATEWAY_UPSTREAM_TIMEOUT");
        assertTerminalEvent(1, "504", "5xx", "UPSTREAM_TIMEOUT");
        Assertions.assertEquals(2, appender.events().size());
    }

    @Test
    void test_oversizedControlledHeaderLogsLocal431WithoutSensitiveValueOrSideEffects() {
        String originCanary = "oversized-origin-canary-" + "x".repeat(2_048);

        assertProblem(gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, originCanary)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}").exchange(), 431,
            "GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE");

        ILoggingEvent event = onlyEvent();
        Map<String, String> fields = fields(event);
        Assertions.assertAll(() -> Assertions.assertEquals("operations-auth-login", fields.get("route")),
            () -> Assertions.assertEquals("431", fields.get("status")),
            () -> Assertions.assertEquals("4xx", fields.get("status_family")),
            () -> Assertions.assertEquals("REQUEST_HEADER_FIELDS_TOO_LARGE", fields.get("outcome")),
            () -> Assertions.assertEquals(0, simulator().journal().count()),
            () -> Assertions.assertEquals(0, GatewayAutoTestRuntime.jwksSimulator().journal().count()),
            () -> Assertions.assertTrue(redisTestCleaner.guardKeys().isEmpty()));
        assertAbsent(event, originCanary);
    }

    @Test
    void test_validJwtAuthorizationDenialStillUsesTrustedSubjectDigest() throws Exception {
        String subjectCanary = "denied-subject-canary-sensitive";
        JwtFixtureBuilder jwt = jwt().claim("sub", subjectCanary).claim("channel", "CLIENT");

        assertProblem(gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt.build())
            .header(HttpHeaders.COOKIE, "__Secure-operations-refresh=opaque-refresh").bodyValue("{}").exchange(), 403,
            "GATEWAY_FORBIDDEN");

        ILoggingEvent event = onlyEvent();
        Map<String, String> fields = fields(event);
        Assertions.assertEquals("AUTHORIZATION_DENIED", fields.get("outcome"));
        Assertions.assertEquals(encoded(digester.accessSubjectDigest(subjectCanary)), fields.get("subject_digest"));
        assertAbsent(event, subjectCanary);
        Assertions.assertEquals(0, simulator().journal().count());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_logoutRateRejectionStillUsesTrustedSubjectDigestBeforeGlobalFilters() throws Exception {
        String subjectCanary = "limited-subject-canary-sensitive";
        JwtFixtureBuilder jwt = jwt().claim("sub", subjectCanary);
        GuardContext context =
            new GuardContext(OperationsRouteCode.AUTH_LOGOUT, InetAddress.getByName(DEFAULT_CLIENT_IP), subjectCanary);
        var logout = routeCatalog.all().stream().filter(route -> route.code() == OperationsRouteCode.AUTH_LOGOUT)
            .findFirst().orElseThrow();
        var subjectRate = rateCommandFactory.createAuthenticatedSubject(logout.authenticatedGuard(), context);
        for (int index = 0; index < 10; index++) {
            Assertions.assertTrue(rateLimiter.evaluate(subjectRate).block(Duration.ofSeconds(1)).allowed());
        }

        assertProblem(logoutRequest(jwt.build()).exchange(), 429, "GATEWAY_RATE_LIMITED");

        ILoggingEvent rejectedEvent = onlyEvent();
        Assertions.assertEquals("RATE_LIMITED", fields(rejectedEvent).get("outcome"));
        Assertions.assertEquals(encoded(digester.accessSubjectDigest(subjectCanary)),
            fields(rejectedEvent).get("subject_digest"));
        assertAbsent(rejectedEvent, subjectCanary);
        Assertions.assertEquals(0, simulator().journal().count());
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_unknownRawPathIsDeniedWithoutSensitiveValue() {
        String rawPathCanary = "unknown-raw-path-canary";

        assertProblem(gatewayClient().get().uri("/" + rawPathCanary + "?secret=query-canary").exchange(), 404,
            "GATEWAY_ROUTE_NOT_FOUND");

        ILoggingEvent event = onlyEvent();
        Map<String, String> fields = fields(event);
        Assertions.assertEquals(BASE_FIELDS, fields.keySet());
        Assertions.assertEquals("UNMATCHED", fields.get("route"));
        Assertions.assertEquals("404", fields.get("status"));
        Assertions.assertEquals("ROUTE_NOT_FOUND", fields.get("outcome"));
        assertAbsent(event, rawPathCanary, "query-canary");
        GatewayAutoTestRuntime.jwksSimulator().journal().verifyNoRequests();
    }

    @Test
    void test_clientCancellationEmitsOneBoundedFallbackEvent() {
        RequestBarrier barrier = new RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.json(200, "{}").waitingFor(barrier));
        Disposable request = GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set(CLIENT_IP, DEFAULT_CLIENT_IP);
            headers.set(HttpHeaders.ORIGIN, ORIGIN);
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        }).post().uri(LOGIN_PATH).send(ByteBufFlux.fromString(Mono.just("{}"))).response().subscribe(ignored -> {
        }, ignored -> {
        });

        barrier.awaitRequest(Duration.ofSeconds(3));
        try {
            request.dispose();
            Awaitility.await().atMost(Duration.ofSeconds(2))
                .untilAsserted(() -> Assertions.assertEquals(1, appender.events().size()));
            Map<String, String> fields = fields(onlyEvent());
            Assertions.assertEquals("operations-auth-login", fields.get("route"));
            Assertions.assertEquals("NONE", fields.get("status"));
            Assertions.assertEquals("NONE", fields.get("status_family"));
            Assertions.assertEquals("CLIENT_CANCELLED", fields.get("outcome"));
        } finally {
            request.dispose();
            barrier.release();
        }
        Assertions.assertEquals(1, appender.events().size());
    }

    @Test
    void test_clientCancellationAfterHeadersAndFirstBodyChunkOverridesProvisionalSuccess() {
        RequestBarrier remainingBodyBarrier = new RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.chunkedUntilReleased(200, "first-chunk", "-remaining", remainingBodyBarrier));
        CountDownLatch firstChunkReceived = new CountDownLatch(1);
        AtomicInteger responseStatus = new AtomicInteger();
        AtomicReference<String> firstChunk = new AtomicReference<>();
        Disposable request = GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set(CLIENT_IP, DEFAULT_CLIENT_IP);
            headers.set(HttpHeaders.ORIGIN, ORIGIN);
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        }).post().uri(LOGIN_PATH).send(ByteBufFlux.fromString(Mono.just("{}")))
            .response((response, content) -> content.asString().doOnNext(chunk -> {
                responseStatus.set(response.status().code());
                firstChunk.compareAndSet(null, chunk);
                firstChunkReceived.countDown();
            }).then()).subscribe(ignored -> {
            }, ignored -> {
            });

        try {
            remainingBodyBarrier.awaitRequest(Duration.ofSeconds(3));
            await(firstChunkReceived, "客户端未收到已提交响应的首个分块");
            Assertions.assertEquals(200, responseStatus.get());
            Assertions.assertEquals("first-chunk", firstChunk.get());
            Assertions.assertEquals(0, appender.events().size(), "响应 Header 和首个 Body 分块不是请求终态");
            request.dispose();

            Map<String, String> fields = fields(onlyEvent());
            Assertions.assertEquals("operations-auth-login", fields.get("route"));
            Assertions.assertEquals("NONE", fields.get("status"));
            Assertions.assertEquals("NONE", fields.get("status_family"));
            Assertions.assertEquals("CLIENT_CANCELLED", fields.get("outcome"));
        } finally {
            request.dispose();
            remainingBodyBarrier.release();
        }
        Assertions.assertEquals(1, appender.events().size());
        Assertions.assertEquals(1, simulator().journal().count());
    }

    @Test
    void test_streamTimeoutAfterCommittedChunkEmitsExactTerminalOutcome() {
        RequestBarrier remainingBodyBarrier = new RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.chunkedUntilReleased(200, "first-chunk", "-remaining", remainingBodyBarrier));
        CountDownLatch firstChunkReceived = new CountDownLatch(1);
        CountDownLatch responseTerminated = new CountDownLatch(1);
        AtomicReference<String> firstChunk = new AtomicReference<>();
        Disposable request = GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set(CLIENT_IP, DEFAULT_CLIENT_IP);
            headers.set(HttpHeaders.ORIGIN, ORIGIN);
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        }).post().uri(LOGIN_PATH).send(ByteBufFlux.fromString(Mono.just("{}")))
            .response((response, content) -> content.asString().doOnNext(chunk -> {
                firstChunk.compareAndSet(null, chunk);
                firstChunkReceived.countDown();
            }).then()).doFinally(ignored -> responseTerminated.countDown()).subscribe(ignored -> {
            }, ignored -> {
            });

        try {
            remainingBodyBarrier.awaitRequest(Duration.ofSeconds(3));
            await(firstChunkReceived, "客户端未收到已提交响应的首个分块");
            Assertions.assertEquals("first-chunk", firstChunk.get());
            Assertions.assertEquals(0, appender.events().size(), "首个响应分块不是请求终态");
            await(responseTerminated, "完整下游响应链未按路由预算超时");

            Map<String, String> fields = fields(onlyEvent());
            Assertions.assertEquals("operations-auth-login", fields.get("route"));
            Assertions.assertEquals("NONE", fields.get("status"));
            Assertions.assertEquals("NONE", fields.get("status_family"));
            Assertions.assertEquals("UPSTREAM_TIMEOUT", fields.get("outcome"));
        } finally {
            request.dispose();
            remainingBodyBarrier.release();
        }
        Assertions.assertEquals(1, appender.events().size());
        Assertions.assertEquals(1, simulator().journal().count());
    }

    @Test
    void test_upstreamDisconnectAfterCommittedBodyIsNotMisclassifiedAsClientCancellation() {
        RequestBarrier disconnectBarrier = new RequestBarrier();
        simulator().scenarios().expect(HttpMethod.POST, "/auth/v1/login",
            BffScenarioStore.Response.chunkedThenDisconnect(200, "first-chunk", disconnectBarrier));
        CountDownLatch firstChunkReceived = new CountDownLatch(1);
        CountDownLatch responseTerminated = new CountDownLatch(1);
        AtomicInteger responseStatus = new AtomicInteger();
        AtomicReference<String> firstChunk = new AtomicReference<>();
        Disposable request = GatewayPublicTestClient.operationsHttpClient(port).headers(headers -> {
            headers.set(CLIENT_IP, DEFAULT_CLIENT_IP);
            headers.set(HttpHeaders.ORIGIN, ORIGIN);
            headers.set(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE);
        }).post().uri(LOGIN_PATH).send(ByteBufFlux.fromString(Mono.just("{}")))
            .response((response, content) -> content.asString().doOnNext(chunk -> {
                responseStatus.set(response.status().code());
                firstChunk.compareAndSet(null, chunk);
                firstChunkReceived.countDown();
            }).then()).doFinally(ignored -> responseTerminated.countDown()).subscribe(ignored -> {
            }, ignored -> {
            });

        try {
            disconnectBarrier.awaitRequest(Duration.ofSeconds(3));
            await(firstChunkReceived, "客户端未收到 BFF 断连前的首个响应分块");
            Assertions.assertEquals(200, responseStatus.get());
            Assertions.assertEquals("first-chunk", firstChunk.get());
            Assertions.assertEquals(0, appender.events().size(), "BFF 首个响应分块不是请求终态");
            disconnectBarrier.release();
            await(responseTerminated, "BFF 分块断连未终止 Gateway 响应");

            Map<String, String> fields = fields(onlyEvent());
            Assertions.assertEquals("operations-auth-login", fields.get("route"));
            Assertions.assertEquals("NONE", fields.get("status"));
            Assertions.assertEquals("NONE", fields.get("status_family"));
            Assertions.assertEquals("UPSTREAM_INVALID_RESPONSE", fields.get("outcome"));
        } finally {
            request.dispose();
            disconnectBarrier.release();
        }
        Assertions.assertEquals(1, appender.events().size());
        Assertions.assertEquals(1, simulator().journal().count());
    }

    @Test
    void test_mappedErrorCancellationAfterCommittedBodyUsesActualOuterTermination() {
        ResponseWriteBarrier responseWriteBarrier = new ResponseWriteBarrier();
        CountDownLatch errorBodyWritten = new CountDownLatch(1);
        MockServerHttpResponse response =
            mappedErrorResponse(errorBodyWritten, body -> body.then(responseWriteBarrier.awaitRelease()));
        Disposable request = mappedErrorHttpHandler().handle(mappedErrorRequest(), response).subscribe(ignored -> {
        }, ignored -> {
        });

        try {
            await(errorBodyWritten, "映射错误响应体未进入 Server 写入链");
            responseWriteBarrier.awaitSubscription();
            Assertions.assertEquals(HttpStatus.SERVICE_UNAVAILABLE, response.getStatusCode());
            Assertions.assertEquals(0, appender.events().size(), "映射错误响应体尚未完成时不得写终态事件");
            request.dispose();

            Map<String, String> fields = fields(onlyEvent());
            Assertions.assertEquals("UNMATCHED", fields.get("route"));
            Assertions.assertEquals("NONE", fields.get("status"));
            Assertions.assertEquals("NONE", fields.get("status_family"));
            Assertions.assertEquals("CLIENT_CANCELLED", fields.get("outcome"));
        } finally {
            request.dispose();
            responseWriteBarrier.release();
        }
        Assertions.assertEquals(1, appender.events().size());
    }

    @Test
    void test_mappedErrorBodyWriteFailureDoesNotClaimCommittedStatusAsCompleted() {
        CountDownLatch errorBodyWritten = new CountDownLatch(1);
        CountDownLatch writeFailed = new CountDownLatch(1);
        MockServerHttpResponse response = mappedErrorResponse(errorBodyWritten,
            body -> body.then(Mono.error(new IllegalStateException("映射错误响应写入失败"))));

        mappedErrorHttpHandler().handle(mappedErrorRequest(), response).subscribe(ignored -> {
        }, ignored -> writeFailed.countDown());

        await(errorBodyWritten, "映射错误响应体未进入失败写入链");
        await(writeFailed, "映射错误响应写入失败未到达最外层终止观察点");
        Map<String, String> fields = fields(onlyEvent());
        Assertions.assertEquals("UNMATCHED", fields.get("route"));
        Assertions.assertEquals("NONE", fields.get("status"));
        Assertions.assertEquals("NONE", fields.get("status_family"));
        Assertions.assertEquals("DEPENDENCY_UNAVAILABLE", fields.get("outcome"));
        Assertions.assertEquals(1, appender.events().size());
    }

    @Test
    void test_adapterSwallowedConnectionResetPreservesClassifiedOutcome() {
        CountDownLatch errorBodyWritten = new CountDownLatch(1);
        CountDownLatch adapterCompleted = new CountDownLatch(1);
        AtomicReference<Throwable> unexpectedError = new AtomicReference<>();
        MockServerHttpResponse response = mappedErrorResponse(errorBodyWritten,
            body -> body.then(Mono.error(new IOException("Connection reset by peer"))));

        mappedErrorHttpHandler().handle(mappedErrorRequest(), response).subscribe(ignored -> {
        }, unexpectedError::set, adapterCompleted::countDown);

        await(errorBodyWritten, "映射错误响应体未进入断连写入链");
        await(adapterCompleted, "Spring Adapter 未吞掉已识别的客户端断连");
        Assertions.assertNull(unexpectedError.get());
        Map<String, String> fields = fields(onlyEvent());
        Assertions.assertEquals("UNMATCHED", fields.get("route"));
        Assertions.assertEquals("NONE", fields.get("status"));
        Assertions.assertEquals("NONE", fields.get("status_family"));
        Assertions.assertEquals("DEPENDENCY_UNAVAILABLE", fields.get("outcome"));
        Assertions.assertEquals(1, appender.events().size());
    }

    @Test
    void test_outerConnectionResetErrorPreservesClassifiedOutcome() {
        CountDownLatch disconnectObserved = new CountDownLatch(1);
        MockServerHttpResponse response = new MockServerHttpResponse();

        outerDisconnectHttpHandler().handle(mappedErrorRequest(), response).subscribe(ignored -> {
        }, ignored -> disconnectObserved.countDown());

        await(disconnectObserved, "最外层客户端断连错误未终止请求");
        Map<String, String> fields = fields(onlyEvent());
        Assertions.assertEquals("UNMATCHED", fields.get("route"));
        Assertions.assertEquals("NONE", fields.get("status"));
        Assertions.assertEquals("NONE", fields.get("status_family"));
        Assertions.assertEquals("UPSTREAM_RESPONSE", fields.get("outcome"));
        Assertions.assertEquals(1, appender.events().size());
    }

    @Test
    void test_validOptionsPreflightUsesTrustedCatalogRouteCode() {
        gatewayClient().options().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "POST").exchange().expectStatus().isNoContent()
            .expectBody().isEmpty();

        Map<String, String> fields = fields(onlyEvent());
        Assertions.assertEquals("operations-auth-login", fields.get("route"));
        Assertions.assertEquals("204", fields.get("status"));
        Assertions.assertEquals("UPSTREAM_RESPONSE", fields.get("outcome"));
        Assertions.assertEquals(0, simulator().journal().count());
    }

    @Test
    void test_validCurrentMdcTraceIdIsCorrelatedWithoutParsingRequestHeaders() {
        String traceId = "0123456789abcdef0123456789abcdef";
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.post(LOGIN_PATH)
            .remoteAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345)));
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "01730e37-0923-44b8-b2af-4c2f4304917b");
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.UPSTREAM_RESPONSE);

        MDC.put("trace_id", traceId);
        try {
            accessLogFilter.filter(exchange, current -> {
                current.getResponse().setStatusCode(HttpStatus.OK);
                return current.getResponse().setComplete();
            }).block(Duration.ofSeconds(1));
        } finally {
            MDC.remove("trace_id");
        }

        Assertions.assertEquals(traceId, fields(onlyEvent()).get("trace_id"));
    }

    @Test
    void test_directFallbackKeepsBoundedInternalRouteAndSanitizesMissingContext() {
        MockServerWebExchange exchange =
            MockServerWebExchange.from(MockServerHttpRequest.get("/unmatched-direct-fallback"));
        exchange.getAttributes().put(GatewayExchangeAttributes.ROUTE_CODE, "custom-catalog-route");
        exchange.getAttributes().put(GatewayExchangeAttributes.OUTCOME, "attacker-outcome");
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, " ");
        exchange.getAttributes().put(GatewayExchangeAttributes.ACCESS_SUBJECT_DIGEST, "attacker-subject");

        MDC.put("trace_id", "00000000000000000000000000000000");
        try {
            accessLogFilter.filter(exchange, ignored -> Mono.empty()).block(Duration.ofSeconds(1));
        } finally {
            MDC.remove("trace_id");
        }

        Map<String, String> fields = fields(onlyEvent());
        Assertions.assertAll(() -> Assertions.assertEquals(BASE_FIELDS, fields.keySet()),
            () -> Assertions.assertEquals("custom-catalog-route", fields.get("route")),
            () -> Assertions.assertEquals("200", fields.get("status")),
            () -> Assertions.assertEquals("2xx", fields.get("status_family")),
            () -> Assertions.assertEquals("UPSTREAM_RESPONSE", fields.get("outcome")),
            () -> Assertions.assertEquals("unavailable", fields.get("request_id")),
            () -> Assertions.assertEquals("unavailable", fields.get("ip_digest")),
            () -> Assertions.assertFalse(fields.containsKey("subject_digest")),
            () -> Assertions.assertFalse(fields.containsKey("trace_id")));
    }

    private void assertTerminalEvent(int index, String status, String statusFamily, String outcome) {
        Awaitility.await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> Assertions.assertTrue(appender.events().size() > index));
        Map<String, String> fields = fields(appender.events().get(index));
        Assertions.assertEquals("operations-auth-login", fields.get("route"));
        Assertions.assertEquals(status, fields.get("status"));
        Assertions.assertEquals(statusFamily, fields.get("status_family"));
        Assertions.assertEquals(outcome, fields.get("outcome"));
    }

    private ILoggingEvent onlyEvent() {
        Awaitility.await().atMost(Duration.ofSeconds(1))
            .untilAsserted(() -> Assertions.assertEquals(1, appender.events().size()));
        return appender.events().getFirst();
    }

    private Map<String, String> fields(ILoggingEvent event) {
        Map<String, String> values = new LinkedHashMap<>();
        event.getKeyValuePairs().forEach(
            pair -> Assertions.assertNull(values.put(pair.key, String.valueOf(pair.value)), "访问日志字段不得重复: " + pair.key));
        return values;
    }

    private void assertAbsent(ILoggingEvent event, String... canaries) {
        String material = event.getFormattedMessage() + fields(event);
        for (String canary : canaries) {
            Assertions.assertFalse(material.contains(canary), () -> "访问日志泄露 Canary: " + canary);
        }
    }

    private WebTestClient.RequestHeadersSpec<?> loginRequest() {
        return gatewayClient().post().uri(LOGIN_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
    }

    private WebTestClient.RequestHeadersSpec<?> logoutRequest(String accessToken) {
        return gatewayClient().post().uri(LOGOUT_PATH).header(HttpHeaders.ORIGIN, ORIGIN)
            .header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken)
            .header(HttpHeaders.COOKIE, "__Secure-operations-refresh=opaque-refresh")
            .contentType(MediaType.APPLICATION_JSON).bodyValue("{}");
    }

    private WebTestClient gatewayClient() {
        return gatewayClient(DEFAULT_CLIENT_IP);
    }

    private WebTestClient gatewayClient(String clientIp) {
        return GatewayPublicTestClient.operations(port).defaultHeader(CLIENT_IP, clientIp)
            .responseTimeout(Duration.ofSeconds(5)).build();
    }

    private com.paytech.gateway.test.simulator.bff.OperationsBffSimulator simulator() {
        return GatewayAutoTestRuntime.operationsBffSimulator();
    }

    private HttpHandler mappedErrorHttpHandler() {
        WebHttpHandlerBuilder builder = WebHttpHandlerBuilder
            .webHandler(
                exchange -> accessLogFilter.filter(exchange, ignored -> Mono.error(new ConnectException("BFF 连接失败"))))
            .exceptionHandler(exceptionHandler);
        customizeTerminalObserver(builder);
        return builder.build();
    }

    private HttpHandler outerDisconnectHttpHandler() {
        WebHttpHandlerBuilder builder =
            WebHttpHandlerBuilder.webHandler(exchange -> accessLogFilter.filter(exchange, current -> {
                current.getAttributes().put(GatewayExchangeAttributes.OUTCOME, GatewayOutcome.UPSTREAM_RESPONSE);
                current.getResponse().setStatusCode(HttpStatus.OK);
                return current.getResponse().setComplete();
            }));
        customizeTerminalObserver(builder);
        builder.httpHandlerDecorator(delegate -> (request, response) -> delegate.handle(request, response)
            .then(Mono.error(new IOException("Connection reset by peer"))));
        return builder.build();
    }

    private void customizeTerminalObserver(WebHttpHandlerBuilder builder) {
        Object terminalObserverCandidate = accessLogFilter;
        if (terminalObserverCandidate instanceof WebHttpHandlerBuilderCustomizer customizer) {
            customizer.customize(builder);
        }
    }

    private MockServerHttpRequest mappedErrorRequest() {
        return MockServerHttpRequest.post(LOGIN_PATH)
            .remoteAddress(new InetSocketAddress(InetAddress.getLoopbackAddress(), 12345)).build();
    }

    private MockServerHttpResponse mappedErrorResponse(CountDownLatch bodyWritten,
        java.util.function.Function<Mono<Void>, Mono<Void>> terminalWrite) {
        MockServerHttpResponse response = new MockServerHttpResponse();
        response.setWriteHandler(body -> terminalWrite.apply(body.doOnNext(buffer -> {
            DataBufferUtils.release(buffer);
            bodyWritten.countDown();
        }).then()));
        return response;
    }

    private static final class CapturingAppender extends AppenderBase<ILoggingEvent> {
        private final List<ILoggingEvent> events = new CopyOnWriteArrayList<>();

        @Override
        protected void append(ILoggingEvent event) {
            event.prepareForDeferredProcessing();
            events.add(event);
        }

        private List<ILoggingEvent> events() {
            return List.copyOf(events);
        }
    }

    private static final class ResponseWriteBarrier {
        private final CountDownLatch subscribed = new CountDownLatch(1);
        private final Sinks.One<Void> release = Sinks.one();

        private Mono<Void> awaitRelease() {
            return Mono.defer(() -> {
                subscribed.countDown();
                return release.asMono();
            });
        }

        private void awaitSubscription() {
            await(subscribed, "映射错误响应的剩余写入屏障未订阅");
        }

        private void release() {
            release.tryEmitEmpty();
        }
    }
}
