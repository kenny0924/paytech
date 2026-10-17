package com.paytech.foundation.web.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.web.api.RetryAfterProvider;
import com.paytech.foundation.web.error.ApiProblemResponseFactory;
import com.paytech.foundation.web.error.GlobalExceptionHandler;
import com.paytech.foundation.web.error.MvcProblemExceptionMapper;
import com.paytech.foundation.web.error.ProblemResponseWriter;
import com.paytech.foundation.web.error.RequestBodyFieldErrorProjector;
import com.paytech.foundation.web.request.RequestIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class RetryAfterProblemTest {

    private static final Instant RESPONSE_TIME = Instant.parse("2026-09-07T12:34:56.123456789Z");
    private static final String PRIVATE_DETAIL = "diagnostic-token-must-not-leak";
    private static final String REQUEST_ID = "request-retry-after";

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final GlobalExceptionHandler handler = handler();

    @Test
    void test_retryableFail409SurvivesResponseResetWithOneIntegerHeader() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        response.setStatus(HttpStatus.ACCEPTED.value());
        response.setHeader(HttpHeaders.RETRY_AFTER, "999");
        response.setHeader("X-Internal-Diagnostic", PRIVATE_DETAIL);
        response.getWriter().write("partial-" + PRIVATE_DETAIL);

        handle(new RetryableConflictException(3), response);

        JsonNode body = assertProblem(response, HttpStatus.CONFLICT,
            CommonErrorCode.CONCURRENT_REQUEST.getCode(), "Checkout creation is already in progress");
        assertEquals("3", response.getHeader(HttpHeaders.RETRY_AFTER));
        assertEquals(1, response.getHeaders(HttpHeaders.RETRY_AFTER).size());
        assertNull(response.getHeader("X-Internal-Diagnostic"));
        assertFalse(body.toString().contains(PRIVATE_DETAIL));
    }

    @Test
    void test_retryableError503WritesHeaderWithoutChangingSafeProblemBody() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handle(new RetryableUnavailableException(11), response);

        assertProblem(response, HttpStatus.SERVICE_UNAVAILABLE,
            CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), CommonErrorCode.SERVICE_UNAVAILABLE.getMessage());
        assertEquals("11", response.getHeader(HttpHeaders.RETRY_AFTER));
    }

    @ParameterizedTest
    @ValueSource(longs = {1, 86400})
    void test_inclusiveRetryAfterBoundariesAreEncodedAsDecimalSeconds(long seconds) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handle(new RetryableConflictException(seconds), response);

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatus());
        assertEquals(Long.toString(seconds), response.getHeader(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void test_ordinaryExceptionsDoNotAcquireRetryAfterHeader() throws Exception {
        MockHttpServletResponse conflictResponse = new MockHttpServletResponse();
        MockHttpServletResponse unknownResponse = new MockHttpServletResponse();

        handle(new ConcurrentRequestException("already processing"), conflictResponse);
        handle(new IllegalStateException(PRIVATE_DETAIL), unknownResponse);

        assertEquals(HttpStatus.CONFLICT.value(), conflictResponse.getStatus());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), unknownResponse.getStatus());
        assertNull(conflictResponse.getHeader(HttpHeaders.RETRY_AFTER));
        assertNull(unknownResponse.getHeader(HttpHeaders.RETRY_AFTER));
    }

    @ParameterizedTest
    @ValueSource(longs = {-1, 0, 86401, Long.MAX_VALUE})
    void test_invalidRetryAfterValuesAreIgnoredWithoutChangingTheOriginalProblem(long seconds) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handle(new RetryableConflictException(seconds), response);

        assertProblem(response, HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_REQUEST.getCode(),
            "Checkout creation is already in progress");
        assertNull(response.getHeader(HttpHeaders.RETRY_AFTER));
    }

    @Test
    void test_throwingRetryAfterProviderIsIgnoredWithoutLeakingOrBecoming500() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        handle(new ThrowingRetryableConflictException(), response);

        JsonNode body = assertProblem(response, HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_REQUEST.getCode(),
            "Checkout creation is already in progress");
        assertNull(response.getHeader(HttpHeaders.RETRY_AFTER));
        assertFalse(body.toString().contains(PRIVATE_DETAIL));
    }

    private void handle(Exception exception, MockHttpServletResponse response) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/checkouts");
        request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, REQUEST_ID);
        handler.handleException(exception, request, response);
    }

    private JsonNode assertProblem(MockHttpServletResponse response, HttpStatus status, String code, String detail)
        throws Exception {
        assertEquals(status.value(), response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertEquals(REQUEST_ID, response.getHeader(RequestIdFilter.HEADER_NAME));
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals(Set.of("type", "title", "detail", "code", "requestId", "timestamp"),
            Set.copyOf(body.propertyNames()));
        assertEquals("about:blank", body.get("type").stringValue());
        assertEquals(status.getReasonPhrase(), body.get("title").stringValue());
        assertEquals(detail, body.get("detail").stringValue());
        assertEquals(code, body.get("code").stringValue());
        assertEquals(REQUEST_ID, body.get("requestId").stringValue());
        assertEquals("2026-09-07T12:34:56.123456Z", body.get("timestamp").stringValue());
        return body;
    }

    private GlobalExceptionHandler handler() {
        MvcProblemExceptionMapper mapper = new MvcProblemExceptionMapper(
            (errorCode, defaultStatus) -> defaultStatus,
            new RequestBodyFieldErrorProjector(objectMapper));
        ProblemResponseWriter writer = new ProblemResponseWriter(
            new ApiProblemResponseFactory(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC)), objectMapper);
        return new GlobalExceptionHandler(mapper, writer);
    }

    private static class RetryableConflictException extends FailException implements RetryAfterProvider {

        private final long seconds;

        private RetryableConflictException(long seconds) {
            super(CommonErrorCode.CONCURRENT_REQUEST, "Checkout creation is already in progress");
            this.seconds = seconds;
        }

        @Override
        public long retryAfterSeconds() {
            return seconds;
        }
    }

    private static final class ThrowingRetryableConflictException extends RetryableConflictException {

        private ThrowingRetryableConflictException() {
            super(1);
        }

        @Override
        public long retryAfterSeconds() {
            throw new IllegalStateException(PRIVATE_DETAIL);
        }
    }

    private static final class RetryableUnavailableException extends ServiceUnavailableException
        implements RetryAfterProvider {

        private final long seconds;

        private RetryableUnavailableException(long seconds) {
            super(new IllegalStateException(PRIVATE_DETAIL));
            this.seconds = seconds;
        }

        @Override
        public long retryAfterSeconds() {
            return seconds;
        }
    }
}
