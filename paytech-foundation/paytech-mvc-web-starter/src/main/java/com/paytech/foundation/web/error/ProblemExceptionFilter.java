package com.paytech.foundation.web.error;

import java.io.IOException;
import java.util.Objects;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.util.DisconnectedClientHelper;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 处理 DispatcherServlet 之前且仍可安全写出响应的应用异常。
 *
 * @author Codex
 */
@Order(Ordered.HIGHEST_PRECEDENCE + 30)
public final class ProblemExceptionFilter extends OncePerRequestFilter {

    private final MvcProblemExceptionMapper exceptionMapper;
    private final ProblemResponseWriter responseWriter;

    public ProblemExceptionFilter(MvcProblemExceptionMapper exceptionMapper, ProblemResponseWriter responseWriter) {
        this.exceptionMapper = Objects.requireNonNull(exceptionMapper, "exceptionMapper must not be null");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
        throws ServletException, IOException {
        try {
            filterChain.doFilter(request, response);
        } catch (IOException exception) {
            if (response.isCommitted() || DisconnectedClientHelper.isClientDisconnectedException(exception)) {
                throw exception;
            }
            responseWriter.write(request, response, exceptionMapper.map(exception, request));
        } catch (ServletException exception) {
            if (response.isCommitted() || DisconnectedClientHelper.isClientDisconnectedException(exception)) {
                throw exception;
            }
            responseWriter.write(request, response, exceptionMapper.map(exception, request));
        } catch (RuntimeException exception) {
            if (response.isCommitted() || DisconnectedClientHelper.isClientDisconnectedException(exception)) {
                throw exception;
            }
            responseWriter.write(request, response, exceptionMapper.map(exception, request));
        }
    }
}
