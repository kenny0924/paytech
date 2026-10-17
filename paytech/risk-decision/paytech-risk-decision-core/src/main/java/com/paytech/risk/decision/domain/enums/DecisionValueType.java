package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum DecisionValueType implements CodeEnum {
    STRING("STRING"),

    NUMBER("NUMBER"),

    BOOLEAN("BOOLEAN"),

    DATETIME("DATETIME"),

    DURATION("DURATION");

    private final String code;

    DecisionValueType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
