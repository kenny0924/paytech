package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Gateway Route Header 白名单用途。
 */
public enum GatewayRouteHeaderUse implements CodeEnum {
    CORS_REQUEST_ALLOWED("CORS_REQUEST_ALLOWED"),

    FORWARD_REQUEST("FORWARD_REQUEST");

    private final String code;

    GatewayRouteHeaderUse(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
