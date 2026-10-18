package com.paytech.foundation.httpclient.core.error;

import java.net.SocketTimeoutException;
import java.net.http.HttpConnectTimeoutException;
import java.net.http.HttpTimeoutException;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.common.enums.CodeMessageEnum;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions.DependencyFailureKind;
import com.paytech.foundation.httpclient.api.HttpServiceClientOptions.DependencyFailureMapper;

/**
 * Surrounds synchronous typed HTTP calls and converts only validated first-party failures.
 * It never logs remote response material or retries a request.
 *
 * @author Codex
 */
public final class HttpServiceClientInterceptor implements MethodInterceptor {

    private final Map<String, CodeMessageEnum> contractErrors;
    private final boolean legacyErrorResponseEnabled;
    private final boolean requestValidationFailuresEnabled;
    private final DependencyFailureMapper dependencyFailureMapper;
    private final HttpProblemDecoder problemDecoder = new HttpProblemDecoder();

    public HttpServiceClientInterceptor(CodeMessageEnum[] contractErrors, boolean legacyErrorResponseEnabled,
        DependencyFailureMapper dependencyFailureMapper) {
        this(contractErrors, legacyErrorResponseEnabled, dependencyFailureMapper, false);
    }

    /** 保留旧构造入口，字段校验失败必须由调用方显式接入。 */
    public HttpServiceClientInterceptor(CodeMessageEnum[] contractErrors, boolean legacyErrorResponseEnabled,
        DependencyFailureMapper dependencyFailureMapper, boolean requestValidationFailuresEnabled) {
        this.requestValidationFailuresEnabled = requestValidationFailuresEnabled;
        Objects.requireNonNull(contractErrors, "contractErrors must not be null");
        this.legacyErrorResponseEnabled = legacyErrorResponseEnabled;
        this.dependencyFailureMapper = Objects.requireNonNull(dependencyFailureMapper,
            "dependencyFailureMapper must not be null");
        Map<String, CodeMessageEnum> indexed = new HashMap<>();
        for (CodeMessageEnum error : contractErrors) {
            Objects.requireNonNull(error, "contract error must not be null");
            if (!hasText(error.getCode()) || !hasText(error.getMessage())
                || Arrays.stream(CommonErrorCode.values()).anyMatch(value -> value.getCode().equals(error.getCode()))
                || indexed.putIfAbsent(error.getCode(), error) != null) {
                throw new IllegalArgumentException("contract errors must have unique non-reserved codes and safe messages");
            }
        }
        this.contractErrors = Map.copyOf(indexed);
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        try {
            Object response = invocation.proceed();
            Class<?> returnType = invocation.getMethod().getReturnType();
            if (response == null && returnType != void.class && returnType != Void.class
                && AnnotatedElementUtils.hasAnnotation(invocation.getMethod(), HttpExchange.class)) {
                throw invalidResponse(null);
            }
            return response;
        } catch (RestClientResponseException failure) {
            throw classifyHttpFailure(failure, isHead(invocation), hasRequestBody(invocation));
        } catch (ResourceAccessException failure) {
            throw dependencyFailure(isDependencyTimeout(failure) ? DependencyFailureKind.TIMEOUT
                : DependencyFailureKind.UNAVAILABLE, failure);
        } catch (RestClientException failure) {
            throw invalidResponse(failure);
        }
    }

    private RuntimeException classifyHttpFailure(RestClientResponseException failure, boolean head, boolean requestBody) {
        if (head) {
            try {
                problemDecoder.validateHead(failure);
            } catch (IllegalArgumentException invalidWireResponse) {
                return invalidResponse(failure);
            }
            return failure.getStatusCode().is5xxServerError()
                ? dependencyFailure(dependencyFailureKind(failure), failure) : classifyHeadClientFailure(failure);
        }
        HttpProblemDecoder.DecodedProblem decoded;
        try {
            decoded = problemDecoder.read(failure, legacyErrorResponseEnabled);
        } catch (IllegalArgumentException invalidWireResponse) {
            return invalidResponse(failure);
        }
        if (failure.getStatusCode().is5xxServerError()) {
            return dependencyFailure(dependencyFailureKind(failure), failure);
        }
        if (!failure.getStatusCode().is4xxClientError()) {
            return invalidResponse(failure);
        }
        if (requestValidationFailuresEnabled && requestBody && decoded.strictProfile()
            && failure.getStatusCode().value() == 422
            && CommonErrorCode.VALIDATION_ERROR.getCode().equals(decoded.code())) {
            return new HttpRequestValidationException(decoded.fields(), failure);
        }
        return classifyClientCode(decoded.code(), failure);
    }

