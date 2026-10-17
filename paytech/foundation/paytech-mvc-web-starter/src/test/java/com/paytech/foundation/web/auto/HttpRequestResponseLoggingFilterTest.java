package com.paytech.foundation.web.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import com.paytech.foundation.web.autoconfigure.WebAutoConfiguration;
import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;
import com.paytech.foundation.web.request.RequestIdFilter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ReadListener;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.HttpServletResponse;

class HttpRequestResponseLoggingFilterTest {

    private static final Set<String> STRUCTURED_VALUE_NAMES = Set.of("requestId", "method", "uri", "query",
        "requestBody", "status", "responseBody", "elapsedMs");

    private final Logger logger =
        (Logger) LoggerFactory.getLogger(HttpRequestResponseLoggingFilter.class);
    private final ListAppender<ILoggingEvent> appender = new ListAppender<>();

    @BeforeEach
    void attachAppender() {
        appender.start();
        logger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        logger.detachAppender(appender);
        appender.stop();
        MDC.remove(RequestIdFilter.MDC_KEY);
    }

    @Test
    void preservesFilterChainBodiesAndLogsCompletedRequest() throws Exception {
        RequestIdFilter requestIdFilter = new RequestIdFilter(() -> "request-10006");
        HttpRequestResponseLoggingFilter loggingFilter = new HttpRequestResponseLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setRequestURI("/payments");
        request.setQueryString("expand=fees");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"cardToken\":\"tok_10001\"}".getBytes(StandardCharsets.UTF_8));

