package com.paytech.platform.support.accesscontrol.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * JWT 签名算法。
 */
public enum JwtSigningAlgorithm implements CodeEnum {
    RS256("RS256");

    private final String code;

    JwtSigningAlgorithm(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
