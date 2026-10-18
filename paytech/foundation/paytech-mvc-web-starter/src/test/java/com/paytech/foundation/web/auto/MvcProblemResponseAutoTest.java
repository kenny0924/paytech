package com.paytech.foundation.web.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.io.EOFException;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.web.server.context.WebServerApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.core.MethodParameter;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.OrderUtils;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.JacksonJsonHttpMessageConverter;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.FieldError;
import org.springframework.validation.ObjectError;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;
import org.springframework.validation.method.MethodValidationResult;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.MissingPathVariableException;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.resource.NoResourceFoundException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpproblem.api.ApiProblemProfile;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.foundation.web.api.ApiErrorStatusResolver;
import com.paytech.foundation.web.error.ApiProblemResponseFactory;
import com.paytech.foundation.web.error.GlobalExceptionHandler;
import com.paytech.foundation.web.error.MappedProblem;
import com.paytech.foundation.web.error.MvcProblemExceptionMapper;
import com.paytech.foundation.web.error.ProblemExceptionFilter;
import com.paytech.foundation.web.error.ProblemResponseWriter;
import com.paytech.foundation.web.error.RequestBodyFieldErrorProjector;
import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;
import com.paytech.foundation.web.logging.WebLoggingProperties;
import com.paytech.foundation.web.request.RequestIdFilter;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

class MvcProblemResponseAutoTest {

    private static final Instant RESPONSE_TIME = Instant.parse("2026-09-06T10:20:30.123456789Z");
    private static final String REQUEST_ID = "request-profile-10001";
    private static final String PRIVATE_DETAIL = "password=must-not-leak";

    private final Logger accessLogger = (Logger) LoggerFactory.getLogger(HttpRequestResponseLoggingFilter.class);
    private final ListAppender<ILoggingEvent> accessAppender = new ListAppender<>();
    private final JsonMapper objectMapper = JsonMapper.builder().build();

    @BeforeEach
    void attachAppender() {
        accessAppender.start();
        accessLogger.addAppender(accessAppender);
    }

