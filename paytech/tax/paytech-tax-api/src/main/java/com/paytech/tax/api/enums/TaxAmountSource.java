package com.paytech.tax.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 税额来源（t_tax_calculation.tax_amount_source）。 */
public enum TaxAmountSource implements CodeEnum {
    /** 按规则包计算（READY / BLOCKED） */
    COMPUTED,

    /** 源 Owner 传入的自报税额（NOT_MANAGED），可为零 */
    DECLARED;

    /** 数据库、接口与消息使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
