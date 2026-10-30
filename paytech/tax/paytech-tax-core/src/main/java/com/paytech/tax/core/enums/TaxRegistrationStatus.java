package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 注册状态（t_tax_registration.status）。 */
public enum TaxRegistrationStatus implements CodeEnum {
    /** 办理中，不可用于计税与开票 */
    PENDING,

    /** 有效 */
    ACTIVE,

    /** 暂停 */
    SUSPENDED,

    /** 已注销 */
    DEREGISTERED;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
