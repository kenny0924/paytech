package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Gateway Guard Rule type code published by Platform Support.
 */
public enum GatewayGuardRuleType implements CodeEnum {
    RATE_LIMIT("RATE_LIMIT"),

    CONCURRENCY("CONCURRENCY");

    private final String code;

    GatewayGuardRuleType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
