package com.paytech.gateway.routing.runtime;

import java.time.Duration;
import java.util.Objects;
import java.util.Set;

import org.springframework.http.HttpMethod;

import com.paytech.gateway.guard.policy.GuardProfile;
import com.paytech.platform.support.api.enums.GatewayRouteChannel;

/** 已经由静态安全策略完整校验的单条公共运行时路由。 */
public record GatewayRuntimeRoute(String routeCode, GatewayRouteChannel channel, HttpMethod method, String externalPath,
    String downstreamPath, GatewayRuntimeTarget target, String securityProfileCode, String authorizationMode,
    String permissionCode, String requiredScopeCode, String requestCookieMode, String responseCookieMode,
    Set<String> corsRequestHeaders, Set<String> forwardedRequestHeaders, Set<String> queryParameterNames,
    long bodyLimitBytes, Duration bodyReceiveTimeout, Duration responseTimeout, GuardProfile ingressGuard,
    GuardProfile authenticatedGuard) {

    /** 固定同渠道目标及不可变策略集合，避免请求期间受快照更新影响。 */
    public GatewayRuntimeRoute {
        routeCode = required(routeCode, "Route Code 不可为空");
        channel = Objects.requireNonNull(channel, "Route Channel 不可为空");
        method = Objects.requireNonNull(method, "HTTP Method 不可为空");
        externalPath = path(externalPath, "外部路径不可为空");
        downstreamPath = path(downstreamPath, "下游路径不可为空");
        target = Objects.requireNonNull(target, "运行时 Target 不可为空");
        if (target.channel() != channel) {
            throw new IllegalArgumentException("运行时 Route 与 Target 渠道必须一致");
        }
        securityProfileCode = required(securityProfileCode, "Security Profile 不可为空");
        authorizationMode = required(authorizationMode, "Authorization Mode 不可为空");
        requestCookieMode = required(requestCookieMode, "请求 Cookie 模式不可为空");
        responseCookieMode = required(responseCookieMode, "响应 Cookie 模式不可为空");
        corsRequestHeaders = Set.copyOf(corsRequestHeaders);
        forwardedRequestHeaders = Set.copyOf(forwardedRequestHeaders);
        queryParameterNames = Set.copyOf(queryParameterNames);
        if (bodyLimitBytes < 0) {
            throw new IllegalArgumentException("请求体上限不可为负数");
        }
        bodyReceiveTimeout = positive(bodyReceiveTimeout, "请求体接收超时必须为正");
        responseTimeout = positive(responseTimeout, "上游响应超时必须为正");
    }

    /** 返回同一快照内绑定目标的逻辑标识，供现有日志及响应策略使用。 */
    public String targetServiceCode() {
        return target.targetServiceCode();
    }

    private static String required(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static String path(String value, String message) {
        if (value == null || !value.startsWith("/")) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }

    private static Duration positive(Duration value, String message) {
        if (value == null || value.isZero() || value.isNegative()) {
            throw new IllegalArgumentException(message);
        }
        return value;
    }
}