    @AfterEach
    void detachAppender() {
        accessLogger.detachAppender(accessAppender);
        accessAppender.stop();
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @Test
    void test_realMvcEndpointsProduceExactProfilesForOwnedFailures() throws Exception {
        MockMvc mvc = mvc(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper, defaultResolver());

        assertProblem(mvc.perform(get("/contract/business").header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andReturn(), HttpStatus.BAD_REQUEST, CommonErrorCode.BUSINESS_CHECK_FAILED.getCode(),
            "Business rule rejected the request", false);
        assertProblem(mvc.perform(get("/contract/concurrent").header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andReturn(), HttpStatus.CONFLICT, CommonErrorCode.CONCURRENT_REQUEST.getCode(),
            "Order is already being processed", false);
        assertProblem(mvc.perform(get("/contract/unavailable").header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andReturn(), HttpStatus.SERVICE_UNAVAILABLE, CommonErrorCode.SERVICE_UNAVAILABLE.getCode(),
            CommonErrorCode.SERVICE_UNAVAILABLE.getMessage(), false);
        MvcResult unknown = mvc.perform(get("/contract/unknown").header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andReturn();
        assertProblem(unknown, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR.getCode(),
            CommonErrorCode.INTERNAL_ERROR.getMessage(), false);
        assertFalse(unknown.getResponse().getContentAsString().contains(PRIVATE_DETAIL));
    }

    @Test
    void test_bodyValidationUses422AndOnlySafeExternalFields() throws Exception {
        MockMvc mvc = mvc(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper, defaultResolver());
        String invalidBody = """
            {"client_reference":"", "items":[{"unit_amount":0}], "secretNote":"safe", "unsafe.name":""}
            """;

        MvcResult result = mvc.perform(post("/contract/orders")
            .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content(invalidBody))
            .andReturn();
        JsonNode body = assertProblem(result, HttpStatus.UNPROCESSABLE_CONTENT,
            CommonErrorCode.VALIDATION_ERROR.getCode(), CommonErrorCode.VALIDATION_ERROR.getMessage(), true);

        JsonNode errors = body.get("errors");
        assertEquals(2, errors.size());
        assertEquals(Set.of("field", "message"), Set.copyOf(errors.get(0).propertyNames()));
        assertEquals("client_reference", errors.get(0).get("field").stringValue());
        assertEquals("must not be blank", errors.get(0).get("message").stringValue());
        assertEquals(Set.of("field", "message"), Set.copyOf(errors.get(1).propertyNames()));
        assertEquals("items[0].unit_amount", errors.get(1).get("field").stringValue());
        assertEquals("must be greater than or equal to the minimum", errors.get(1).get("message").stringValue());
        assertFalse(result.getResponse().getContentAsString().contains("unsafe.name"));

        MvcResult unsafeOnly = mvc.perform(post("/contract/orders")
            .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content("""
                {"client_reference":"valid", "items":[{"unit_amount":1}],
                 "secretNote":"safe", "unsafe.name":""}
                """))
            .andReturn();
        assertProblem(unsafeOnly, HttpStatus.UNPROCESSABLE_CONTENT,
            CommonErrorCode.VALIDATION_ERROR.getCode(), CommonErrorCode.VALIDATION_ERROR.getMessage(), false);
    }

    @Test
    void test_malformedJsonIs400WithoutFieldErrors() throws Exception {
        MockMvc mvc = mvc(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper, defaultResolver());

        MvcResult result = mvc.perform(post("/contract/orders")
            .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
            .contentType(MediaType.APPLICATION_JSON)
            .content("{\"client_reference\":"))
            .andReturn();

        assertProblem(result, HttpStatus.BAD_REQUEST, CommonErrorCode.INVALID_REQUEST.getCode(),
            CommonErrorCode.INVALID_REQUEST.getMessage(), false);
    }

    @Test
    void test_notFoundMethodAndMediaMappingsRetainOnlyApprovedStandardHeaders() throws Exception {
        MockMvc mvc = mvc(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper, defaultResolver());

        assertProblem(mvc.perform(get("/contract/not-found").header(RequestIdFilter.HEADER_NAME, REQUEST_ID))
            .andReturn(), HttpStatus.NOT_FOUND, CommonErrorCode.NOT_FOUND.getCode(),
            CommonErrorCode.NOT_FOUND.getMessage(), false);

        MvcResult method = mvc.perform(post("/contract/get-only")
            .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
            .contentType(MediaType.APPLICATION_JSON))
            .andReturn();
        assertProblem(method, HttpStatus.METHOD_NOT_ALLOWED, CommonErrorCode.METHOD_NOT_ALLOWED.getCode(),
            CommonErrorCode.METHOD_NOT_ALLOWED.getMessage(), false);
        assertEquals("GET", method.getResponse().getHeader(HttpHeaders.ALLOW));

        MvcResult media = mvc.perform(post("/contract/orders")
            .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
            .contentType(MediaType.TEXT_PLAIN)
            .content("not-json"))
            .andReturn();
        assertProblem(media, HttpStatus.UNSUPPORTED_MEDIA_TYPE, CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.getCode(),
            CommonErrorCode.UNSUPPORTED_MEDIA_TYPE.getMessage(), false);
        assertTrue(media.getResponse().getHeaders(HttpHeaders.ACCEPT).contains(MediaType.APPLICATION_JSON_VALUE));
        assertNull(media.getResponse().getHeader("X-Internal-Diagnostic"));
    }

    @Test
    void test_requestBodyProjectorTranslatesNamingAndRejectsUnsafeOrAmbiguousPaths() throws Exception {
        JsonMapper snakeCaseMapper = JsonMapper.builder()
            .propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
            .build();
        RequestBodyFieldErrorProjector projector = new RequestBodyFieldErrorProjector(snakeCaseMapper);
        MethodParameter bodyParameter = new MethodParameter(
            ValidationTarget.class.getDeclaredMethod("bodyOnly", CheckoutBody.class), 0);
        BeanPropertyBindingResult errors = new BeanPropertyBindingResult(validBody(), "checkoutBody");
        addFieldError(errors, "items[0].unitAmount", "DecimalMin", PRIVATE_DETAIL);
        addFieldError(errors, "clientReference", "NotBlank", PRIVATE_DETAIL);
        addFieldError(errors, "clientReference", "NotBlank", PRIVATE_DETAIL);
        addFieldError(errors, "secretNote", "OwnerConstraint", PRIVATE_DETAIL);
        addFieldError(errors, "metadata", "NotEmpty", PRIVATE_DETAIL);
        addFieldError(errors, "metadata[apiKey]", "NotBlank", PRIVATE_DETAIL);
        addFieldError(errors, "items['0'].unitAmount", "DecimalMin", PRIVATE_DETAIL);
        addFieldError(errors, "CheckoutBody.clientReference", "NotBlank", PRIVATE_DETAIL);
        addFieldError(errors, "unknownField", "NotBlank", PRIVATE_DETAIL);
        addFieldError(errors, "unsafeName", "NotBlank", PRIVATE_DETAIL);
        errors.addError(new ObjectError("checkoutBody", new String[]{"OwnerGlobal"}, null, PRIVATE_DETAIL));

        List<ProblemFieldError> projected = projector.project(bodyParameter, errors);

        assertEquals(List.of(
            new ProblemFieldError("client_reference", "must not be blank"),
            new ProblemFieldError("items[0].unit_amount", "must be greater than or equal to the minimum"),
            new ProblemFieldError("secret_note", "is invalid")
        ), projected);
        assertFalse(projected.toString().contains(PRIVATE_DETAIL));

        MethodParameter ambiguousParameter = new MethodParameter(
            ValidationTarget.class.getDeclaredMethod("ambiguous", AmbiguousBody.class), 0);
        AmbiguousBody ambiguousBody = new AmbiguousBody("first", "second");
        BeanPropertyBindingResult ambiguousErrors =
            new BeanPropertyBindingResult(ambiguousBody, "ambiguousBody");
        addFieldError(ambiguousErrors, "clientReference", "NotBlank", PRIVATE_DETAIL);

        assertEquals(List.of(), projector.project(ambiguousParameter, ambiguousErrors));
    }

    @Test
    void test_fallbackOrderStaysInsideRequestIdAndLoggingFilters() {
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 10, OrderUtils.getOrder(RequestIdFilter.class));
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 20,
            OrderUtils.getOrder(HttpRequestResponseLoggingFilter.class));
        assertEquals(Ordered.HIGHEST_PRECEDENCE + 30, OrderUtils.getOrder(ProblemExceptionFilter.class));
    }

    @Test
    void test_handlerMethodValidationUses422OnlyWhenEveryViolationBelongsToRequestBody() throws Exception {
        MvcProblemExceptionMapper mapper = mapper(defaultResolver(), objectMapper);
        HandlerMethodValidationException bodyOnly = methodValidation(false, false);
        HandlerMethodValidationException mixed = methodValidation(false, true);
        HandlerMethodValidationException returnValue = methodValidation(true, false);

        MappedProblem bodyProblem = mapper.map(bodyOnly, request("POST"));
        MappedProblem mixedProblem = mapper.map(mixed, request("POST"));
        MappedProblem returnProblem = mapper.map(returnValue, request("POST"));

        assertEquals(HttpStatus.UNPROCESSABLE_CONTENT, bodyProblem.status());
        assertEquals(CommonErrorCode.VALIDATION_ERROR, bodyProblem.errorCode());
        assertEquals(List.of(new ProblemFieldError("client_reference", "must not be blank")), bodyProblem.errors());
        assertEquals(HttpStatus.BAD_REQUEST, mixedProblem.status());
        assertEquals(CommonErrorCode.INVALID_REQUEST, mixedProblem.errorCode());
        assertTrue(mixedProblem.errors().isEmpty());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, returnProblem.status());
        assertEquals(CommonErrorCode.INTERNAL_ERROR, returnProblem.errorCode());
        assertTrue(returnProblem.errors().isEmpty());
    }

    @Test
    void test_missingPathVariableRetainsSpringStandardStatusVariants() throws Exception {
        MvcProblemExceptionMapper mapper = mapper(defaultResolver(), objectMapper);
        MethodParameter parameter = new MethodParameter(
            ValidationTarget.class.getDeclaredMethod("pathVariable", String.class), 0);

        MappedProblem missing = mapper.map(new MissingPathVariableException("resourceId", parameter), request("GET"));
        MappedProblem missingAfterConversion = mapper.map(
            new MissingPathVariableException("resourceId", parameter, true), request("GET"));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, missing.status());
        assertEquals(CommonErrorCode.INTERNAL_ERROR, missing.errorCode());
        assertEquals(HttpStatus.BAD_REQUEST, missingAfterConversion.status());
        assertEquals(CommonErrorCode.INVALID_REQUEST, missingAfterConversion.errorCode());
    }

    @Test
    void test_headAdviceWritesHeadersButNeverReadsClockOrSerializes() throws Exception {
        CountingClock clock = new CountingClock(RESPONSE_TIME);
        CountingJsonMapper countingMapper = new CountingJsonMapper();
        MockMvc mvc = mvc(clock, countingMapper, defaultResolver());

        MvcResult result = mvc.perform(head("/contract/business")
            .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)).andReturn();

        assertEquals(HttpStatus.BAD_REQUEST.value(), result.getResponse().getStatus());
        assertEquals("application/problem+json", result.getResponse().getContentType());
        assertEquals(REQUEST_ID, result.getResponse().getHeader(RequestIdFilter.HEADER_NAME));
        assertEquals(0, result.getResponse().getContentAsByteArray().length);
        assertEquals(0, clock.readCount());
        assertEquals(0, countingMapper.serializationCount());
    }

