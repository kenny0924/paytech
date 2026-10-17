package com.paytech.foundation.web.auto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.slf4j.LoggerFactory;
import org.springframework.beans.BeansException;
import org.springframework.core.env.MapPropertySource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockServletContext;
import org.springframework.web.context.support.AnnotationConfigWebApplicationContext;

import com.paytech.foundation.web.autoconfigure.WebAutoConfiguration;
import com.paytech.foundation.web.error.ApiProblemResponseFactory;
import com.paytech.foundation.web.error.GlobalExceptionHandler;
import com.paytech.foundation.web.error.MvcProblemExceptionMapper;
import com.paytech.foundation.web.error.ProblemResponseWriter;
import com.paytech.foundation.web.error.RequestBodyFieldErrorProjector;
import com.paytech.foundation.web.logging.HttpRequestResponseLoggingFilter;
import com.paytech.foundation.web.logging.WebLoggingProperties;
import com.paytech.foundation.web.request.RequestIdFilter;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletInputStream;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class MetadataOnlyRequestLoggingTest {

    private static final String SECRET = "password-and-token-must-never-be-logged";
    private static final Set<String> METADATA_FIELDS = Set.of("requestId", "method", "uri", "status", "elapsedMs");

    private final Logger accessLogger = (Logger) LoggerFactory.getLogger(HttpRequestResponseLoggingFilter.class);
    private final Logger errorLogger = (Logger) LoggerFactory.getLogger(MvcProblemExceptionMapper.class);
    private final ListAppender<ILoggingEvent> accessAppender = new ListAppender<>();
    private final ListAppender<ILoggingEvent> errorAppender = new ListAppender<>();
    private final HttpRequestResponseLoggingFilter filter =
        new HttpRequestResponseLoggingFilter(new WebLoggingProperties(List.of("/auth")));

    @BeforeEach
    void attachAppenders() {
        accessAppender.start();
        errorAppender.start();
        accessLogger.addAppender(accessAppender);
        errorLogger.addAppender(errorAppender);
    }

    @AfterEach
    void detachAppenders() {
        accessLogger.detachAppender(accessAppender);
        errorLogger.detachAppender(errorAppender);
        accessAppender.stop();
        errorAppender.stop();
    }

    @Test
    void test_sensitiveBodiesReachEndpointAndClientWithoutCachingOrLogging() throws Exception {
        MockHttpServletRequest request = request("/auth/login/" + SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();
        request.setQueryString("password=" + SECRET);
        request.setContentType("application/json");
        request.setContent(SECRET.getBytes(StandardCharsets.UTF_8));
        request.addHeader("Authorization", SECRET);
        request.setCookies(new Cookie("session", SECRET));

        filter.doFilter(request, response, (incoming, outgoing) -> {
            assertSame(request, incoming);
            assertSame(response, outgoing);
            assertEquals(SECRET, new String(incoming.getInputStream().readAllBytes(), StandardCharsets.UTF_8));
            assertEquals(SECRET, ((HttpServletRequest) incoming).getHeader("Authorization"));
            HttpServletResponse httpResponse = (HttpServletResponse) outgoing;
            httpResponse.setStatus(201);
            httpResponse.addCookie(new Cookie("session", SECRET));
            httpResponse.getOutputStream().write(SECRET.getBytes(StandardCharsets.UTF_8));
        });

        assertEquals(SECRET, response.getContentAsString());
        assertTrue(response.getHeader("Set-Cookie").contains(SECRET));
        assertMetadata("/auth/**", 201);
    }

    @Test
    void test_unmappedSensitiveRequestNeverReadsUnreadBody() throws Exception {
        MockHttpServletRequest request = unreadRequest("/auth/unknown/" + SECRET);
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (incoming, outgoing) -> {
            assertSame(request, incoming);
            assertSame(response, outgoing);
            ((HttpServletResponse) outgoing).sendError(404);
        });

        assertMetadata("/auth/**", 404);
    }

    @ParameterizedTest
    @ValueSource(strings = {"/%61uth/login", "/%2561uth/login", "/auth%2flogin", "/AUTH%2fauth", "/auth;secret/login",
        "/auth/login;secret", "/public/../auth/login", "/auth/../public", "/./auth/login", "//auth/login",
        "/auth//login", "/auth\\login", "/auth/%", "/auth/%FF", "/auth/login?secret", "/auth/login#secret",
        "/auth/\nsecret", "/鉴权/login", "relative-auth/login", ""})
    void test_ambiguousPathsNeverExposeBodyOrOriginalPath(String path) throws Exception {
        MockHttpServletRequest request = unreadRequest(path);

        filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) ->
            ((HttpServletResponse) outgoing).setStatus(400));

        assertMetadata("/[unresolved]", 400);
    }

    @Test
    void test_configuredPrefixUsesApplicationPathWithoutContextPath() throws Exception {
        MockHttpServletRequest request = unreadRequest("/platform-support/auth/login/" + SECRET);
        request.setContextPath("/platform-support");

        filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> {});

        assertMetadata("/auth/**", 200);
    }

    @Test
    void test_mismatchedContextPathFailsClosed() throws Exception {
        MockHttpServletRequest request = unreadRequest("/other/auth/login/" + SECRET);
        request.setContextPath("/platform-support");

        filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> {});

        assertMetadata("/[unresolved]", 200);
    }

    @Test
    void test_prefixIsMatchedAtSegmentBoundary() throws Exception {
        MockHttpServletRequest request = request("/authentication-status");
        request.setContent("ordinary-body".getBytes(StandardCharsets.UTF_8));

        filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) -> {});

        Map<String, Object> values = values(accessAppender.list.getFirst());
        assertEquals("ordinary-body", values.get("requestBody"));
        assertEquals("/authentication-status", values.get("uri"));
        assertNull(request.getAttribute(HttpRequestResponseLoggingFilter.METADATA_ONLY_REQUEST_ATTRIBUTE));
    }

    @Test
    void test_unknownFailureKeepsResponseSafeAndSuppressesExceptionDetails() throws Exception {
        MockHttpServletRequest request = unreadRequest("/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        JsonMapper objectMapper = JsonMapper.builder().build();
        MvcProblemExceptionMapper exceptionMapper = new MvcProblemExceptionMapper(
            (errorCode, defaultStatus) -> defaultStatus, new RequestBodyFieldErrorProjector(objectMapper));
        GlobalExceptionHandler handler = new GlobalExceptionHandler(exceptionMapper,
            new ProblemResponseWriter(new ApiProblemResponseFactory(Clock.systemUTC()), objectMapper));

        filter.doFilter(request, response, (incoming, outgoing) -> {
            handler.handleException(new IllegalStateException(SECRET), (HttpServletRequest) incoming,
                (HttpServletResponse) outgoing);
        });

        JsonNode body = objectMapper.readTree(response.getContentAsByteArray());
        assertEquals("INTERNAL_ERROR", body.get("code").stringValue());
        assertFalse(response.getContentAsString().contains(SECRET));
        assertMetadata("/auth/**", 500);
        assertEquals(1, errorAppender.list.size());
        ILoggingEvent event = errorAppender.list.getFirst();
        assertEquals("敏感请求发生未处理系统异常, requestId=request-sensitive",
            event.getFormattedMessage());
        assertNull(event.getThrowableProxy());
    }

    @Test
    void test_propagatedFailureDoesNotTriggerBodyDrainOrAdditionalLog() {
        MockHttpServletRequest request = unreadRequest("/auth/login");
        MockHttpServletResponse response = new MockHttpServletResponse();
        IllegalStateException failure = new IllegalStateException(SECRET);
        FilterChain endpoint = (incoming, outgoing) -> {
            ((HttpServletResponse) outgoing).setStatus(500);
            throw failure;
        };

        assertSame(failure,
            assertThrows(IllegalStateException.class, () -> filter.doFilter(request, response, endpoint)));

        assertMetadata("/auth/**", 500);
    }

    @Test
    void test_sensitiveAttributeProtectsErrorPath() throws Exception {
        MockHttpServletRequest request = unreadRequest("/error");
        request.setAttribute(HttpRequestResponseLoggingFilter.METADATA_ONLY_REQUEST_ATTRIBUTE, Boolean.TRUE);

        filter.doFilter(request, new MockHttpServletResponse(), (incoming, outgoing) ->
            ((HttpServletResponse) outgoing).setStatus(500));

        assertMetadata("/[unresolved]", 500);
    }

    @Test
    void test_autoConfigurationBindsPrefixesAndProtectsConfiguredEndpoint() throws Exception {
        Map<String, Object> properties = Map.of("web.logging.metadata-only-path-prefixes[0]", "/internal/auth",
            "web.logging.metadata-only-path-prefixes[1]", "/auth");
        try (AnnotationConfigWebApplicationContext context = context(properties)) {
            context.refresh();
            assertEquals(List.of("/internal/auth", "/auth"),
                context.getBean(WebLoggingProperties.class).metadataOnlyPathPrefixes());
            var configuredFilter = context.getBean(HttpRequestResponseLoggingFilter.class);
            configuredFilter.doFilter(unreadRequest("/internal/auth/" + SECRET), new MockHttpServletResponse(),
                (incoming, outgoing) -> {});
            assertMetadata("/internal/auth/**", 200);
        }
    }

    @ParameterizedTest
    @ValueSource(strings = {"auth", "", "/auth/", "/auth/**", "/auth?secret", "/auth#secret", "/%61uth", "/auth;secret",
        "/auth//login", "/auth/../login", "/auth/./login", "/auth\\login", " /auth", "/auth "})
    void test_invalidPrefixFailsStartup(String prefix) {
        try (AnnotationConfigWebApplicationContext context =
            context(Map.of("web.logging.metadata-only-path-prefixes[0]", prefix))) {
            assertThrows(BeansException.class, context::refresh);
        }
    }

    @Test
    void test_emptyDefaultAndExplicitRootPrefix() throws Exception {
        try (AnnotationConfigWebApplicationContext context = context(Map.of())) {
            context.refresh();
            assertTrue(context.getBean(WebLoggingProperties.class).metadataOnlyPathPrefixes().isEmpty());
        }
        var rootFilter = new HttpRequestResponseLoggingFilter(new WebLoggingProperties(List.of("/")));
        rootFilter.doFilter(unreadRequest("/anything/" + SECRET), new MockHttpServletResponse(),
            (incoming, outgoing) -> {});
        assertMetadata("/**", 200);
    }

    private AnnotationConfigWebApplicationContext context(Map<String, Object> properties) {
        AnnotationConfigWebApplicationContext context = new AnnotationConfigWebApplicationContext();
        context.setServletContext(new MockServletContext());
        context.getEnvironment().getPropertySources().addFirst(new MapPropertySource("test", properties));
        context.register(WebAutoConfiguration.class);
        return context;
    }

    private MockHttpServletRequest request(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setMethod("POST");
        request.setRequestURI(path);
        request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, "request-sensitive");
        return request;
    }

    private MockHttpServletRequest unreadRequest(String path) {
        MockHttpServletRequest request = new MockHttpServletRequest() {
            @Override
            public ServletInputStream getInputStream() {
                throw new AssertionError("日志保护不得获取或消费敏感请求 InputStream");
            }
        };
        request.setMethod("POST");
        request.setRequestURI(path);
        request.setAttribute(RequestIdFilter.REQUEST_ATTRIBUTE, "request-sensitive");
        return request;
    }

    private void assertMetadata(String safePath, int status) {
        assertEquals(1, accessAppender.list.size());
        ILoggingEvent event = accessAppender.list.getFirst();
        assertEquals(Level.INFO, event.getLevel());
        assertEquals("HTTP request completed", event.getFormattedMessage());
        assertNull(event.getThrowableProxy());
        Map<String, Object> values = values(event);
        assertEquals(METADATA_FIELDS, values.keySet());
        assertEquals("request-sensitive", values.get("requestId"));
        assertEquals("POST", values.get("method"));
        assertEquals(safePath, values.get("uri"));
        assertEquals(status, values.get("status"));
        assertTrue(((Number) values.get("elapsedMs")).longValue() >= 0);
        assertFalse(values.toString().contains(SECRET));
    }

    private Map<String, Object> values(ILoggingEvent event) {
        Map<String, Object> values = new LinkedHashMap<>();
        event.getKeyValuePairs().forEach(pair -> values.put(pair.key, pair.value));
        return values;
    }
}
