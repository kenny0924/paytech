package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 正式交易来源方式（t_tax_transaction.origin）。 */
public enum TaxTransactionOrigin implements CodeEnum {
    /** 由事件消费建立 */
    AUTO,

    /** 人工登记（本期不用） */
    MANUAL;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
