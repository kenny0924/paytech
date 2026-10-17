package com.paytech.client.console.bff.ext.rest;

import java.io.IOException;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import com.paytech.client.console.bff.core.ClientRefreshCookie;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.paytech.foundation.web.error.MappedProblem;
import com.paytech.foundation.web.error.MvcProblemExceptionMapper;
import com.paytech.foundation.web.error.ProblemResponseWriter;
import com.paytech.platform.support.api.enums.ClientAuthenticationErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/** 共享 Writer 会重置响应，认证端点须显式批准防缓存和失效 Cookie 头。 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice(assignableTypes = ClientAuthenticationController.class)
public class ClientProblemAdvice {
    private final MvcProblemExceptionMapper mapper;
    private final ProblemResponseWriter writer;

    public ClientProblemAdvice(MvcProblemExceptionMapper mapper, ProblemResponseWriter writer) {
        this.mapper = mapper;
        this.writer = writer;
    }

    @ExceptionHandler(Exception.class)
    public void handle(Exception failure, HttpServletRequest request, HttpServletResponse response) throws IOException {
        var mapped = mapper.map(failure, request);
        var headers = HttpHeaders.copyOf(mapped.headers());
        headers.setCacheControl("no-store");
        headers.setPragma("no-cache");
        headers.set("Referrer-Policy", "no-referrer");
        if (mapped.errorCode() == ClientAuthenticationErrorCode.CLIENT_SESSION_INVALID
            && "/client-auth/v1/refresh".equals(request.getServletPath())) {
            headers.set(HttpHeaders.SET_COOKIE, ClientRefreshCookie.encode("", 0));
        }
        writer.write(request, response,
            new MappedProblem(mapped.status(), mapped.errorCode(), mapped.detail(), mapped.errors(), headers));
    }
}
