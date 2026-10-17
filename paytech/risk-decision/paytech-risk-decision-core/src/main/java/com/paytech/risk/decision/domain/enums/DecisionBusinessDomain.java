package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum DecisionBusinessDomain implements CodeEnum {
    DUE_DILIGENCE("DUE_DILIGENCE"),

    CHECKOUT("CHECKOUT"),

    PAYMENT("PAYMENT"),

    SETTLEMENT("SETTLEMENT"),

    DISPUTE("DISPUTE");

    private final String code;

    DecisionBusinessDomain(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
