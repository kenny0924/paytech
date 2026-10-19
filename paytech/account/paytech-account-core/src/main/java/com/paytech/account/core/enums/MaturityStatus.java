package com.paytech.account.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 计费事实应付余额到期转可用的进度（t_balance_applied_fact.maturity_status，设计 8.5）。 */
public enum MaturityStatus implements CodeEnum {
    /** 没有到期时间，或事实没有记入 PENDING 的贷方流水 */
    NOT_APPLICABLE,

    /** 等待到期任务移动 */
    PENDING,

    /** 已全部移动到 AVAILABLE */
    MATURED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
