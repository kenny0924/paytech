package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum GroupOperator implements CodeEnum {
    AND("AND"),

    OR("OR"),

    NOT("NOT");

    private final String code;

    GroupOperator(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
