package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Gateway request idempotency mode code published by Platform Support.
 */
public enum GatewayIdempotencyMode implements CodeEnum {
    NONE("NONE"),

    REQUIRED("REQUIRED");

    private final String code;

    GatewayIdempotencyMode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
