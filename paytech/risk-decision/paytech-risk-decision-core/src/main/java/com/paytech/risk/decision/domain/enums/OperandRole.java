package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum OperandRole implements CodeEnum {
    LEFT("LEFT"),

    RIGHT("RIGHT");

    private final String code;

    OperandRole(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
