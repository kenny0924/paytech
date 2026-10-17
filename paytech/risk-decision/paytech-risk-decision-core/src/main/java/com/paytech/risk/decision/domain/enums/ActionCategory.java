package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum ActionCategory implements CodeEnum {
    DECISION("DECISION"),

    TAG("TAG"),

    RESTRICTION("RESTRICTION"),

    LIST("LIST"),

    NOTIFICATION("NOTIFICATION"),

    SETTLEMENT("SETTLEMENT"),

    LIMIT("LIMIT");

    private final String code;

    ActionCategory(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
