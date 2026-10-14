package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 名单用途，仅用于展示与规则命名约定；命中后的处置由规则决定。 */
public enum RiskListPurpose implements CodeEnum {
    /** 阻断类名单。 */
    BLOCK("BLOCK"),

    /** 放行类名单。 */
    ALLOW("ALLOW");

    private final String code;

    RiskListPurpose(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
