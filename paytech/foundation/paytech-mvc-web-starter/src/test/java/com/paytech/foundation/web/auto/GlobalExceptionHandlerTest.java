package com.paytech.foundation.web.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.web.method.annotation.ExceptionHandlerMethodResolver;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpproblem.api.ApiProblemResponse;
import com.paytech.foundation.web.error.ApiProblemResponseFactory;
import com.paytech.foundation.web.error.GlobalExceptionHandler;
import com.paytech.foundation.web.error.MvcProblemExceptionMapper;
import com.paytech.foundation.web.error.ProblemResponseWriter;
import com.paytech.foundation.web.error.RequestBodyFieldErrorProjector;
import com.paytech.foundation.web.request.RequestIdFilter;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class GlobalExceptionHandlerTest {

    private static final Instant RESPONSE_TIME = Instant.parse("2026-09-06T10:15:30.123456789Z");
    private static final String PRIVATE_DETAIL = "password=must-not-leak";

    private final JsonMapper objectMapper = JsonMapper.builder().build();
    private final GlobalExceptionHandler handler = handler((errorCode, defaultStatus) -> defaultStatus);

    @Test
    void test_allExceptionsResolveToTheSingleSharedWriterMethod() {
        ExceptionHandlerMethodResolver resolver = new ExceptionHandlerMethodResolver(GlobalExceptionHandler.class);

        for (Class<? extends Exception> exceptionType : Set.of(BizCheckFailException.class,
            InvalidParameterException.class, ConcurrentRequestException.class, ServiceUnavailableException.class,
            ErrorException.class, IllegalStateException.class)) {
            Method resolvedMethod = resolver.resolveMethodByExceptionType(exceptionType);
            assertNotNull(resolvedMethod);
            assertEquals("handleException", resolvedMethod.getName());
        }
    }

    @Test
    void test_businessFailureUsesSafeDetailAndExactProblemProfile() throws Exception {
        MockHttpServletResponse response = handle(new BizCheckFailException("Client does not exist"), "request-10001");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertEquals(HttpStatus.BAD_REQUEST.value(), response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertEquals(Set.of("type", "title", "detail", "code", "requestId", "timestamp"),
            Set.copyOf(body.propertyNames()));
        assertEquals("about:blank", body.get("type").stringValue());
        assertEquals("Bad Request", body.get("title").stringValue());
        assertEquals("Client does not exist", body.get("detail").stringValue());
        assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(), body.get("code").stringValue());
        assertEquals("request-10001", body.get("requestId").stringValue());
        assertEquals("2026-09-06T10:15:30.123456Z", body.get("timestamp").stringValue());
        assertEquals("request-10001", response.getHeader(RequestIdFilter.HEADER_NAME));
        assertNull(response.getHeader("X-Timestamp"));
        assertNull(response.getHeader("Date"));
    }

    @Test
    void test_systemFailuresExposeOnlyCodeDefaultDetail() throws Exception {
        ErrorException exception = new ErrorException(CommonErrorCode.INTERNAL_ERROR, PRIVATE_DETAIL,
            new IllegalStateException(PRIVATE_DETAIL));

        MockHttpServletResponse response = handle(exception, "request-system-error");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(), response.getStatus());
        assertEquals(CommonErrorCode.INTERNAL_ERROR.getMessage(), body.get("detail").stringValue());
        assertFalse(response.getContentAsString().contains(PRIVATE_DETAIL));
    }

    @Test
    void test_dependencyFailureUses503AndDoesNotExposeCause() throws Exception {
        ServiceUnavailableException exception =
            new ServiceUnavailableException(new IllegalStateException(PRIVATE_DETAIL));

        MockHttpServletResponse response = handle(exception, "request-unavailable");
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE.value(), response.getStatus());
        assertEquals("Service Unavailable", body.get("title").stringValue());
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.getCode(), body.get("code").stringValue());
        assertEquals(CommonErrorCode.SERVICE_UNAVAILABLE.getMessage(), body.get("detail").stringValue());
        assertFalse(response.getContentAsString().contains(PRIVATE_DETAIL));
    }

    @Test
    void test_factoryReadsFixedClockOnceAndTruncatesToSixFractionalDigits() {
        CountingClock clock = new CountingClock(RESPONSE_TIME);
        ApiProblemResponseFactory factory = new ApiProblemResponseFactory(clock);

        ApiProblemResponse response = factory.create(HttpStatus.CONFLICT,
            CommonErrorCode.CONCURRENT_REQUEST, "already processing", "request-fixed-time", null);

        assertEquals("about:blank", response.type());
        assertEquals("Conflict", response.title());
        assertEquals("already processing", response.detail());
        assertEquals("CONCURRENT_REQUEST", response.code());
        assertEquals("request-fixed-time", response.requestId());
        assertEquals("2026-09-06T10:15:30.123456Z", response.timestamp());
        assertNull(response.errors());
        assertEquals(1, clock.readCount());
    }

    private MockHttpServletResponse handle(Exception exception, String requestId) throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, requestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        handler.handleException(exception, request, response);
        return response;
    }

    private GlobalExceptionHandler handler(com.paytech.foundation.web.api.ApiErrorStatusResolver resolver) {
        ApiProblemResponseFactory factory =
            new ApiProblemResponseFactory(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC));
        RequestBodyFieldErrorProjector projector = new RequestBodyFieldErrorProjector(objectMapper);
        MvcProblemExceptionMapper exceptionMapper = new MvcProblemExceptionMapper(resolver, projector);
        return new GlobalExceptionHandler(exceptionMapper, new ProblemResponseWriter(factory, objectMapper));
    }

    private static final class CountingClock extends Clock {

        private final Instant instant;
        private final AtomicInteger reads = new AtomicInteger();

        private CountingClock(Instant instant) {
            this.instant = instant;
        }

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
            reads.incrementAndGet();
            return instant;
        }

        private int readCount() {
            return reads.get();
        }
    }
}
