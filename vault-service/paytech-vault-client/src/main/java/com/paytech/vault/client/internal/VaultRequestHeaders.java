package com.paytech.vault.client.internal;

import java.util.Set;

import org.springframework.core.MethodParameter;
import org.springframework.http.HttpRequest;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.invoker.HttpRequestValues;
import org.springframework.web.service.invoker.HttpServiceArgumentResolver;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.vault.client.VaultClientErrorCode;

import lombok.AllArgsConstructor;

/** 将原始代理参数保留为不可打印的请求属性，在发送前恢复协议专属 Header。 */
@AllArgsConstructor
public final class VaultRequestHeaders implements HttpServiceArgumentResolver {
    private static final String ATTRIBUTE_PREFIX = VaultRequestHeaders.class.getName() + ".";
    private static final Set<String> OWNED_HEADERS = Set.of("X-Caller-Service", "X-Request-Id", "Idempotency-Key");
    private final String caller;

    @Override
    public boolean resolve(Object argument, MethodParameter parameter, HttpRequestValues.Builder requestValues) {
        RequestHeader annotation = parameter.getParameterAnnotation(RequestHeader.class);
        if (annotation == null || !OWNED_HEADERS.contains(annotation.value())) return false;
        if (!(argument instanceof String value)) throw new ErrorException(VaultClientErrorCode.INTERNAL_ERROR);
        requestValues.addAttribute(ATTRIBUTE_PREFIX + annotation.value(), new HeaderValue(value));
        requestValues.addHeader(annotation.value(), value);
        return true;
    }

    void enforce(HttpRequest request) {
        var headers = request.getHeaders();
        headers.set("X-Caller-Service", caller);
        headers.set("X-Request-Id", originalValue(request, "X-Request-Id"));
        headers.remove("Idempotency-Key");
        if (request.getURI().getPath().equals("/vault/v1/records/protect")) {
            headers.set("Idempotency-Key", originalValue(request, "Idempotency-Key"));
        }
    }

    private String originalValue(HttpRequest request, String name) {
        Object attribute = request.getAttributes().get(ATTRIBUTE_PREFIX + name);
        if (!(attribute instanceof HeaderValue original)) throw new ErrorException(VaultClientErrorCode.INTERNAL_ERROR);
        return original.value();
    }

    private record HeaderValue(String value) {
        @Override public String toString() { return "HeaderValue[redacted]"; }
    }
}
