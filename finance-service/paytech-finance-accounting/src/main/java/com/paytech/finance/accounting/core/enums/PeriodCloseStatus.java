package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 关账或重开流程状态；CREATED/RUNNING/PENDING_CHECK/APPROVED 时占用活动槽。 */
public enum PeriodCloseStatus implements CodeEnum {
    /** 已创建 */
    CREATED,

    /** 执行中 */
    RUNNING,

    /** 待复核 */
    PENDING_CHECK,

    /** 已批准 */
    APPROVED,

    /** 已驳回 */
    REJECTED,

    /** 已完成 */
    COMPLETED,

    /** 已失败 */
    FAILED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
