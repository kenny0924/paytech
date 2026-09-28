package com.paytech.gateway.web;

import com.paytech.foundation.common.enums.CodeMessageEnum;

/** Gateway 可安全外显的稳定错误码。 */
public enum GatewayErrorCode implements CodeMessageEnum {
    GATEWAY_UNAUTHORIZED("GATEWAY_UNAUTHORIZED", "未通过身份验证"),

    GATEWAY_FORBIDDEN("GATEWAY_FORBIDDEN", "请求不符合渠道安全策略"),

    INSUFFICIENT_SCOPE("INSUFFICIENT_SCOPE", "凭据不具备此接口所需权限"),

    GATEWAY_ROUTE_NOT_FOUND("GATEWAY_ROUTE_NOT_FOUND", "请求的网关路由不存在"),

    GATEWAY_INVALID_REQUEST("GATEWAY_INVALID_REQUEST", "请求参数不符合网关路由契约"),

    GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE("GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE", "请求头字段超过允许大小"),

    GATEWAY_PAYLOAD_TOO_LARGE("GATEWAY_PAYLOAD_TOO_LARGE", "请求内容超过允许大小"),

    GATEWAY_REQUEST_TIMEOUT("GATEWAY_REQUEST_TIMEOUT", "请求内容接收超时"),

    GATEWAY_RATE_LIMITED("GATEWAY_RATE_LIMITED", "请求过于频繁"),

    GATEWAY_CONCURRENCY_LIMITED("GATEWAY_CONCURRENCY_LIMITED", "当前并发请求过多"),

    GATEWAY_DEPENDENCY_UNAVAILABLE("GATEWAY_DEPENDENCY_UNAVAILABLE", "网关依赖暂时不可用"),

    UPSTREAM_PROTOCOL_ERROR("UPSTREAM_PROTOCOL_ERROR", "上游服务响应不符合安全契约"),

    GATEWAY_UPSTREAM_TIMEOUT("GATEWAY_UPSTREAM_TIMEOUT", "上游服务响应超时");

    private final String code;
    private final String message;

    GatewayErrorCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override
    public String getCode() {
        return code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    /** Client API 公开协议码不暴露 Gateway 实现前缀；Operations 保持原合同。 */
    public String clientApiCode() {
        return switch (this) {
            case GATEWAY_UNAUTHORIZED -> "UNAUTHENTICATED";
            case GATEWAY_FORBIDDEN -> "FORBIDDEN";
            case GATEWAY_ROUTE_NOT_FOUND -> "NOT_FOUND";
            case GATEWAY_INVALID_REQUEST -> "INVALID_REQUEST";
            case GATEWAY_REQUEST_HEADER_FIELDS_TOO_LARGE -> "REQUEST_HEADERS_TOO_LARGE";
            case GATEWAY_PAYLOAD_TOO_LARGE -> "REQUEST_TOO_LARGE";
            case GATEWAY_REQUEST_TIMEOUT -> "REQUEST_TIMEOUT";
            case GATEWAY_RATE_LIMITED, GATEWAY_CONCURRENCY_LIMITED -> "RATE_LIMIT_EXCEEDED";
            case GATEWAY_DEPENDENCY_UNAVAILABLE -> "DEPENDENCY_UNAVAILABLE";
            case GATEWAY_UPSTREAM_TIMEOUT -> "DEPENDENCY_TIMEOUT";
            case UPSTREAM_PROTOCOL_ERROR, INSUFFICIENT_SCOPE -> code;
        };
    }
}
