package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum DecisionEnvironment implements CodeEnum {
    TEST("TEST"),

    PRODUCTION("PRODUCTION");

    private final String code;

    DecisionEnvironment(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
