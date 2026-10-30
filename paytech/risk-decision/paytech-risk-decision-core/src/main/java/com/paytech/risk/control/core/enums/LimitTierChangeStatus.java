package com.paytech.risk.control.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 档位变更记录状态；建档与迁移直接为 APPROVED，只有 PENDING 可变。 */
public enum LimitTierChangeStatus implements CodeEnum {
    /** 待审批。 */
    PENDING("PENDING"),

    /** 已批准，档位已生效。 */
    APPROVED("APPROVED"),

    /** 已驳回。 */
    REJECTED("REJECTED"),

    /** 申请人已撤回。 */
    WITHDRAWN("WITHDRAWN");

    private final String code;

    LimitTierChangeStatus(String code) {
        this.code = code;
    }

    @Override
    public String getCode() {
        return code;
    }
}
