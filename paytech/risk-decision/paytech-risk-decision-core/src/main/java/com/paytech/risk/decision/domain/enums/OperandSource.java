package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum OperandSource implements CodeEnum {
    FEATURE("FEATURE"),

    CONTEXT("CONTEXT"),

    LITERAL("LITERAL"),

    VARIABLE("VARIABLE");

    private final String code;

    OperandSource(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
