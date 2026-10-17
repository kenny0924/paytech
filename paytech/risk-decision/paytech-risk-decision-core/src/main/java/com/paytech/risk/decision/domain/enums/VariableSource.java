package com.paytech.risk.decision.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 统一变量的通用合同代码；不登记业务变量编码。 */
public enum VariableSource implements CodeEnum {
    INPUT,

    CONSTANT,

    COLLECTION,

    NODE_OUTPUT,

    /** 实时累计 Feature，经不可变 Feature Version 计算。 */
    VELOCITY,

    /** 名单命中 Feature，经不可变 Feature Version 计算。 */
    LIST;

    /** 返回稳定持久化代码。 */
    @Override public String getCode() {
        return name();
    }
}
