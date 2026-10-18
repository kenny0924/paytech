package com.paytech.account.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 日终核对差异状态（t_balance_discrepancy.status）；阶段 0 不接工单。 */
public enum DiscrepancyStatus implements CodeEnum {
    /** 已记录、待处置 */
    OPEN;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
