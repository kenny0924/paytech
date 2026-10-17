package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Gateway Guard key dimension code published by Platform Support.
 */
public enum GatewayGuardDimension implements CodeEnum {
    SHARED_ROUTE("SHARED_ROUTE"),

    CLIENT_IP("CLIENT_IP"),

    AUTHENTICATED_PRINCIPAL("AUTHENTICATED_PRINCIPAL"),

    TENANT("TENANT");

    private final String code;

    GatewayGuardDimension(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
