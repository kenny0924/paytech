package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 计费事实明细行的影响方向（t_billing_fact_line.effect_direction）。 */
public enum FactLineDirection implements CodeEnum {
    /** 新增经济影响 */
    CHARGE,

    /** 冲回原有经济影响 */
    REVERSAL,

    /** 调整原有经济影响，具体借贷方向由付方 / 收方决定 */
    ADJUSTMENT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
