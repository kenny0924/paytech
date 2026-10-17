package com.paytech.gateway.routing;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpMethod;

import com.paytech.gateway.guard.policy.GuardProfile;

/** 一个固定 Operations Method、外部路径与 BFF 路径的完整公开路由契约。 */
public record OperationsRouteDefinition(OperationsRouteCode code, HttpMethod method, String externalPath,
    String bffPath, OperationsQueryPolicy queryPolicy, OperationsAccessRequirement accessRequirement,
    RefreshCookieRequirement refreshCookieRequirement, RefreshCookieResponsePolicy refreshCookieResponsePolicy,
    Set<String> corsRequestHeaders, Set<String> forwardedRequestHeaders, int bodyLimitBytes,
    Duration bodyReceiveTimeout, GuardProfile ingressGuard, GuardProfile authenticatedGuard, Duration responseTimeout) {

    public OperationsRouteDefinition {
        Objects.requireNonNull(code, "Operations 路由编码不可为空");
        Objects.requireNonNull(method, "Operations 路由 HTTP Method 不可为空");
        validatePath(externalPath, "Operations 外部路径");
        validatePath(bffPath, "Operations BFF 路径");
        Objects.requireNonNull(queryPolicy, "Operations Query 策略不可为空");
        Objects.requireNonNull(accessRequirement, "Operations 身份要求不可为空");
        Objects.requireNonNull(refreshCookieRequirement, "Refresh Cookie 要求不可为空");
        Objects.requireNonNull(refreshCookieResponsePolicy, "Refresh Cookie 响应策略不可为空");
        corsRequestHeaders = immutableHeaders(corsRequestHeaders, "CORS 请求 Header 集合不可为空");
        forwardedRequestHeaders = immutableHeaders(forwardedRequestHeaders, "转发请求 Header 集合不可为空");
        if (bodyLimitBytes <= 0) {
            throw new IllegalArgumentException("请求体上限必须为正");
        }
        validateDuration(bodyReceiveTimeout, "请求体接收超时必须为正");
        Objects.requireNonNull(ingressGuard, "入口 Guard Profile 不可为空");
        validateAuthenticatedGuard(accessRequirement, authenticatedGuard);
        validateDuration(responseTimeout, "上游响应超时必须为正");
    }

    private static Set<String> immutableHeaders(Set<String> headers, String nullMessage) {
        Set<String> values = Set.copyOf(Objects.requireNonNull(headers, nullMessage));
        if (values.stream().anyMatch(value -> value == null || value.isBlank())) {
            throw new IllegalArgumentException("请求 Header 名称不可为空");
        }
        return values;
    }

    private static void validatePath(String value, String field) {
        if (value == null || value.isBlank() || !value.startsWith("/") || value.contains("?") || value.contains("#")
            || value.contains("//")) {
            throw new IllegalArgumentException(field + "必须是不带 Query 或 Fragment 的绝对路径");
        }
    }

    private static void validateAuthenticatedGuard(OperationsAccessRequirement accessRequirement,
        GuardProfile authenticatedGuard) {
        if (accessRequirement == OperationsAccessRequirement.AUTHENTICATED && authenticatedGuard == null) {
            throw new IllegalArgumentException("已认证 Operations 路由必须声明 Subject Guard Profile");
        }
        if (accessRequirement == OperationsAccessRequirement.ANONYMOUS && authenticatedGuard != null) {
            throw new IllegalArgumentException("匿名 Operations 路由不可声明 Subject Guard Profile");
        }
    }

    private static void validateDuration(Duration value, String message) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(message);
        }
    }

    /** 迁移期兼容旧 Guard 解析器；后续任务将直接使用 accessRequirement。 */
    public boolean accessTokenRequired() {
        return accessRequirement == OperationsAccessRequirement.AUTHENTICATED;
    }
}
