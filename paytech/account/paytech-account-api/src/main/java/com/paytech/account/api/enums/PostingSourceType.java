package com.paytech.account.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 过账批次的来源类型
 * <li>ledger 消费 AccountPosted 时按它分流：ECONOMIC_FACT 过账，BALANCE_MOVEMENT 只推进进度、不过账（桶移动在 ledger 控制科目中净额为零）。</li>
 */
public enum PostingSourceType implements CodeEnum {
    /** 消费 BillingFactRecorded 生成的批次；不影响 Owner 的事实也生成 0 流水批次 */
    ECONOMIC_FACT,

    /** 余额桶移动（接口命令或到期任务）生成的批次 */
    BALANCE_MOVEMENT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
