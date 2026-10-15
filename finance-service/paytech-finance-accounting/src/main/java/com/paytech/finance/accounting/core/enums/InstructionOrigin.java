package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 记账指令来源。 */
public enum InstructionOrigin implements CodeEnum {
    /** 规则自动生成 */
    AUTO,

    /** 人工调整生成 */
    MANUAL;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
