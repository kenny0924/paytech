package com.paytech.foundation.web.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.ErrorResponse;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.foundation.web.autoconfigure.WebAutoConfiguration;
import com.paytech.foundation.web.error.ApiProblemResponseFactory;
import com.paytech.foundation.web.error.GlobalExceptionHandler;
import com.paytech.foundation.web.error.MappedProblem;
import com.paytech.foundation.web.error.MvcProblemExceptionMapper;
import com.paytech.foundation.web.error.ProblemExceptionFilter;
import com.paytech.foundation.web.error.ProblemResponseWriter;
import com.paytech.foundation.web.error.RequestBodyFieldErrorProjector;
import com.paytech.foundation.web.request.RequestIdFilter;

import tools.jackson.databind.json.JsonMapper;

class ApiErrorStatusResolverTest {

    private static final String PRIVATE_DETAIL = "diagnostic-token-must-not-be-exposed";
    private static final Instant BUSINESS_TIME = Instant.parse("2001-02-03T04:05:06.123456Z");

    @Test
    void test_defaultAutoConfigurationProvidesOneSharedMapperWriterAndFallback() {
        try (var context = context()) {
            context.register(TestInfrastructureConfiguration.class);
            context.refresh();

            assertTrue(context.getBeansOfType(ApiErrorStatusResolver.class).isEmpty());
            assertEquals(1, context.getBeansOfType(MvcProblemExceptionMapper.class).size());
            assertEquals(1, context.getBeansOfType(ProblemResponseWriter.class).size());
            assertEquals(1, context.getBeansOfType(ProblemExceptionFilter.class).size());
            assertEquals(1, context.getBeansOfType(GlobalExceptionHandler.class).size());
        }
    }

    @Test
    void test_autoConfiguredProblemFactoryDoesNotConsumeAnUnrelatedBusinessClock() {
        try (var context = context()) {
            context.register(TestInfrastructureConfiguration.class);
            context.refresh();

            var response = context.getBean(ApiProblemResponseFactory.class)
                .create(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR,
                    CommonErrorCode.INTERNAL_ERROR.getMessage(), "request-independent-clock", null);

            assertNotEquals(ApiProblemProfile.formatTimestamp(BUSINESS_TIME), response.timestamp());
            assertTrue(ApiProblemProfile.isCanonicalTimestamp(response.timestamp()));
        }
    }

    @Test
    void test_autoConfigurationUsesApplicationResolverOnlyForOwnerErrors() {
        try (var context = context()) {
            context.register(TestInfrastructureConfiguration.class, StatusConfiguration.class);
            context.refresh();
            MvcProblemExceptionMapper mapper = context.getBean(MvcProblemExceptionMapper.class);

            assertProblem(HttpStatus.CONFLICT, CommonErrorCode.BUSINESS_CHECK_FAILED,
                mapper.map(failure(), request()));
            assertProblem(HttpStatus.BAD_GATEWAY, CommonErrorCode.INTERNAL_ERROR,
                mapper.map(error(), request()));
            assertProblem(HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE,
                mapper.map(unavailable(), request()));
            assertProblem(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR,
                mapper.map(new IllegalStateException(PRIVATE_DETAIL), request()));
        }
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class, names = {"UNAUTHORIZED", "FORBIDDEN", "CONFLICT", "UNSUPPORTED_MEDIA_TYPE"})
    void test_failExceptionCanResolveOnlyToStandard4xx(HttpStatus status) {
        MvcProblemExceptionMapper mapper = mapper((code, defaultStatus) -> {
            assertSame(CommonErrorCode.BUSINESS_CHECK_FAILED, code);
            assertEquals(HttpStatus.BAD_REQUEST, defaultStatus);
            return status;
        });

        MappedProblem problem = mapper.map(failure(), request());

        assertProblem(status, CommonErrorCode.BUSINESS_CHECK_FAILED, problem);
        assertEquals(CommonErrorCode.BUSINESS_CHECK_FAILED.getMessage(), problem.detail());
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class,
        names = {"CONTINUE", "OK", "NO_CONTENT", "TEMPORARY_REDIRECT", "INTERNAL_SERVER_ERROR", "BAD_GATEWAY"})
    void test_failExceptionRejectsEveryNon4xxResolution(HttpStatus status) {
        assertProblem(HttpStatus.BAD_REQUEST, CommonErrorCode.BUSINESS_CHECK_FAILED,
            mapper((code, defaultStatus) -> status).map(failure(), request()));
    }

    @ParameterizedTest
    @EnumSource(value = HttpStatus.class,
        names = {"CONTINUE", "OK", "NO_CONTENT", "TEMPORARY_REDIRECT", "BAD_REQUEST", "UNAUTHORIZED", "CONFLICT"})
    void test_errorExceptionRejectsEveryNon5xxResolution(HttpStatus status) {
        assertProblem(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR,
            mapper((code, defaultStatus) -> status).map(error(), request()));
    }

