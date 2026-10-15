package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 记账指令的验证和入账状态；POSTED 后不可回退。 */
public enum InstructionStatus implements CodeEnum {
    /** 已接收 */
    RECEIVED,

    /** 验证中 */
    VALIDATING,

    /** 已验证待过账 */
    VALIDATED,

    /** 已过账 */
    POSTED,

    /** 已拒绝 */
    REJECTED,

    /** 来源冲突 */
    CONFLICT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
