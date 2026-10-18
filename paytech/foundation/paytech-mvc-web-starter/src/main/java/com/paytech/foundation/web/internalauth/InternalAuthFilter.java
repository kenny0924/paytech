package com.paytech.foundation.web.internalauth;

import java.io.IOException;
import java.util.Collections;
import java.util.Objects;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

import com.paytech.foundation.internalauth.api.CallerIdentityHolder;
import com.paytech.foundation.internalauth.core.inbound.InternalCallerAuthenticator;
import com.paytech.foundation.web.error.MappedProblem;
import com.paytech.foundation.web.error.ProblemResponseWriter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Servlet 入站内部调用身份校验（D26），只在 {@code internal-auth.mode} 为 DUAL 或 REQUIRED 时注册。
 * <li>拒绝时用共享 {@link ProblemResponseWriter} 写出 401 / 503 Problem，{@code code} 为内部凭证错误码。</li>
 * <li>识别出的调用方放入请求属性与 {@link CallerIdentityHolder}，请求结束清除。</li>
 * <li>位于 Request ID 与日志过滤器之后，被拒绝的请求仍有 Request ID 与终态日志；不记录 Authorization 头。</li>
 *
 * @author Claude
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 40)
public final class InternalAuthFilter extends OncePerRequestFilter {

    private final ProblemResponseWriter problemWriter;
    private final InternalCallerAuthenticator authenticator;

    /**
     * 创建过滤器。
     *
     * @param authenticator 入站判定器
     * @param problemWriter 共享 Problem 写出器
     */
    public InternalAuthFilter(InternalCallerAuthenticator authenticator, ProblemResponseWriter problemWriter) {
        this.authenticator = Objects.requireNonNull(authenticator, "authenticator must not be null");
        this.problemWriter = Objects.requireNonNull(problemWriter, "problemWriter must not be null");
    }

    /**
     * 判定后放行或拒绝；放行时绑定并在结束后清除调用方身份。
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
        throws ServletException, IOException {
        InternalCallerAuthenticator.Decision decision = authenticator.authenticate(pathWithinApplication(request),
            Collections.list(request.getHeaders(InternalCallerAuthenticator.AUTHORIZATION_HEADER)),
            Collections.list(request.getHeaders(InternalCallerAuthenticator.LEGACY_CALLER_HEADER)));
        if (decision.rejected()) {
            problemWriter.write(request, response, new MappedProblem(HttpStatus.valueOf(decision.status()),
                decision.rejection(), null, null, null));
            return;
        }
        if (decision.identity() == null) {
            chain.doFilter(request, response);
            return;
        }
        request.setAttribute(CallerIdentityHolder.REQUEST_ATTRIBUTE, decision.identity());
        CallerIdentityHolder.set(decision.identity());
        try {
            chain.doFilter(request, response);
        } finally {
            CallerIdentityHolder.clear();
        }
    }

    private static String pathWithinApplication(HttpServletRequest request) {
        String uri = request.getRequestURI();
        String contextPath = request.getContextPath();
        return contextPath != null && !contextPath.isEmpty() && uri.startsWith(contextPath)
            ? uri.substring(contextPath.length()) : uri;
    }
}
