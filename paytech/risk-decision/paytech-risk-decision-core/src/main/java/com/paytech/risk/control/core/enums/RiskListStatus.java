package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 名单状态。 */
public enum RiskListStatus implements CodeEnum {
    /** 可维护成员并参与命中判断。 */
    ACTIVE("ACTIVE"),

    /** 停用，不再命中。 */
    INACTIVE("INACTIVE");

    private final String code;

    RiskListStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