    private RuntimeException dependencyFailure(DependencyFailureKind kind, Throwable cause) {
        RuntimeException result = dependencyFailureMapper.map(kind, cause);
        if (result == null || result == cause || result.getCause() != cause) {
            throw invalidDependencyFailureMapperResult();
        }
        return result;
    }

    private static IllegalStateException invalidDependencyFailureMapperResult() {
        return new IllegalStateException("dependencyFailureMapper returned an invalid exception");
    }

    private static DependencyFailureKind dependencyFailureKind(RestClientResponseException failure) {
        return failure.getStatusCode().value() == 504 ? DependencyFailureKind.TIMEOUT
            : DependencyFailureKind.UNAVAILABLE;
    }

    private static boolean isDependencyTimeout(ResourceAccessException failure) {
        Set<Throwable> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        boolean timeoutFound = false;
        for (Throwable current = failure; current != null && seen.add(current); current = current.getCause()) {
            if (current instanceof HttpConnectTimeoutException) {
                return false;
            }
            if (current instanceof HttpTimeoutException || current instanceof SocketTimeoutException) {
                timeoutFound = true;
            }
        }
        return timeoutFound;
    }

    private RuntimeException classifyHeadClientFailure(RestClientResponseException failure) {
        int status = failure.getStatusCode().value();
        if (status == 400) {
            return new InvalidParameterException(CommonErrorCode.INVALID_REQUEST.getMessage(), failure);
        }
        if (status == 409) {
            return new ConcurrentRequestException(CommonErrorCode.CONCURRENT_REQUEST.getMessage(), failure);
        }
        return new BizCheckFailException(CommonErrorCode.BUSINESS_CHECK_FAILED.getMessage(), failure);
    }

    private RuntimeException classifyClientCode(String code, RestClientResponseException failure) {
        if (CommonErrorCode.BUSINESS_CHECK_FAILED.getCode().equals(code)) {
            return new BizCheckFailException(CommonErrorCode.BUSINESS_CHECK_FAILED.getMessage(), failure);
        }
        if (CommonErrorCode.INVALID_REQUEST.getCode().equals(code)) {
            return new InvalidParameterException(CommonErrorCode.INVALID_REQUEST.getMessage(), failure);
        }
        if (CommonErrorCode.CONCURRENT_REQUEST.getCode().equals(code)) {
            return new ConcurrentRequestException(CommonErrorCode.CONCURRENT_REQUEST.getMessage(), failure);
        }
        if (CommonErrorCode.NOT_FOUND.getCode().equals(code) && failure.getStatusCode().value() == 404) {
            return new FailException(CommonErrorCode.NOT_FOUND, null, failure);
        }
        CodeMessageEnum contractError = contractErrors.get(code);
        return contractError == null ? invalidResponse(failure) : new FailException(contractError, null, failure);
    }

    /** 公共字段错误只对应显式 Body；GET/DELETE 等无 Body 请求保持协议错误处理。 */
    private static boolean hasRequestBody(MethodInvocation invocation) {
        return Arrays.stream(invocation.getMethod().getParameters())
            .anyMatch(parameter -> parameter.isAnnotationPresent(org.springframework.web.bind.annotation.RequestBody.class));
    }

    private static boolean isHead(MethodInvocation invocation) {
        HttpExchange methodExchange = AnnotatedElementUtils.findMergedAnnotation(invocation.getMethod(), HttpExchange.class);
        if (methodExchange != null && "HEAD".equalsIgnoreCase(methodExchange.method())) {
            return true;
        }
        HttpExchange typeExchange = AnnotatedElementUtils.findMergedAnnotation(
            invocation.getMethod().getDeclaringClass(), HttpExchange.class);
        return typeExchange != null && "HEAD".equalsIgnoreCase(typeExchange.method());
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static ErrorException invalidResponse(Throwable cause) {
        return new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, null, cause);
    }
}
