package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Gateway request Cookie handling mode code published by Platform Support.
 */
public enum GatewayRequestCookieMode implements CodeEnum {
    DROP("DROP"),

    REQUIRE_PROFILE_COOKIE("REQUIRE_PROFILE_COOKIE");

    private final String code;

    GatewayRequestCookieMode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
