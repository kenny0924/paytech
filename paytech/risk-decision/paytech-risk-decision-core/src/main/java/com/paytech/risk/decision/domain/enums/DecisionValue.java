package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum DecisionValue implements CodeEnum {
    ALLOW("ALLOW"),

    REVIEW("REVIEW"),

    DENY("DENY");

    private final String code;

    DecisionValue(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
