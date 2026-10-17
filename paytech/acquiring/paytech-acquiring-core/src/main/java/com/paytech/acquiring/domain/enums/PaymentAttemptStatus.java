package com.paytech.acquiring.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Commerce 本地 Payment Attempt 投影的闭合状态。 */
public enum PaymentAttemptStatus implements CodeEnum {
    CREATED("CREATED"),

    RISK_CHECKING("RISK_CHECKING"),

    RISK_REJECTED("RISK_REJECTED"),

    RISK_FAILED("RISK_FAILED"),

    LIMIT_REJECTED("LIMIT_REJECTED"),

    ROUTING("ROUTING"),

    PROCESSING("PROCESSING"),

    REQUIRES_ACTION("REQUIRES_ACTION"),

    UNKNOWN("UNKNOWN"),

    SUCCEEDED("SUCCEEDED"),

    FAILED("FAILED"),

    SUCCESS_CONFLICT("SUCCESS_CONFLICT");

    private final String code;

    PaymentAttemptStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
