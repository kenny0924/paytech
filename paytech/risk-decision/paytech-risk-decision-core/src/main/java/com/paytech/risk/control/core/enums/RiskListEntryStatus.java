package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 名单成员状态。 */
public enum RiskListEntryStatus implements CodeEnum {
    /** 当前生效。 */
    ACTIVE("ACTIVE"),

    /** 已过期。 */
    EXPIRED("EXPIRED"),

    /** 人工或动作解除。 */
    RELEASED("RELEASED"),

    /** 被新成员替代。 */
    SUPERSEDED("SUPERSEDED");

    private final String code;

    RiskListEntryStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