        FilterChain endpoint = (servletRequest, servletResponse) -> {
            String body = new String(servletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            assertEquals("{\"cardToken\":\"tok_10001\"}", body);
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(201);
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.getWriter().write("{\"paymentId\":\"pay_10001\"}");
        };

        requestIdFilter.doFilter(request, response,
            (servletRequest, servletResponse) -> loggingFilter.doFilter(servletRequest, servletResponse, endpoint));

        assertEquals("{\"paymentId\":\"pay_10001\"}",
            new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        Map<String, Object> values = structuredValues(singleEvent());
        assertEquals(STRUCTURED_VALUE_NAMES, values.keySet());
        assertEquals("request-10006", values.get("requestId"));
        assertEquals("POST", values.get("method"));
        assertEquals("/payments", values.get("uri"));
        assertEquals("expand=fees", values.get("query"));
        assertEquals("{\"cardToken\":\"tok_10001\"}", values.get("requestBody"));
        assertEquals(201, values.get("status"));
        assertEquals("{\"paymentId\":\"pay_10001\"}", values.get("responseBody"));
        assertTrue(((Number) values.get("elapsedMs")).longValue() >= 0);
    }

    @Test
    void logsEntireRequestBodyWhenDownstreamDoesNotReadIt() throws Exception {
        HttpRequestResponseLoggingFilter loggingFilter = new HttpRequestResponseLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setRequestURI("/payments");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent("{\"cardToken\":\"tok_10002\"}".getBytes(StandardCharsets.UTF_8));

        FilterChain endpoint = (servletRequest, servletResponse) ->
            ((HttpServletResponse) servletResponse).setStatus(202);

        loggingFilter.doFilter(request, response, endpoint);

        Map<String, Object> values = structuredValues(singleEvent());
        assertEquals("{\"cardToken\":\"tok_10002\"}", values.get("requestBody"));
        assertEquals(202, values.get("status"));
    }

    @Test
    void usesUtf8ForBodiesWithoutDeclaredCharset() throws Exception {
        HttpRequestResponseLoggingFilter loggingFilter = new HttpRequestResponseLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String requestBody = "请求内容";
        String responseBody = "响应内容";
        request.setMethod("POST");
        request.setRequestURI("/payments");
        request.setContentType(MediaType.TEXT_PLAIN_VALUE);
        request.setContent(requestBody.getBytes(StandardCharsets.UTF_8));

        FilterChain endpoint = (servletRequest, servletResponse) -> {
            assertEquals(requestBody,
                new String(servletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setContentType(MediaType.TEXT_PLAIN_VALUE);
            httpResponse.getOutputStream().write(responseBody.getBytes(StandardCharsets.UTF_8));
        };

        loggingFilter.doFilter(request, response, endpoint);

        assertEquals(responseBody, new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        Map<String, Object> values = structuredValues(singleEvent());
        assertEquals(requestBody, values.get("requestBody"));
        assertEquals(responseBody, values.get("responseBody"));
    }

    @Test
    void usesUtf8WhenDeclaredCharsetIsInvalid() throws Exception {
        HttpRequestResponseLoggingFilter loggingFilter = new HttpRequestResponseLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String requestBody = "无效字符集请求";
        String responseBody = "无效字符集响应";
        request.setMethod("POST");
        request.setRequestURI("/payments");
        request.setContentType("text/plain;charset=invalid-charset");
        request.setContent(requestBody.getBytes(StandardCharsets.UTF_8));

        FilterChain endpoint = (servletRequest, servletResponse) -> {
            assertEquals(requestBody,
                new String(servletRequest.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setContentType("text/plain;charset=invalid-charset");
            httpResponse.getOutputStream().write(responseBody.getBytes(StandardCharsets.UTF_8));
        };

        loggingFilter.doFilter(request, response, endpoint);

        assertEquals(responseBody, new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        Map<String, Object> values = structuredValues(singleEvent());
        assertEquals(requestBody, values.get("requestBody"));
        assertEquals(responseBody, values.get("responseBody"));
    }

    @Test
    void logsExceptionalCompletionAndRestoresCallerMdc() {
        RequestIdFilter requestIdFilter = new RequestIdFilter(() -> "request-10007");
        HttpRequestResponseLoggingFilter loggingFilter = new HttpRequestResponseLoggingFilter();
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setMethod("POST");
        request.setRequestURI("/payments");
        String responseBody = "{\"error\":\"endpoint unavailable\"}";
        IllegalStateException failure = new IllegalStateException("endpoint unavailable");
        FilterChain endpoint = (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(500);
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.getOutputStream().write(responseBody.getBytes(StandardCharsets.UTF_8));
            throw failure;
        };
        MDC.put(RequestIdFilter.MDC_KEY, "caller-request-id");

        IllegalStateException thrown = assertThrows(IllegalStateException.class,
            () -> requestIdFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                loggingFilter.doFilter(servletRequest, servletResponse, endpoint)));

        assertSame(failure, thrown);
        assertEquals("caller-request-id", MDC.get(RequestIdFilter.MDC_KEY));
        assertEquals(responseBody, new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        Map<String, Object> values = structuredValues(singleEvent());
        assertEquals("request-10007", values.get("requestId"));
        assertEquals(500, values.get("status"));
        assertEquals(responseBody, values.get("responseBody"));
        assertTrue(((Number) values.get("elapsedMs")).longValue() >= 0);
    }

    @Test
    void preservesDownstreamFailureWhenUnreadRequestDrainFails() {
        RequestIdFilter requestIdFilter = new RequestIdFilter(() -> "request-10008");
        HttpRequestResponseLoggingFilter loggingFilter = new HttpRequestResponseLoggingFilter();
        String requestBody = "{\"secret\":\"request-body-must-not-leak\"}";
        IOException drainFailure = new IOException("failed to drain " + requestBody);
        MockHttpServletRequest request = new MockHttpServletRequest() {

            @Override
            public ServletInputStream getInputStream() {
                return new ServletInputStream() {

                    @Override
                    public int read() throws IOException {
                        throw drainFailure;
                    }

                    @Override
                    public boolean isFinished() {
                        return false;
                    }

                    @Override
                    public boolean isReady() {
                        return true;
                    }

                    @Override
                    public void setReadListener(ReadListener readListener) {
                    }
                };
            }
        };
        MockHttpServletResponse response = new MockHttpServletResponse();
        String responseBody = "{\"error\":\"business failure\"}";
        IllegalStateException businessFailure = new IllegalStateException("business failure");
        request.setMethod("POST");
        request.setRequestURI("/payments");
        request.setContentType(MediaType.APPLICATION_JSON_VALUE);
        request.setContent(requestBody.getBytes(StandardCharsets.UTF_8));
        FilterChain endpoint = (servletRequest, servletResponse) -> {
            HttpServletResponse httpResponse = (HttpServletResponse) servletResponse;
            httpResponse.setStatus(500);
            httpResponse.setContentType(MediaType.APPLICATION_JSON_VALUE);
            httpResponse.getOutputStream().write(responseBody.getBytes(StandardCharsets.UTF_8));
            throw businessFailure;
        };

        Exception thrown = assertThrows(Exception.class,
            () -> requestIdFilter.doFilter(request, response, (servletRequest, servletResponse) ->
                loggingFilter.doFilter(servletRequest, servletResponse, endpoint)));

        assertSame(businessFailure, thrown);
        assertEquals(responseBody, new String(response.getContentAsByteArray(), StandardCharsets.UTF_8));
        assertEquals(2, appender.list.size());
        ILoggingEvent drainEvent = appender.list.get(0);
        assertEquals(Level.WARN, drainEvent.getLevel());
        assertEquals("HTTP request body cache completion failed", drainEvent.getFormattedMessage());
        assertNull(drainEvent.getThrowableProxy());
        Map<String, Object> drainValues = structuredValues(drainEvent);
        assertEquals(Set.of("requestId", "exceptionType"), drainValues.keySet());
        assertEquals("request-10008", drainValues.get("requestId"));
        assertEquals(IOException.class.getName(), drainValues.get("exceptionType"));
        ILoggingEvent terminalEvent = appender.list.get(1);
        assertEquals(Level.INFO, terminalEvent.getLevel());
        assertEquals("HTTP request completed", terminalEvent.getFormattedMessage());
        Map<String, Object> terminalValues = structuredValues(terminalEvent);
        assertEquals("request-10008", terminalValues.get("requestId"));
        assertEquals(500, terminalValues.get("status"));
        assertEquals(responseBody, terminalValues.get("responseBody"));
    }

    @Test
    void autoConfiguresLoggingFilterForServletApplications() {
        try (AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext()) {
            context.setServletContext(new MockServletContext());
            context.register(WebAutoConfiguration.class);
            context.refresh();

            assertEquals(1, context.getBeansOfType(HttpRequestResponseLoggingFilter.class).size());
        }
    }

    private ILoggingEvent singleEvent() {
        assertEquals(1, appender.list.size());
        ILoggingEvent event = appender.list.get(0);
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("HTTP request completed", event.getFormattedMessage());
        return event;
    }

    private Map<String, Object> structuredValues(ILoggingEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        event.getKeyValuePairs().forEach(keyValuePair -> values.put(keyValuePair.key, keyValuePair.value));
        return values;
    }
}
