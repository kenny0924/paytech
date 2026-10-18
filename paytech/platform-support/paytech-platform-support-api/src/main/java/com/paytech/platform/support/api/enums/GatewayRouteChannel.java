package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Public Gateway route channel code published by Platform Support.
 */
public enum GatewayRouteChannel implements CodeEnum {
    OPERATIONS("OPERATIONS"),

    CLIENT("CLIENT"),

    PARTNER("PARTNER"),

    CHECKOUT("CHECKOUT"),

    CLIENT_API("CLIENT_API"),

    CHANNEL_CALLBACK("CHANNEL_CALLBACK");

    private final String code;

    GatewayRouteChannel(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
