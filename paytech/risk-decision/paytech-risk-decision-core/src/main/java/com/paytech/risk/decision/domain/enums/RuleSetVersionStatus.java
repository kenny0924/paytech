package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum RuleSetVersionStatus implements CodeEnum {
    DRAFT("DRAFT"),

    FROZEN("FROZEN"),

    RETIRED("RETIRED");

    private final String code;

    RuleSetVersionStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
