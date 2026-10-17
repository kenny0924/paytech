package com.paytech.gateway.routing;

import com.paytech.foundation.common.enums.CodeEnum;

/** 已批准的 Operations 认证路由稳定编码。 */
public enum OperationsRouteCode implements CodeEnum {
    AUTH_LOGIN("operations-auth-login"), AUTH_REFRESH("operations-auth-refresh"), AUTH_LOGOUT("operations-auth-logout");

    private final String code;

    OperationsRouteCode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
