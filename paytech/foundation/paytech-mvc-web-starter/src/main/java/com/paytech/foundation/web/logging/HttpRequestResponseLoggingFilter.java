package com.paytech.foundation.web.logging;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.ContentCachingRequestWrapper;
import org.springframework.web.util.ContentCachingResponseWrapper;

import com.paytech.foundation.web.request.RequestIdFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 在请求结束时记录 HTTP 请求、响应和耗时。
 *
 * @author Codex
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public final class HttpRequestResponseLoggingFilter extends OncePerRequestFilter {

    /** 敏感请求在共享异常处理和后续派发中延续禁止内容日志的标记。 */
    public static final String METADATA_ONLY_REQUEST_ATTRIBUTE =
        HttpRequestResponseLoggingFilter.class.getName() + ".metadataOnly";

    private static final Logger LOGGER = LoggerFactory.getLogger(HttpRequestResponseLoggingFilter.class);
    private static final String UNRESOLVED_PATH = "/[unresolved]";

    private final WebLoggingProperties properties;

    public HttpRequestResponseLoggingFilter() {
        this(new WebLoggingProperties(List.of()));
    }

    public HttpRequestResponseLoggingFilter(WebLoggingProperties properties) {
        this.properties = Objects.requireNonNull(properties, "日志配置不能为空");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        String safePath = metadataOnlyPath(request);
        if (safePath != null) {
            filterWithoutBodyCaching(request, response, filterChain, safePath);
            return;
        }
        filterWithBodyCaching(request, response, filterChain);
    }

    private void filterWithBodyCaching(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        ContentCachingRequestWrapper cachedRequest =
            new ContentCachingRequestWrapper(request, Integer.MAX_VALUE);
        ContentCachingResponseWrapper cachedResponse = new ContentCachingResponseWrapper(response);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(cachedRequest, cachedResponse);
        } finally {
            try {
                completeRequestCache(cachedRequest);
                log(cachedRequest, cachedResponse, startedAt);
            } finally {
                cachedResponse.copyBodyToResponse();
            }
        }
    }

    private void completeRequestCache(ContentCachingRequestWrapper request) {
        try {
            request.getInputStream().transferTo(OutputStream.nullOutputStream());
        } catch (IOException | RuntimeException exception) {
            LOGGER.atWarn()
                .addKeyValue("requestId", request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE))
                .addKeyValue("exceptionType", exception.getClass().getName())
                .log("HTTP request body cache completion failed");
        }
    }

    private void filterWithoutBodyCaching(HttpServletRequest request, HttpServletResponse response,
        FilterChain filterChain, String safePath) throws ServletException, IOException {
        request.setAttribute(METADATA_ONLY_REQUEST_ATTRIBUTE, Boolean.TRUE);
        long startedAt = System.nanoTime();
        try {
            filterChain.doFilter(request, response);
        } finally {
            LOGGER.atInfo()
                .addKeyValue("requestId", request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE))
                .addKeyValue("method", request.getMethod())
                .addKeyValue("uri", safePath)
                .addKeyValue("status", response.getStatus())
                .addKeyValue("elapsedMs", TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt))
                .log("HTTP request completed");
        }
    }

    private String metadataOnlyPath(HttpServletRequest request) {
        if (Boolean.TRUE.equals(request.getAttribute(METADATA_ONLY_REQUEST_ATTRIBUTE))) {
            return UNRESOLVED_PATH;
        }
        String path = applicationPath(request);
        if (!WebLoggingProperties.isCanonicalPath(path)) {
            return UNRESOLVED_PATH;
        }
        for (String prefix : properties.metadataOnlyPathPrefixes()) {
            if (prefix.equals("/") || path.equals(prefix) || path.startsWith(prefix + "/")) {
                return prefix.equals("/") ? "/**" : prefix + "/**";
            }
        }
        return null;
    }

    private String applicationPath(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        if (uri == null || contextPath == null) {
            return null;
        }
        if (contextPath.isEmpty()) {
            return uri;
        }
        if (!WebLoggingProperties.isCanonicalPath(contextPath)) {
            return null;
        }
        if (uri.equals(contextPath)) {
            return "/";
        }
        return uri.startsWith(contextPath + "/") ? uri.substring(contextPath.length()) : null;
    }

    private void log(ContentCachingRequestWrapper request, ContentCachingResponseWrapper response, long startedAt) {
        long elapsedMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt);
        LOGGER.atInfo()
            .addKeyValue("requestId", request.getAttribute(RequestIdFilter.REQUEST_ATTRIBUTE))
            .addKeyValue("method", request.getMethod())
            .addKeyValue("uri", request.getRequestURI())
            .addKeyValue("query", request.getQueryString())
            .addKeyValue("requestBody", decode(request.getContentAsByteArray(), request.getContentType()))
            .addKeyValue("status", response.getStatus())
            .addKeyValue("responseBody", decode(response.getContentAsByteArray(), response.getContentType()))
            .addKeyValue("elapsedMs", elapsedMs)
            .log("HTTP request completed");
    }

    private String decode(byte[] content, String contentType) {
        Charset charset = StandardCharsets.UTF_8;
        try {
            Charset declaredCharset = contentType == null ? null : MediaType.parseMediaType(contentType).getCharset();
            charset = declaredCharset == null ? StandardCharsets.UTF_8 : declaredCharset;
        } catch (IllegalArgumentException ignored) {
            // Use UTF-8 when Content-Type or its charset is invalid.
        }
        return new String(content, charset);
    }
}
