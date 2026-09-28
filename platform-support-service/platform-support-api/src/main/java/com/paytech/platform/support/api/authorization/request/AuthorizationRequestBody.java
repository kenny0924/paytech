package com.paytech.platform.support.api.authorization.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;

/** Operations 当前路由授权请求。 */
public final class AuthorizationRequestBody {
    /** Gateway 可信上下文中的路由代码，由 BFF 提供，不能接受浏览器指定的授权目标。 */
    @NotBlank
    private final String routeCode;

    /** 绑定路由字段，格式约束由 HTTP 入口校验。 */
    @JsonCreator
    public AuthorizationRequestBody(@JsonProperty("routeCode") String routeCode) {
        this.routeCode = routeCode;
    }

    /** 返回待授权的可信路由代码。 */
    public String getRouteCode() {
        return routeCode;
    }
}
