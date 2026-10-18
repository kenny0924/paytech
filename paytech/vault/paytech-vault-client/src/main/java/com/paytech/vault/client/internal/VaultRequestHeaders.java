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

/**
 * 将原始代理参数保留为不可打印的请求属性，在发送前恢复协议专属 Header。
 * <li>始终发送 {@code X-Caller-Service}；存在 {@link VaultInternalToken} 时另附 {@code Authorization: Bearer}，
 * 由 Vault 按 {@code VAULT_INTERNAL_AUTH_MODE} 决定采用哪一个。</li>
 * <li>宿主或回调设置的 {@code Authorization} 一律移除，只发送本组件取得的 Token。</li>
 */
@AllArgsConstructor
public final class VaultRequestHeaders implements HttpServiceArgumentResolver {
    private static final String ATTRIBUTE_PREFIX = VaultRequestHeaders.class.getName() + ".";
    private static final Set<String> OWNED_HEADERS = Set.of("X-Caller-Service", "X-Request-Id", "Idempotency-Key",
            "X-Operator-Id");
    /** 管理接口路径前缀；只有这些请求携带操作人。 */
    static final String ADMIN_PATH_PREFIX = "/vault/v1/admin/";
    /** 需要幂等键的写路径：服务加密与人工加密。 */
    private static final Set<String> IDEMPOTENT_PATHS = Set.of("/vault/v1/records/protect", "/vault/v1/admin/manual/protect");
    private static final String AUTHORIZATION = "Authorization";
    private final String caller;
    /** Token {@code aud}：Vault 服务发现名。 */
    private final String targetService;
    /** 可选；为 null 时行为与引入内部 Token 前一致。 */
    private final VaultInternalToken internalToken;

    public VaultRequestHeaders(String caller) {
        this(caller, null, null);
    }

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
        headers.remove("X-Operator-Id");
        String path = request.getURI().getPath();
        if (IDEMPOTENT_PATHS.contains(path)) {
            headers.set("Idempotency-Key", originalValue(request, "Idempotency-Key"));
        }
        if (path.startsWith(ADMIN_PATH_PREFIX)) {
            headers.set("X-Operator-Id", originalValue(request, "X-Operator-Id"));
        }
        headers.remove(AUTHORIZATION);
        String token = internalToken(headers.getFirst("X-Request-Id"));
        if (token != null) {
            headers.set(AUTHORIZATION, "Bearer " + token);
        }
    }

    /** 取 Token；来源存在但失败时失败关闭为依赖不可用，不发送请求、不泄漏底层异常。 */
    private String internalToken(String requestId) {
        if (internalToken == null) return null;
        try {
            return internalToken.token(targetService);
        } catch (RuntimeException unavailable) {
            throw VaultResponseErrorHandler.unavailable(requestId);
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
