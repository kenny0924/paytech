package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 地区包状态（t_tax_policy.status）。 */
public enum TaxPolicyStatus implements CodeEnum {
    /** 草稿，不参与计算 */
    DRAFT,

    /** 已批准，内容不可修改，参与计算 */
    APPROVED,

    /** 已退役，只用于解释历史计算 */
    RETIRED;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
