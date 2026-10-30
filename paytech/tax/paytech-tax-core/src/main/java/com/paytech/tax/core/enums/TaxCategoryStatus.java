package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 税类状态（t_tax_category.status）。 */
public enum TaxCategoryStatus implements CodeEnum {
    /** 可用于新计算 */
    ACTIVE,

    /** 已停用；历史计算仍可读取，新计算 BLOCKED(UNKNOWN_TAX_CATEGORY) */
    RETIRED;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
