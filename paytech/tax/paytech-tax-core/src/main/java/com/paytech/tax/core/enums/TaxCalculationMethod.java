package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 组件算法（t_tax_calculation_component.calculation_method）。 */
public enum TaxCalculationMethod implements CodeEnum {
    /** 未税价比例：tax = round(base × rate) */
    PERCENTAGE_EXCLUSIVE,

    /** 含税价比例反算：tax = round(gross × rate / (1 + rate)) */
    PERCENTAGE_INCLUSIVE,

    /** 无税率（NOT_COLLECTED、免税等），税额为 0 */
    NONE;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
