package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum ComparisonOperator implements CodeEnum {
    EQ("EQ"),

    NE("NE"),

    GT("GT"),

    GTE("GTE"),

    LT("LT"),

    LTE("LTE"),

    IN("IN"),

    NOT_IN("NOT_IN"),

    BETWEEN("BETWEEN"),

    IS_NULL("IS_NULL"),

    IS_NOT_NULL("IS_NOT_NULL");

    private final String code;

    ComparisonOperator(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
