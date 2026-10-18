package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Mutually exclusive Gateway authorization mode code published by Platform Support.
 */
public enum GatewayAuthorizationMode implements CodeEnum {
    PUBLIC("PUBLIC"),

    HUMAN_AUTHENTICATED("HUMAN_AUTHENTICATED"),

    HUMAN_PERMISSION("HUMAN_PERMISSION"),

    API_SCOPE("API_SCOPE"),

    CHECKOUT_RESOURCE("CHECKOUT_RESOURCE"),

    CHANNEL_CALLBACK("CHANNEL_CALLBACK");

    private final String code;

    GatewayAuthorizationMode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
