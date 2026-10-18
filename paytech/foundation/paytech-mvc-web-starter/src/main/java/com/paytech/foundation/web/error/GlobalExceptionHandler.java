package com.paytech.foundation.web.error;

import java.io.IOException;
import java.util.Objects;

import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 将 DispatcherServlet 内的异常交给共享映射器与 Writer。
 *
 * @author Codex
 */
@RestControllerAdvice
public final class GlobalExceptionHandler {

    private final MvcProblemExceptionMapper exceptionMapper;
    private final ProblemResponseWriter responseWriter;

    public GlobalExceptionHandler(MvcProblemExceptionMapper exceptionMapper, ProblemResponseWriter responseWriter) {
        this.exceptionMapper = Objects.requireNonNull(exceptionMapper, "exceptionMapper must not be null");
        this.responseWriter = Objects.requireNonNull(responseWriter, "responseWriter must not be null");
    }

    /**
     * 所有 MVC 异常经同一路径分类并写出。
     *
     * @param exception
     *            已解析的 MVC 异常
     * @param request
     *            当前请求
     * @param response
     *            当前响应
     * @throws IOException
     *             响应写入失败
     */
    @ExceptionHandler(Exception.class)
    public void handleException(Exception exception, HttpServletRequest request, HttpServletResponse response)
        throws IOException {
        responseWriter.write(request, response, exceptionMapper.map(exception, request));
    }
}
