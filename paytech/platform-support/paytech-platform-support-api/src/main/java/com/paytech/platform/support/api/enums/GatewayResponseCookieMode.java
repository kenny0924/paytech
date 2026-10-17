package com.paytech.platform.support.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * Gateway response Cookie handling mode code published by Platform Support.
 */
public enum GatewayResponseCookieMode implements CodeEnum {
    NONE("NONE"),

    ISSUE_ONLY("ISSUE_ONLY"),

    ISSUE_OR_CLEAR("ISSUE_OR_CLEAR"),

    CLEAR_ONLY("CLEAR_ONLY");

    private final String code;

    GatewayResponseCookieMode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
