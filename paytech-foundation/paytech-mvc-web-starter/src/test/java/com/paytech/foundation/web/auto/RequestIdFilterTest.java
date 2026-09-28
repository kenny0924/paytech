package com.paytech.foundation.web.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.paytech.foundation.web.request.RequestIdFilter;

import jakarta.servlet.FilterChain;

class RequestIdFilterTest {

    @AfterEach
    void clearMdc() {
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @ParameterizedTest
    @MethodSource("validRequestIds")
    void test_singleProfileRequestIdIsPreservedExactly(String incomingRequestId) throws Exception {
        AtomicInteger generatedCount = new AtomicInteger();
        RequestIdFilter filter = new RequestIdFilter(() -> {
            generatedCount.incrementAndGet();
            return "generated-request-id";
        });
        MockHttpServletRequest request = request(incomingRequestId);
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> requestIdSeenByChain = new AtomicReference<>();
        FilterChain chain = (servletRequest, servletResponse) ->
            requestIdSeenByChain.set(MDC.get(RequestIdFilter.MDC_KEY));

        filter.doFilter(request, response, chain);

        assertEquals(incomingRequestId, request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
        assertEquals(incomingRequestId, requestIdSeenByChain.get());
        assertEquals(List.of(incomingRequestId), response.getHeaders(RequestIdFilter.HEADER_NAME));
        assertEquals(0, generatedCount.get());
        assertNull(MDC.get(RequestIdFilter.MDC_KEY));
    }

    @ParameterizedTest
    @MethodSource("invalidIncomingHeaders")
    void test_missingDuplicateOrInvalidHeaderIsReplaced(List<String> headerValues) throws Exception {
        AtomicInteger generatedCount = new AtomicInteger();
        RequestIdFilter filter = new RequestIdFilter(() -> {
            generatedCount.incrementAndGet();
            return "Generated.Request_Id-01";
        });
        MockHttpServletRequest request = new MockHttpServletRequest();
        headerValues.forEach(value -> request.addHeader(RequestIdFilter.HEADER_NAME, value));
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (servletRequest, servletResponse) ->
            assertEquals("Generated.Request_Id-01", MDC.get(RequestIdFilter.MDC_KEY)));

        assertEquals("Generated.Request_Id-01", request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE));
        assertEquals(List.of("Generated.Request_Id-01"), response.getHeaders(RequestIdFilter.HEADER_NAME));
        assertEquals(1, generatedCount.get());
    }

    @ParameterizedTest
    @MethodSource("invalidGeneratedRequestIds")
    void test_invalidGeneratedRequestIdFailsClosed(String generatedRequestId) {
        RequestIdFilter filter = new RequestIdFilter(() -> generatedRequestId);

        IllegalStateException exception = assertThrows(IllegalStateException.class,
            () -> filter.doFilter(new MockHttpServletRequest(), new MockHttpServletResponse(),
                (request, response) -> {
                }));

        assertEquals("Generated request ID does not match the Problem Profile", exception.getMessage());
    }

    @Test
    void test_previousMdcValueIsRestoredAfterExceptionalCompletion() {
        RequestIdFilter filter = new RequestIdFilter(() -> "generated-request-id");
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException failure = new IllegalStateException("downstream failure");
        MDC.put(RequestIdFilter.MDC_KEY, "caller-request-id");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> filter.doFilter(request, response, (servletRequest, servletResponse) -> {
                throw failure;
            }));

        assertEquals(failure, thrown);
        assertEquals("caller-request-id", MDC.get(RequestIdFilter.MDC_KEY));
    }

    private static Stream<String> validRequestIds() {
        return Stream.of("A", "a.b_C-9", "R".repeat(64));
    }

    private static Stream<Arguments> invalidIncomingHeaders() {
        return Stream.of(
            Arguments.of(List.of()),
            Arguments.of(List.of("first", "second")),
            Arguments.of(List.of("first,second")),
            Arguments.of(List.of("contains:colon")),
            Arguments.of(List.of("unsafe\r\nheader")),
            Arguments.of(List.of("请求-01")),
            Arguments.of(List.of("-invalid-leading")),
            Arguments.of(List.of(".invalid-leading")),
            Arguments.of(List.of("_invalid-leading")),
            Arguments.of(List.of("R".repeat(65)))
        );
    }

    private static Stream<String> invalidGeneratedRequestIds() {
        return Stream.of(null, "", "-invalid", "contains:colon", "R".repeat(65));
    }

    private MockHttpServletRequest request(String requestId) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(RequestIdFilter.HEADER_NAME, requestId);
        return request;
    }
}
