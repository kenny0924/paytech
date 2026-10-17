package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum ExecutionVersionStatus implements CodeEnum {
    REGISTERED("REGISTERED"),

    CONFIGURATION_ONLY("CONFIGURATION_ONLY"),

    ACTIVE("ACTIVE"),

    RETIRED("RETIRED");

    private final String code;

    ExecutionVersionStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
