package com.paytech.operations.console.bff.ext.rest;

import java.util.Collections;

import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

import com.paytech.foundation.common.error.FailException;
import com.paytech.operations.console.bff.configuration.OperationsBrowserProperties;
import com.paytech.operations.console.bff.domain.enums.OperationsBffErrorCode;
import com.paytech.operations.console.bff.integration.PlatformOperationsClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.AllArgsConstructor;

/** 统一 HTTP 协议与实时授权边界；默认要求 PLT-I02 通过，只豁免登录和刷新。 @author Codex */
@Component
@AllArgsConstructor
public final class OperationsRequestInterceptor implements HandlerInterceptor {
    private final PlatformOperationsClient client;
    private final OperationsBrowserProperties properties;
    private final OperatorContextArgumentResolver contextResolver;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-store");
        response.setHeader(HttpHeaders.PRAGMA, "no-cache");
        if (!(handler instanceof HandlerMethod)) {
            return true;
        }
        if (isPublicAuthenticationRequest(request)) {
            validateOrigin(request);
            validateContentType(request.getContentType());
            return true;
        }
        OperatorContext context = contextResolver.resolveContext(request);
        client.authorize(context);
        request.setAttribute(OperatorContextArgumentResolver.CONTEXT_ATTRIBUTE, context);
        validateOrigin(request);
        // 无 RequestBody 的 DELETE 合同不要求虚构 JSON；Origin 和实时授权仍执行。
        boolean bodylessDelete = "DELETE".equals(request.getMethod())
            && java.util.Arrays.stream(((HandlerMethod) handler).getMethodParameters())
                .noneMatch(parameter -> parameter.hasParameterAnnotation(org.springframework.web.bind.annotation.RequestBody.class));
        if (!isReadMethod(request) && !bodylessDelete) {
            validateContentType(request.getContentType());
        }
        return true;
    }

    private boolean isPublicAuthenticationRequest(HttpServletRequest request) {
        return "POST".equals(request.getMethod())
            && (OperationsAuthenticationPaths.LOGIN.equals(request.getRequestURI())
                || OperationsAuthenticationPaths.REFRESH.equals(request.getRequestURI()));
    }

    private void validateOrigin(HttpServletRequest request) {
        var origins = Collections.list(request.getHeaders(HttpHeaders.ORIGIN));
        if (isReadMethod(request) && origins.isEmpty()) {
            return;
        }
        if (origins.size() != 1 || !properties.consoleOrigin().equals(origins.getFirst())) {
            throw new FailException(OperationsBffErrorCode.ORIGIN_REJECTED);
        }
    }

    private void validateContentType(String contentType) {
        if (contentType == null) {
            throw new FailException(OperationsBffErrorCode.UNSUPPORTED_MEDIA_TYPE);
        }
        try {
            if (!MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) {
                throw new FailException(OperationsBffErrorCode.UNSUPPORTED_MEDIA_TYPE);
            }
        } catch (InvalidMediaTypeException exception) {
            throw new FailException(OperationsBffErrorCode.UNSUPPORTED_MEDIA_TYPE, null, exception);
        }
    }

    private boolean isReadMethod(HttpServletRequest request) {
        return "GET".equals(request.getMethod()) || "HEAD".equals(request.getMethod());
    }

}
