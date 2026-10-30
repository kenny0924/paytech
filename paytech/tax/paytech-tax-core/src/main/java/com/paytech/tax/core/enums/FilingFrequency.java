package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 申报周期（t_tax_registration.filing_frequency）。 */
public enum FilingFrequency implements CodeEnum {
    /** 按法定时区日历月 */
    MONTHLY,

    /** 按法定时区日历季度 */
    QUARTERLY;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
