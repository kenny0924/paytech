package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum DecisionPointStatus implements CodeEnum {
    ACTIVE("ACTIVE"),

    INACTIVE("INACTIVE");

    private final String code;

    DecisionPointStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
