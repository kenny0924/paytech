package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 公共 Route 当前支持的精确 HTTP Method。
 */
public enum GatewayHttpMethod implements CodeEnum {
    GET("GET"),

    POST("POST"),

    PUT("PUT"),

    PATCH("PATCH"),

    DELETE("DELETE"),

    HEAD("HEAD");

    private final String code;

    GatewayHttpMethod(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
