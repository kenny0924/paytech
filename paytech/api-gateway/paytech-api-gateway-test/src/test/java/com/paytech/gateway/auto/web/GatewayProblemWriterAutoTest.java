package com.paytech.gateway.auto.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.codec.ServerCodecConfigurer;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ResponseStatusException;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.gateway.web.GatewayErrorCode;
import com.paytech.gateway.web.GatewayErrorWriter;
import com.paytech.gateway.web.GatewayExceptionWebHandler;
import com.paytech.gateway.web.GatewayExchangeAttributes;
import com.paytech.gateway.web.GatewayUpstreamFailureClassifier;

import reactor.netty.channel.AbortedException;
import reactor.test.StepVerifier;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Gateway WebFlux 本地 Problem Writer 的无基础设施契约测试。 */
class GatewayProblemWriterAutoTest {
    private static final Instant FIXED_INSTANT = Instant.parse("2026-09-06T12:34:56.123456789Z");
    private static final String FIXED_TIMESTAMP = "2026-09-06T12:34:56.123456Z";
    private static final String REQUEST_ID = "client-request.123_retry";
    private static final Set<String> EXACT_MEMBERS =
        Set.of("type", "title", "detail", "code", "requestId", "timestamp");
    private static final Pattern REQUEST_ID_PATTERN =
        Pattern.compile("^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$");

    private final JsonMapper json = JsonMapper.builder().build();

