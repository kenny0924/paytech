package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum EvaluationStrategy implements CodeEnum {
    FIRST_TERMINAL("FIRST_TERMINAL");

    private final String code;

    EvaluationStrategy(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