    @Test
    void test_nullOrThrowingResolverFailsClosedToDefaultStatus() {
        assertProblem(HttpStatus.BAD_REQUEST, CommonErrorCode.BUSINESS_CHECK_FAILED,
            mapper((code, defaultStatus) -> null).map(failure(), request()));
        assertProblem(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR,
            mapper((code, defaultStatus) -> {
                throw new IllegalStateException(PRIVATE_DETAIL);
            }).map(error(), request()));
    }

    @Test
    void test_sharedConcurrentUpstreamAndUnavailableCategoriesCannotBeRemapped() {
        MvcProblemExceptionMapper mapper = mapper((code, defaultStatus) -> HttpStatus.valueOf(418));

        assertProblem(HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_REQUEST,
            mapper.map(new ConcurrentRequestException("already running"), request()));
        assertProblem(HttpStatus.BAD_GATEWAY, CommonErrorCode.UPSTREAM_INVALID_RESPONSE,
            mapper.map(new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, PRIVATE_DETAIL), request()));
        assertProblem(HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE,
            mapper.map(unavailable(), request()));
    }

    @Test
    void test_otherStandardSpringErrorResponsesKeepStatusButNeverGeneratedDetail() {
        MappedProblem clientProblem = mapper(defaultResolver())
            .map(new FrameworkErrorException(HttpStatus.valueOf(418)), request());
        MappedProblem serverProblem = mapper(defaultResolver())
            .map(new FrameworkErrorException(HttpStatus.NOT_IMPLEMENTED), request());

        assertProblem(HttpStatus.valueOf(418), CommonErrorCode.INVALID_REQUEST, clientProblem);
        assertEquals(CommonErrorCode.INVALID_REQUEST.getMessage(), clientProblem.detail());
        assertProblem(HttpStatus.NOT_IMPLEMENTED, CommonErrorCode.INTERNAL_ERROR, serverProblem);
        assertEquals(CommonErrorCode.INTERNAL_ERROR.getMessage(), serverProblem.detail());
        assertFalse(clientProblem.toString().contains(PRIVATE_DETAIL));
        assertFalse(serverProblem.toString().contains(PRIVATE_DETAIL));
    }

    @Test
    void test_nonStandardSpringErrorResponseFailsClosedTo500() {
        MappedProblem problem = mapper(defaultResolver())
            .map(new FrameworkErrorException(HttpStatusCode.valueOf(499)), request());

        assertProblem(HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR, problem);
    }

    private AnnotationConfigWebApplicationContext context() {
        var context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.register(WebAutoConfiguration.class);
        return context;
    }

    private MvcProblemExceptionMapper mapper(ApiErrorStatusResolver resolver) {
        return new MvcProblemExceptionMapper(resolver,
            new RequestBodyFieldErrorProjector(JsonMapper.builder().build()));
    }

    private ApiErrorStatusResolver defaultResolver() {
        return (errorCode, defaultStatus) -> defaultStatus;
    }

    private FailException failure() {
        return new FailException(CommonErrorCode.BUSINESS_CHECK_FAILED, null,
            new IllegalArgumentException(PRIVATE_DETAIL));
    }

    private ErrorException error() {
        return new ErrorException(CommonErrorCode.INTERNAL_ERROR, PRIVATE_DETAIL,
            new IllegalStateException(PRIVATE_DETAIL));
    }

    private ServiceUnavailableException unavailable() {
        return new ServiceUnavailableException(new IllegalStateException(PRIVATE_DETAIL));
    }

    private MockHttpServletRequest request() {
        var request = new MockHttpServletRequest();
        request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, "request-status-resolution");
        return request;
    }

    private void assertProblem(HttpStatus expectedStatus, CommonErrorCode expectedCode, MappedProblem problem) {
        assertEquals(expectedStatus, problem.status());
        assertEquals(expectedCode, problem.errorCode());
        assertNotNull(problem.detail());
        assertTrue(problem.headers().isEmpty());
        assertTrue(problem.errors().isEmpty());
    }

    private static final class FrameworkErrorException extends RuntimeException implements ErrorResponse {

        private final HttpStatusCode status;

        private FrameworkErrorException(HttpStatusCode status) {
            super(PRIVATE_DETAIL);
            this.status = status;
        }

        @Override
        public HttpStatusCode getStatusCode() {
            return status;
        }

        @Override
        public HttpHeaders getHeaders() {
            return HttpHeaders.EMPTY;
        }

        @Override
        public ProblemDetail getBody() {
            return ProblemDetail.forStatusAndDetail(status, PRIVATE_DETAIL);
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class TestInfrastructureConfiguration {

        @Bean
        Clock unrelatedBusinessClock() {
            return Clock.fixed(BUSINESS_TIME, ZoneOffset.UTC);
        }

        @Bean
        JsonMapper testObjectMapper() {
            return JsonMapper.builder().build();
        }
    }

    @Configuration(proxyBeanMethods = false)
    static class StatusConfiguration {

        @Bean
        ApiErrorStatusResolver testErrorStatusResolver() {
            return (code, defaultStatus) -> switch (code) {
                case CommonErrorCode.BUSINESS_CHECK_FAILED -> HttpStatus.CONFLICT;
                case CommonErrorCode.INTERNAL_ERROR -> HttpStatus.BAD_GATEWAY;
                default -> defaultStatus;
            };
        }
    }
}
