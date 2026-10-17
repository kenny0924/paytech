package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum StaticCheckSeverity implements CodeEnum {
    INFO("INFO"),

    WARNING("WARNING"),

    ERROR("ERROR");

    private final String code;

    StaticCheckSeverity(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
