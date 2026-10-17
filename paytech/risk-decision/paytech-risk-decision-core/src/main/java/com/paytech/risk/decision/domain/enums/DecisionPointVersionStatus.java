package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum DecisionPointVersionStatus implements CodeEnum {
    DRAFT("DRAFT"),

    FROZEN("FROZEN"),

    ACTIVE("ACTIVE"),

    RETIRED("RETIRED");

    private final String code;

    DecisionPointVersionStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
