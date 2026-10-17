package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum DecisionObligationType implements CodeEnum {
    ACTION("ACTION"),

    RESERVE_LIMIT_GROUP("RESERVE_LIMIT_GROUP"),

    CHECK_SETTLEMENT_ELIGIBILITY("CHECK_SETTLEMENT_ELIGIBILITY");

    private final String code;

    DecisionObligationType(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
