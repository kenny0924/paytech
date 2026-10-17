package com.paytech.operations.console.bff.ext.rest;

import java.util.Collections;
import java.util.regex.Pattern;

import org.springframework.core.MethodParameter;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

import com.paytech.foundation.common.error.FailException;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.enums.OperationsContextType;
import com.paytech.platform.support.api.enums.OperationsErrorCode;

import jakarta.servlet.http.HttpServletRequest;

/** 解析 Gateway 可信 Header；Controller 参数只接受入站边界已授权的请求上下文。 @author Codex */
@Component
public final class OperatorContextArgumentResolver implements HandlerMethodArgumentResolver {
    static final String CONTEXT_ATTRIBUTE = OperatorContext.class.getName();
    private static final String REALM_HEADER = "X-Gateway-Realm";
    private static final String CHANNEL_HEADER = "X-Gateway-Channel";
    private static final String ACTOR_TYPE_HEADER = "X-Gateway-Actor-Type";
    private static final String SUBJECT_HEADER = "X-Gateway-Actor-Subject";
    private static final String SESSION_HEADER = "X-Gateway-Session-Id";
    private static final String ROUTE_HEADER = "X-Gateway-Route-Code";
    private static final String CONTEXT_TYPE_HEADER = "X-Gateway-Context-Type";
    private static final String TENANT_TYPE_HEADER = "X-Gateway-Tenant-Type";
    private static final String TENANT_ID_HEADER = "X-Gateway-Tenant-Id";
    private static final String MEMBER_ID_HEADER = "X-Gateway-Tenant-Member-Id";
    private static final Pattern HEADER_VALUE = Pattern.compile("[A-Za-z0-9_-]{1,128}");

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.getParameterType() == OperatorContext.class;
    }

    @Override
    public OperatorContext resolveArgument(MethodParameter parameter, ModelAndViewContainer container,
        NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
        if (request.getAttribute(CONTEXT_ATTRIBUTE) instanceof OperatorContext context) {
            return context;
        }
        throw new FailException(OperationsErrorCode.OPERATIONS_SESSION_INVALID);
    }

    OperatorContext resolveContext(HttpServletRequest request) {
        requireEqual(request, REALM_HEADER, "OPERATIONS");
        requireEqual(request, CHANNEL_HEADER, "OPERATIONS");
        requireEqual(request, ACTOR_TYPE_HEADER, "PLATFORM_USER");
        validateTenantContext(request);
        return new OperatorContext(requiredHeader(request, SUBJECT_HEADER), requiredHeader(request, SESSION_HEADER),
            requiredHeader(request, ROUTE_HEADER));
    }

    private void validateTenantContext(HttpServletRequest request) {
        String contextType = requiredHeader(request, CONTEXT_TYPE_HEADER);
        if (OperationsContextType.TENANT.getCode().equals(contextType)) {
            requireEqual(request, TENANT_TYPE_HEADER, "INTERNAL");
            requiredHeader(request, TENANT_ID_HEADER);
            requiredHeader(request, MEMBER_ID_HEADER);
            return;
        }
        if (!OperationsContextType.BOOTSTRAP.getCode().equals(contextType)
            || request.getHeader(TENANT_ID_HEADER) != null || request.getHeader(TENANT_TYPE_HEADER) != null
            || request.getHeader(MEMBER_ID_HEADER) != null) {
            throw new FailException(OperationsErrorCode.OPERATIONS_SESSION_INVALID);
        }
    }

    private String requiredHeader(HttpServletRequest request, String name) {
        var values = Collections.list(request.getHeaders(name));
        if (values.size() != 1 || !HEADER_VALUE.matcher(values.getFirst()).matches()) {
            throw new FailException(OperationsErrorCode.OPERATIONS_SESSION_INVALID);
        }
        return values.getFirst();
    }

    private void requireEqual(HttpServletRequest request, String name, String expected) {
        if (!expected.equals(requiredHeader(request, name))) {
            throw new FailException(OperationsErrorCode.OPERATIONS_SESSION_INVALID);
        }
    }
}
