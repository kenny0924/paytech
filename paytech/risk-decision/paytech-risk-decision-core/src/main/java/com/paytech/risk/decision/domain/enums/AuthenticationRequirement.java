package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 支付放行时的认证要求。 */
public enum AuthenticationRequirement implements CodeEnum {
    /** 无需额外认证。 */
    NOT_REQUIRED("NOT_REQUIRED"),

    /** 要求 3DS 认证。 */
    THREE_DS_REQUIRED("THREE_DS_REQUIRED"),

    /** 要求挑战认证；一期不产生。 */
    CHALLENGE_REQUIRED("CHALLENGE_REQUIRED");

    private final String code;

    AuthenticationRequirement(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
