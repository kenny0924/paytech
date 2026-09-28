package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 人工认证域。
 */
public enum AuthenticationRealm implements CodeEnum {
    OPERATIONS("OPERATIONS"),

    CUSTOMER("CUSTOMER");

    private final String code;

    AuthenticationRealm(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
