package com.paytech.account.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 经济事实对余额的影响状态（t_balance_applied_fact.apply_status）。 */
public enum AppliedFactStatus implements CodeEnum {
    /** 已应用到余额 */
    APPLIED,

    /** 已应用后被替代事实冲回 */
    REVERSED,

    /** 已被替代、自身事件尚未到达；到达时不应用 */
    PENDING_SUPERSEDED,

    /** 自身事件到达时已被替代，未应用 */
    SKIPPED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
