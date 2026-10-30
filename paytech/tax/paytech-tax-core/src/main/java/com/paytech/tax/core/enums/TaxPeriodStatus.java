package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 期间状态（t_tax_period.status）；本期自动生成为 OPEN。 */
public enum TaxPeriodStatus implements CodeEnum {
    /** 可归属新底稿 */
    OPEN,

    /** 申报准备中 */
    PREPARING,

    /** 已申报 */
    FILED,

    /** 已关闭 */
    CLOSED;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
