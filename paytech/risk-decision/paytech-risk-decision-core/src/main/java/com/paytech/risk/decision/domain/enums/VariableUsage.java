package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 统一变量的通用合同代码；不登记业务变量编码。 */
public enum VariableUsage implements CodeEnum {
    RULE_CONDITION,

    FLOW_CONDITION,

    ACTION_PARAMETER;

    /** 返回稳定持久化代码。 */
    @Override public String getCode() {
        return name();
    }
}
