package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** Risk Decision 的闭合稳定代码。 */
public enum ConfigurationStatus implements CodeEnum {
    CONFIGURATION_ONLY("CONFIGURATION_ONLY"),

    ACTIVE("ACTIVE");

    private final String code;

    ConfigurationStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
