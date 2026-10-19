package com.paytech.billing.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 规则行的计算方法（t_billing_fact_line.source_evidence.calculationMethod）。 */
public enum CalculationMethod implements CodeEnum {
    /** 基数 × 费率 */
    PERCENTAGE,

    /** 固定金额 */
    FIXED,

    /** 按基数所在阶梯的费率作用于全部基数 */
    TIERED_WHOLE_BASE,

    /** 逐档累进，各档费率只作用于落入该档的基数 */
    TIERED_PROGRESSIVE,

    /** 直接取值，用于税额、实际成本等 */
    PASS_THROUGH;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