    @Test
    void test_writeUsesExactProblemProfileFixedClockAndApprovedHeaders() {
        GatewayErrorWriter writer = writer(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
        MockServerWebExchange exchange = exchange("GET");
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, REQUEST_ID);
        exchange.getResponse().getHeaders().add(ApiProblemProfile.REQUEST_ID_HEADER, "untrusted-duplicate");
        exchange.getResponse().getHeaders().add("X-Timestamp", "obsolete");

        writer.write(exchange, HttpStatus.BAD_REQUEST, GatewayErrorCode.GATEWAY_INVALID_REQUEST, 17L).block();

        assertProblem(exchange, HttpStatus.BAD_REQUEST, "GATEWAY_INVALID_REQUEST", "请求参数不符合网关路由契约",
            REQUEST_ID, FIXED_TIMESTAMP);
        assertEquals(List.of("17"), exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.RETRY_AFTER));
        assertTrue(exchange.getResponse().getHeaders().getOrEmpty("X-Timestamp").isEmpty());
    }

    @Test
    void test_headBranchesBeforeClockModelAndSerializationAndTransfersNoBody() {
        CountingClock clock = new CountingClock();
        GatewayErrorWriter writer = writer(clock);
        MockServerWebExchange exchange = exchange("HEAD");
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, "invalid/request-id");
        exchange.getResponse().getHeaders().put(HttpHeaders.CONTENT_TYPE,
            List.of("application/json", "application/problem+json;charset=UTF-8"));
        exchange.getResponse().getHeaders().put(ApiProblemProfile.REQUEST_ID_HEADER, List.of("one", "two"));
        exchange.getResponse().getHeaders().set("X-Timestamp", "obsolete");

        writer.write(exchange, HttpStatus.SERVICE_UNAVAILABLE, GatewayErrorCode.GATEWAY_DEPENDENCY_UNAVAILABLE, 3L)
            .block();

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exchange.getResponse().getStatusCode());
        assertEquals(List.of(ApiProblemProfile.CONTENT_TYPE),
            exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.CONTENT_TYPE));
        List<String> requestIds = exchange.getResponse().getHeaders().getOrEmpty(ApiProblemProfile.REQUEST_ID_HEADER);
        assertEquals(1, requestIds.size());
        assertTrue(REQUEST_ID_PATTERN.matcher(requestIds.getFirst()).matches());
        assertEquals(requestIds.getFirst(), exchange.getAttribute(GatewayExchangeAttributes.REQUEST_ID));
        assertEquals(List.of("3"), exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.RETRY_AFTER));
        assertTrue(exchange.getResponse().getHeaders().getOrEmpty("X-Timestamp").isEmpty());
        assertEquals("", exchange.getResponse().getBodyAsString().block());
        assertEquals(0, clock.calls.get());
    }

    @Test
    void test_replaceDropsUncommittedOwnerMaterialAndUsesNewProtocolCode() {
        GatewayErrorWriter writer = writer(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
        MockServerWebExchange exchange = exchange("GET");
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, REQUEST_ID);
        exchange.getResponse().setStatusCode(HttpStatus.I_AM_A_TEAPOT);
        exchange.getResponse().getHeaders().set("X-Owner-Secret", "owner-secret-canary");
        exchange.getResponse().getHeaders().set(HttpHeaders.CONTENT_TYPE, "text/plain");

        writer.replace(exchange, HttpStatus.BAD_GATEWAY, GatewayErrorCode.UPSTREAM_PROTOCOL_ERROR).block();

        assertProblem(exchange, HttpStatus.BAD_GATEWAY, "UPSTREAM_PROTOCOL_ERROR", "上游服务响应不符合安全契约",
            REQUEST_ID, FIXED_TIMESTAMP);
        assertTrue(exchange.getResponse().getHeaders().getOrEmpty("X-Owner-Secret").isEmpty());
        assertFalse(exchange.getResponse().getBodyAsString().block().contains("owner-secret-canary"));
    }

    @Test
    void test_unknownRuntimeUsesCommonInternalErrorWithoutLeakingCause() {
        GatewayExceptionWebHandler handler = handler(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
        MockServerWebExchange exchange = exchange("GET");
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, REQUEST_ID);

        handler.handle(exchange, new IllegalStateException("internal-secret-canary")).block();

        assertProblem(exchange, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR.getCode(),
            CommonErrorCode.INTERNAL_ERROR.getMessage(), REQUEST_ID, FIXED_TIMESTAMP);
        assertFalse(exchange.getResponse().getBodyAsString().block().contains("internal-secret-canary"));
    }

    @Test
    void test_responseStatusExceptionsUseSafeStatusSpecificOrSeriesFallbackCodes() {
        assertResponseStatus(HttpStatus.UNAUTHORIZED, "GATEWAY_UNAUTHORIZED", "未通过身份验证");
        assertResponseStatus(HttpStatus.REQUEST_TIMEOUT, "GATEWAY_REQUEST_TIMEOUT", "请求内容接收超时");
        assertResponseStatus(HttpStatus.BAD_GATEWAY, "UPSTREAM_PROTOCOL_ERROR", "上游服务响应不符合安全契约");
        assertResponseStatus(HttpStatus.I_AM_A_TEAPOT, "GATEWAY_INVALID_REQUEST", "请求参数不符合网关路由契约");
        assertResponseStatus(HttpStatus.NOT_IMPLEMENTED, "INTERNAL_ERROR", "系统处理失败");
    }

    @Test
    void test_cancellationClientAbortAndCommittedResponsesAreNotRewritten() {
        GatewayExceptionWebHandler handler = handler(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
        CancellationException cancellation = new CancellationException("cancelled");
        AbortedException clientAbort = AbortedException.beforeSend();

        assertSameFailure(handler, exchange("GET"), cancellation);
        assertSameFailure(handler, exchange("GET"), clientAbort);

        MockServerWebExchange committed = exchange("GET");
        committed.getResponse().setStatusCode(HttpStatus.ACCEPTED);
        committed.getResponse().setComplete().block();
        IllegalStateException failure = new IllegalStateException("already-committed");
        assertSameFailure(handler, committed, failure);
        assertEquals(HttpStatus.ACCEPTED, committed.getResponse().getStatusCode());
        assertEquals("", committed.getResponse().getBodyAsString().block());
    }

    @Test
    void test_negativeRetryAfterIsRejectedBeforeWriting() {
        GatewayErrorWriter writer = writer(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
        MockServerWebExchange exchange = exchange("GET");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
            () -> writer.write(exchange, HttpStatus.TOO_MANY_REQUESTS, GatewayErrorCode.GATEWAY_RATE_LIMITED, -1L)
                .block());

        assertEquals("retryAfterSeconds must not be negative", failure.getMessage());
        assertEquals(null, exchange.getResponse().getStatusCode());
        assertTrue(exchange.getResponse().getHeaders().isEmpty());
    }

    private void assertResponseStatus(HttpStatus status, String code, String detail) {
        GatewayExceptionWebHandler handler = handler(Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));
        MockServerWebExchange exchange = exchange("GET");
        exchange.getAttributes().put(GatewayExchangeAttributes.REQUEST_ID, REQUEST_ID);

        handler.handle(exchange, new ResponseStatusException(status, "response-status-secret-canary")).block();

        assertProblem(exchange, status, code, detail, REQUEST_ID, FIXED_TIMESTAMP);
        assertFalse(exchange.getResponse().getBodyAsString().block().contains("response-status-secret-canary"));
    }

    private void assertSameFailure(GatewayExceptionWebHandler handler, MockServerWebExchange exchange,
        RuntimeException failure) {
        StepVerifier.create(handler.handle(exchange, failure))
            .expectErrorSatisfies(actual -> assertSame(failure, actual)).verify();
    }

    private GatewayErrorWriter writer(Clock clock) {
        return new GatewayErrorWriter(ServerCodecConfigurer.create(), clock);
    }

    private GatewayExceptionWebHandler handler(Clock clock) {
        return new GatewayExceptionWebHandler(writer(clock), new GatewayUpstreamFailureClassifier());
    }

    private MockServerWebExchange exchange(String method) {
        return MockServerWebExchange.from(MockServerHttpRequest.method(org.springframework.http.HttpMethod.valueOf(method),
            "/problem-test"));
    }

    private void assertProblem(MockServerWebExchange exchange, HttpStatus status, String code, String detail,
        String requestId, String timestamp) {
        assertEquals(status, exchange.getResponse().getStatusCode());
        assertEquals(List.of(ApiProblemProfile.CONTENT_TYPE),
            exchange.getResponse().getHeaders().getOrEmpty(HttpHeaders.CONTENT_TYPE));
        assertEquals(List.of(requestId),
            exchange.getResponse().getHeaders().getOrEmpty(ApiProblemProfile.REQUEST_ID_HEADER));
        JsonNode body = json.readTree(exchange.getResponse().getBodyAsString().block());
        assertEquals(EXACT_MEMBERS, body.propertyNames());
        for (String member : EXACT_MEMBERS) {
            assertTrue(body.get(member).isString(), member);
            assertFalse(body.get(member).stringValue().isBlank(), member);
        }
        assertEquals(ApiProblemProfile.TYPE, body.get("type").stringValue());
        assertEquals(status.getReasonPhrase(), body.get("title").stringValue());
        assertEquals(detail, body.get("detail").stringValue());
        assertEquals(code, body.get("code").stringValue());
        assertEquals(requestId, body.get("requestId").stringValue());
        assertEquals(timestamp, body.get("timestamp").stringValue());
        assertTrue(ApiProblemProfile.isCanonicalTimestamp(body.get("timestamp").stringValue()));
        assertFalse(body.has("status"));
        assertFalse(body.has("instance"));
        assertFalse(body.has("message"));
        assertFalse(body.has("errors"));
    }

    private static final class CountingClock extends Clock {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public ZoneId getZone() {
            return ZoneOffset.UTC;
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            calls.incrementAndGet();
            throw new AssertionError("HEAD must not read the Problem clock");
        }
    }
}
