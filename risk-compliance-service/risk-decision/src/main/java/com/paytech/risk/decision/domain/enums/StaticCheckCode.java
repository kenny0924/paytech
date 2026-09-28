package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum StaticCheckCode implements CodeEnum {
    REFERENCE_INTEGRITY("REFERENCE_INTEGRITY"),

    AST_SHAPE("AST_SHAPE"),

    AST_TYPES("AST_TYPES"),

    FEATURE_DEPENDENCY("FEATURE_DEPENDENCY"),

    HANDLER_HASH("HANDLER_HASH"),

    ACTION_PARAMETERS("ACTION_PARAMETERS"),

    TERMINAL_ACTION("TERMINAL_ACTION"),

    SYSTEM_OBLIGATION("SYSTEM_OBLIGATION");

    private final String code;

    StaticCheckCode(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
