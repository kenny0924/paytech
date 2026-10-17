package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum StaticCheckStatus implements CodeEnum {
    PASSED("PASSED"),

    FAILED("FAILED");

    private final String code;

    StaticCheckStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
