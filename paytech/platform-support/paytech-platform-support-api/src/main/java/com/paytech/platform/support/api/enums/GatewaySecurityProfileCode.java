package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Precompiled Gateway security profile selector code published by Platform Support.
 */
public enum GatewaySecurityProfileCode implements CodeEnum {
    OPERATIONS_JWT("OPERATIONS_JWT"),

    CLIENT_JWT("CLIENT_JWT"),

    PARTNER_JWT("PARTNER_JWT"),

    CLIENT_API_CREDENTIAL("CLIENT_API_CREDENTIAL"),

    CHECKOUT_SESSION("CHECKOUT_SESSION"),

    CHANNEL_CALLBACK("CHANNEL_CALLBACK"),

    PUBLIC("PUBLIC");

    private final String code;

    GatewaySecurityProfileCode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
