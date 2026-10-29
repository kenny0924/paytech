package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 自动承保规则版本状态。 */
public enum UnderwritingRuleStatus implements CodeEnum {
    /** 草稿，未生效。 */
    DRAFT("DRAFT"),

    /** 当前生效，至多一个。 */
    ACTIVE("ACTIVE"),

    /** 已退役。 */
    RETIRED("RETIRED");

    private final String code;

    UnderwritingRuleStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