    @Test
    void test_writerGeneratesOneValidFinalRequestIdWhenTheFilterAttributeIsUnavailable() throws Exception {
        ApiProblemResponseFactory factory =
            new ApiProblemResponseFactory(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC));
        ProblemResponseWriter writer = new ProblemResponseWriter(factory, objectMapper);
        MappedProblem problem = new MappedProblem(HttpStatus.INTERNAL_SERVER_ERROR,
            CommonErrorCode.INTERNAL_ERROR, CommonErrorCode.INTERNAL_ERROR.getMessage(), List.of(), HttpHeaders.EMPTY);

        for (String invalidAttribute : List.of("", "invalid:request-id")) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/without-request-id-filter");
            if (!invalidAttribute.isEmpty()) {
                request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, invalidAttribute);
            }
            MockHttpServletResponse response = new MockHttpServletResponse();

            writer.write(request, response, problem);

            String finalRequestId = response.getHeader(RequestIdFilter.HEADER_NAME);
            JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
            assertTrue(ApiProblemProfile.isValidRequestId(finalRequestId));
            assertEquals(finalRequestId, body.get("requestId").stringValue());
            assertEquals(finalRequestId, request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
            assertEquals(List.of(finalRequestId), response.getHeaders(RequestIdFilter.HEADER_NAME));
            assertFalse("unavailable".equals(finalRequestId));
        }

        CountingClock countingClock = new CountingClock(RESPONSE_TIME);
        CountingJsonMapper countingMapper = new CountingJsonMapper();
        ProblemResponseWriter headWriter =
            new ProblemResponseWriter(new ApiProblemResponseFactory(countingClock), countingMapper);
        MockHttpServletRequest headRequest = new MockHttpServletRequest("HEAD", "/without-request-id-filter");
        MockHttpServletResponse headResponse = new MockHttpServletResponse();

        headWriter.write(headRequest, headResponse, problem);

        String headRequestId = headResponse.getHeader(RequestIdFilter.HEADER_NAME);
        assertTrue(ApiProblemProfile.isValidRequestId(headRequestId));
        assertEquals(headRequestId, headRequest.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
        assertEquals(0, headResponse.getContentAsByteArray().length);
        assertEquals(0, countingClock.readCount());
        assertEquals(0, countingMapper.serializationCount());
    }

    @Test
    void test_fallbackReplacesUncommittedPartialResponseBeforeSingleTerminalLog() throws Exception {
        Components components = components(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper,
            defaultResolver(), new WebLoggingProperties(List.of()));
        MockHttpServletRequest request = request("GET");
        request.setRequestURI("/before-dispatcher");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MDC.put(RequestIdFilter.MDC_KEY, "caller-request-id");

        FilterChain endpoint = (incoming, outgoing) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) outgoing;
            httpResponse.setStatus(HttpStatus.ACCEPTED.value());
            httpResponse.setContentType(MediaType.TEXT_PLAIN_VALUE);
            httpResponse.setHeader("X-Internal-Diagnostic", PRIVATE_DETAIL);
            httpResponse.getWriter().write("partial-" + PRIVATE_DETAIL);
            throw new IllegalStateException(PRIVATE_DETAIL);
        };

        components.requestIdFilter().doFilter(request, response, (firstRequest, firstResponse) ->
            components.loggingFilter().doFilter(firstRequest, firstResponse, (secondRequest, secondResponse) ->
                components.problemFilter().doFilter(secondRequest, secondResponse, endpoint)));

        JsonNode body = assertProblem(response, HttpStatus.INTERNAL_SERVER_ERROR,
            CommonErrorCode.INTERNAL_ERROR.getCode(), CommonErrorCode.INTERNAL_ERROR.getMessage(), false);
        assertFalse(body.toString().contains(PRIVATE_DETAIL));
        assertNull(response.getHeader("X-Internal-Diagnostic"));
        assertEquals("caller-request-id", MDC.get(RequestIdFilter.MDC_KEY));
        assertEquals(1, accessAppender.list.size());
        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR.value(),
            structuredValue(accessAppender.list.getFirst(), "status"));
        assertFalse(accessAppender.list.getFirst().getKeyValuePairs().toString().contains("partial-"));
    }

    @Test
    void test_fallbackHeadWritesZeroBytesWithoutClockOrSerializer() throws Exception {
        CountingClock clock = new CountingClock(RESPONSE_TIME);
        CountingJsonMapper mapper = new CountingJsonMapper();
        Components components = components(clock, mapper, defaultResolver(), new WebLoggingProperties(List.of()));
        MockHttpServletRequest request = request("HEAD");
        request.setRequestURI("/before-dispatcher");
        MockHttpServletResponse response = new MockHttpServletResponse();

        components.requestIdFilter().doFilter(request, response, (firstRequest, firstResponse) ->
            components.loggingFilter().doFilter(firstRequest, firstResponse, (secondRequest, secondResponse) ->
                components.problemFilter().doFilter(secondRequest, secondResponse, (incoming, outgoing) -> {
                    throw new ConcurrentRequestException("already processing");
                })));

        assertEquals(HttpStatus.CONFLICT.value(), response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertEquals(REQUEST_ID, response.getHeader(RequestIdFilter.HEADER_NAME));
        assertEquals(0, response.getContentAsByteArray().length);
        assertEquals(0, clock.readCount());
        assertEquals(0, mapper.serializationCount());
    }

    @Test
    void test_fallbackRethrowsCommittedResponseAndDisconnectedClient() throws Exception {
        Components components = components(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper,
            defaultResolver(), new WebLoggingProperties(List.of()));
        MockHttpServletRequest committedRequest = request("GET");
        MockHttpServletResponse committedResponse = new MockHttpServletResponse();
        IllegalStateException committedFailure = new IllegalStateException("after commit");

        IllegalStateException actualCommitted = assertThrows(IllegalStateException.class,
            () -> components.problemFilter().doFilter(committedRequest, committedResponse, (incoming, outgoing) -> {
                ServletResponse servletResponse = outgoing;
                servletResponse.getWriter().write("already committed");
                servletResponse.flushBuffer();
                throw committedFailure;
            }));
        assertSame(committedFailure, actualCommitted);
        assertEquals("already committed", committedResponse.getContentAsString());

        IOException transportAbort = new EOFException("client disconnected");
        IOException actualAbort = assertThrows(IOException.class,
            () -> components.problemFilter().doFilter(request("GET"), new MockHttpServletResponse(),
                (incoming, outgoing) -> {
                    throw transportAbort;
                }));
        assertSame(transportAbort, actualAbort);
    }

    @Test
    void test_fallbackMapsOrdinaryDirectAndWrappedServerIoFailures() throws Exception {
        Components components = components(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper,
            defaultResolver(), new WebLoggingProperties(List.of()));
        IOException directFailure = new IOException("failed to read a server-owned file");
        MockHttpServletResponse directResponse = new MockHttpServletResponse();

        components.problemFilter().doFilter(request("GET"), directResponse, (incoming, outgoing) -> {
            throw directFailure;
        });

        assertProblem(directResponse, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR.getCode(),
            CommonErrorCode.INTERNAL_ERROR.getMessage(), false);
        ServletException wrappedFailure =
            new ServletException("application adapter failed", new IOException("server-side storage failure"));
        MockHttpServletResponse wrappedResponse = new MockHttpServletResponse();

        components.problemFilter().doFilter(request("GET"), wrappedResponse, (incoming, outgoing) -> {
            throw wrappedFailure;
        });

        assertProblem(wrappedResponse, HttpStatus.INTERNAL_SERVER_ERROR, CommonErrorCode.INTERNAL_ERROR.getCode(),
            CommonErrorCode.INTERNAL_ERROR.getMessage(), false);
    }

    @Test
    void test_localProjectedBodyValidationUsesSharedWriterAndNeverExposesCause() throws Exception {
        var components = components(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper,
            defaultResolver(), new WebLoggingProperties(List.of()));
        var response = new MockHttpServletResponse();
        components.problemFilter().doFilter(request("POST"), response, (incoming, outgoing) -> {
            throw new RequestBodyValidationException(List.of(new ProblemFieldError("graph.nodes[1].decisionCode",
                "请检查此处配置")), new IllegalStateException(PRIVATE_DETAIL));
        });
        assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT, "VALIDATION_ERROR",
            CommonErrorCode.VALIDATION_ERROR.getMessage(), true);
        var fields = objectMapper.readTree(response.getContentAsString()).path("errors");
        assertEquals("graph.nodes[1].decisionCode", fields.get(0).path("field").asString());
        assertFalse(response.getContentAsString().contains(PRIVATE_DETAIL));
    }

    /** Owner 业务码携带字段清单：保留 Owner 码与安全消息，状态按 Owner 解析（默认 422），errors 去重排序。 */
    @Test
    void test_ownerCodeFieldErrorsKeepOwnerCodeAndSortedErrors() throws Exception {
        var components = components(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper,
            defaultResolver(), new WebLoggingProperties(List.of()));
        var response = new MockHttpServletResponse();
        components.problemFilter().doFilter(request("POST"), response, (incoming, outgoing) -> {
            throw new RequestBodyValidationException(OwnerFieldError.INVALID_FORM, List.of(
                new ProblemFieldError("contact.phone", "手机号须带国家码"),
                new ProblemFieldError("address.city", "城市过长"),
                new ProblemFieldError("contact.phone", "手机号须带国家码")), new IllegalStateException(PRIVATE_DETAIL));
        });
        var body = assertProblem(response, HttpStatus.UNPROCESSABLE_CONTENT, OwnerFieldError.INVALID_FORM.getCode(),
            OwnerFieldError.INVALID_FORM.getMessage(), true);
        var fields = body.path("errors");
        assertEquals(2, fields.size());
        assertEquals("address.city", fields.get(0).path("field").asString());
        assertEquals("contact.phone", fields.get(1).path("field").asString());
        assertEquals("手机号须带国家码", fields.get(1).path("message").asString());
        assertFalse(response.getContentAsString().contains(PRIVATE_DETAIL));

        ApiErrorStatusResolver ownerResolver = (errorCode, defaultStatus) ->
            errorCode == OwnerFieldError.INVALID_FORM ? HttpStatus.BAD_REQUEST : defaultStatus;
        var owner = components(Clock.fixed(RESPONSE_TIME, ZoneOffset.UTC), objectMapper, ownerResolver,
            new WebLoggingProperties(List.of()));
        var ownerResponse = new MockHttpServletResponse();
        owner.problemFilter().doFilter(request("POST"), ownerResponse, (incoming, outgoing) -> {
            throw new RequestBodyValidationException(OwnerFieldError.INVALID_FORM,
                List.of(new ProblemFieldError("contact.phone", "手机号须带国家码")), null);
        });
        assertProblem(ownerResponse, HttpStatus.BAD_REQUEST, OwnerFieldError.INVALID_FORM.getCode(),
            OwnerFieldError.INVALID_FORM.getMessage(), true);
    }

    /** 测试用 Owner 业务码。 */
    enum OwnerFieldError implements com.paytech.foundation.common.enums.CodeMessageEnum {
        INVALID_FORM;

        @Override
        public String getCode() {
            return "INVALID_FORM";
        }

        @Override
        public String getMessage() {
            return "表单字段不符合约定";
        }
    }

    @Test
    void test_embeddedConnectorSuppliesParseableDateWithoutCustomTimestampHeader() throws Exception {
        try (var context = new SpringApplicationBuilder(ConnectorApplication.class)
            .web(WebApplicationType.SERVLET)
            .properties("server.port=0", "spring.main.banner-mode=off")
            .run()) {
            int port = ((WebServerApplicationContext) context).getWebServer().getPort();
            HttpRequest request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/date/failure"))
                .header(RequestIdFilter.HEADER_NAME, REQUEST_ID)
                .GET()
                .build();

            HttpResponse<String> response = HttpClient.newHttpClient()
                .send(request, HttpResponse.BodyHandlers.ofString());

            assertEquals(HttpStatus.BAD_REQUEST.value(), response.statusCode());
            String date = response.headers().firstValue(HttpHeaders.DATE).orElseThrow();
            assertNotNull(ZonedDateTime.parse(date, DateTimeFormatter.RFC_1123_DATE_TIME));
            assertTrue(response.headers().allValues(HttpHeaders.DATE).size() == 1);
            assertTrue(response.headers().firstValue("X-Timestamp").isEmpty());
            assertEquals("application/problem+json",
                response.headers().firstValue(HttpHeaders.CONTENT_TYPE).orElseThrow());
            assertEquals(REQUEST_ID, response.headers().firstValue(RequestIdFilter.HEADER_NAME).orElseThrow());
        }
    }

    private MockMvc mvc(Clock clock, JsonMapper mapper, ApiErrorStatusResolver resolver) {
        Components components = components(clock, mapper, resolver, new WebLoggingProperties(List.of()));
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(new ContractController())
            .setControllerAdvice(components.handler())
            .setMessageConverters(new JacksonJsonHttpMessageConverter(mapper))
            .setValidator(validator)
            .addFilters(components.requestIdFilter(), components.loggingFilter(), components.problemFilter())
            .build();
    }

    private Components components(Clock clock, JsonMapper mapper, ApiErrorStatusResolver resolver,
        WebLoggingProperties loggingProperties) {
        ApiProblemResponseFactory responseFactory = new ApiProblemResponseFactory(clock);
        MvcProblemExceptionMapper exceptionMapper = mapper(resolver, mapper);
        ProblemResponseWriter writer = new ProblemResponseWriter(responseFactory, mapper);
        return new Components(new RequestIdFilter(() -> REQUEST_ID),
            new HttpRequestResponseLoggingFilter(loggingProperties),
            new ProblemExceptionFilter(exceptionMapper, writer), new GlobalExceptionHandler(exceptionMapper, writer));
    }

    private MvcProblemExceptionMapper mapper(ApiErrorStatusResolver resolver, ObjectMapper mapper) {
        return new MvcProblemExceptionMapper(resolver, new RequestBodyFieldErrorProjector(mapper));
    }

    private ApiErrorStatusResolver defaultResolver() {
        return (errorCode, defaultStatus) -> defaultStatus;
    }

    private JsonNode assertProblem(MvcResult result, HttpStatus status, String code, String detail,
        boolean expectErrors) throws Exception {
        return assertProblem(result.getResponse(), status, code, detail, expectErrors);
    }

    private JsonNode assertProblem(MockHttpServletResponse response, HttpStatus status, String code, String detail,
        boolean expectErrors) throws Exception {
        assertEquals(status.value(), response.getStatus());
        assertEquals("application/problem+json", response.getContentType());
        assertEquals(List.of(REQUEST_ID), response.getHeaders(RequestIdFilter.HEADER_NAME));
        assertNull(response.getHeader("X-Timestamp"));
        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        Set<String> expectedFields = expectErrors
            ? Set.of("type", "title", "detail", "code", "requestId", "timestamp", "errors")
            : Set.of("type", "title", "detail", "code", "requestId", "timestamp");
        assertEquals(expectedFields, Set.copyOf(body.propertyNames()));
        assertEquals("about:blank", body.get("type").stringValue());
        assertEquals(status.getReasonPhrase(), body.get("title").stringValue());
        assertEquals(detail, body.get("detail").stringValue());
        assertEquals(code, body.get("code").stringValue());
        assertEquals(REQUEST_ID, body.get("requestId").stringValue());
        assertEquals("2026-09-06T10:20:30.123456Z", body.get("timestamp").stringValue());
        assertNull(body.get("status"));
        assertNull(body.get("instance"));
        assertNull(body.get("message"));
        return body;
    }

    private void addFieldError(BeanPropertyBindingResult errors, String field, String code, String message) {
        errors.addError(new FieldError(errors.getObjectName(), field, null, false,
            new String[]{code}, null, message));
    }

    private HandlerMethodValidationException methodValidation(boolean returnValue, boolean includeQuery)
        throws Exception {
        var method = ValidationTarget.class.getDeclaredMethod("mixed", CheckoutBody.class, String.class);
        MethodParameter bodyParameter = new MethodParameter(method, 0);
        BeanPropertyBindingResult bodyErrors = new BeanPropertyBindingResult(validBody(), "checkoutBody");
        addFieldError(bodyErrors, "clientReference", "NotBlank", PRIVATE_DETAIL);
        ParameterErrors bodyResult = new ParameterErrors(bodyParameter, validBody(), bodyErrors,
            null, null, null);
        List<ParameterValidationResult> results;
        if (includeQuery) {
            MethodParameter queryParameter = new MethodParameter(method, 1);
            ParameterValidationResult queryResult = new ParameterValidationResult(queryParameter, "", List.of(
                new org.springframework.context.support.DefaultMessageSourceResolvable(
                    new String[]{"NotBlank"}, null, PRIVATE_DETAIL)), null, null, null,
                (error, sourceType) -> null);
            results = List.of(bodyResult, queryResult);
        } else {
            results = List.of(bodyResult);
        }
        MethodValidationResult validationResult = new FixedMethodValidationResult(new ValidationTarget(), method,
            returnValue, results);
        return new HandlerMethodValidationException(validationResult);
    }

    private CheckoutBody validBody() {
        return new CheckoutBody("client-order-1", List.of(new CheckoutItem(BigDecimal.ONE)), "secret",
            "safe", Map.of());
    }

    private MockHttpServletRequest request(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/contract/test");
        request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, REQUEST_ID);
        return request;
    }

    private Object structuredValue(ILoggingEvent event, String key) {
        return event.getKeyValuePairs().stream()
            .filter(pair -> pair.key.equals(key))
            .findFirst()
            .orElseThrow()
            .value;
    }

    private record Components(
        RequestIdFilter requestIdFilter,
        HttpRequestResponseLoggingFilter loggingFilter,
        ProblemExceptionFilter problemFilter,
        GlobalExceptionHandler handler
    ) {
    }

    private record CheckoutBody(
        @JsonProperty("client_reference") @NotBlank String clientReference,
        @Valid @NotNull List<CheckoutItem> items,
        @NotBlank(message = PRIVATE_DETAIL) String secretNote,
        @JsonProperty("unsafe.name") @NotBlank String unsafeName,
        Map<String, String> metadata
    ) {
    }

    private record CheckoutItem(
        @JsonProperty("unit_amount") @NotNull @DecimalMin("0.01") BigDecimal unitAmount
    ) {
    }

    private record AmbiguousBody(
        String clientReference,
        @JsonProperty("client_reference") String duplicateReference
    ) {
    }

    private static final class ValidationTarget {

        @SuppressWarnings("unused")
        private void bodyOnly(@RequestBody CheckoutBody body) {
        }

        @SuppressWarnings("unused")
        private void mixed(@RequestBody CheckoutBody body, @RequestParam String query) {
        }

        @SuppressWarnings("unused")
        private void ambiguous(@RequestBody AmbiguousBody body) {
        }

        @SuppressWarnings("unused")
        private void pathVariable(@PathVariable("resourceId") String resourceId) {
        }
    }

    private record FixedMethodValidationResult(
        Object target,
        java.lang.reflect.Method method,
        boolean forReturnValue,
        List<ParameterValidationResult> parameterValidationResults
    ) implements MethodValidationResult {

        @Override
        public Object getTarget() {
            return target;
        }

        @Override
        public java.lang.reflect.Method getMethod() {
            return method;
        }

        @Override
        public boolean isForReturnValue() {
            return forReturnValue;
        }

        @Override
        public List<ParameterValidationResult> getParameterValidationResults() {
            return parameterValidationResults;
        }

        @Override
        public List<org.springframework.context.MessageSourceResolvable> getCrossParameterValidationResults() {
            return List.of();
        }
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

    private static final class CountingJsonMapper extends JsonMapper {

        private final AtomicInteger serializations = new AtomicInteger();

        @Override
        public byte[] writeValueAsBytes(Object value) {
            serializations.incrementAndGet();
            return super.writeValueAsBytes(value);
        }

        private int serializationCount() {
            return serializations.get();
        }
    }

    @RestController
    @RequestMapping("/contract")
    static class ContractController {

        @PostMapping(path = "/orders", consumes = MediaType.APPLICATION_JSON_VALUE)
        String create(@Valid @RequestBody CheckoutBody body) {
            return body.clientReference();
        }

        @GetMapping("/business")
        String business() {
            throw new BizCheckFailException("Business rule rejected the request");
        }

        @GetMapping("/concurrent")
        String concurrent() {
            throw new ConcurrentRequestException("Order is already being processed");
        }

        @GetMapping("/unavailable")
        String unavailable() {
            throw new ServiceUnavailableException(new IllegalStateException(PRIVATE_DETAIL));
        }

        @GetMapping("/unknown")
        String unknown() {
            throw new IllegalStateException(PRIVATE_DETAIL);
        }

        @GetMapping("/not-found")
        String notFound() throws NoResourceFoundException {
            throw new NoResourceFoundException(HttpMethod.GET, "/missing", "No static resource");
        }

        @GetMapping("/get-only")
        String getOnly() {
            return "ok";
        }
    }

    @SpringBootConfiguration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import(ConnectorController.class)
    static class ConnectorApplication {
    }

    @RestController
    static class ConnectorController {

        @GetMapping("/date/failure")
        String fail() {
            throw new BizCheckFailException("safe connector failure");
        }
    }
}
