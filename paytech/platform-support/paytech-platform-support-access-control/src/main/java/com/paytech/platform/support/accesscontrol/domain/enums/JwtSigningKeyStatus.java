package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * JWT 签名密钥生命周期状态。
 */
public enum JwtSigningKeyStatus implements CodeEnum {
    PREPARED("PREPARED"),

    ACTIVE("ACTIVE"),

    RETIRING("RETIRING"),

    RETIRED("RETIRED");

    private final String code;

    JwtSigningKeyStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
