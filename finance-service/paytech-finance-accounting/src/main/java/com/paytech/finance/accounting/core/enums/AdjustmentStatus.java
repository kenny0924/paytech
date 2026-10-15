package com.paytech.finance.accounting.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 人工会计调整申请的生命周期状态。 */
public enum AdjustmentStatus implements CodeEnum {
    /** 草稿 */
    DRAFT,

    /** 已提交待复核 */
    SUBMITTED,

    /** 已批准 */
    APPROVED,

    /** 已驳回 */
    REJECTED,

    /** 已过账 */
    POSTED,

    /** 已取消 */
    CANCELLED;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
