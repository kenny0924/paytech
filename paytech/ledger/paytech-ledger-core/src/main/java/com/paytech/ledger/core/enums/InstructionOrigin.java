package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 记账指令来源。 */
public enum InstructionOrigin implements CodeEnum {
    /** 规则自动生成（关账损益结转等 ledger 内部流程） */
    AUTO,

    /** account 入账批次（AccountPosted）生成：Owner 控制科目行来自 account 流水，其余行按规则 */
    ACCOUNT_POSTING,

    /** 人工调整生成 */
    MANUAL;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
