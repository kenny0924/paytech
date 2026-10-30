package com.paytech.tax.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 正式交易类型（t_tax_transaction.transaction_kind）；本期只有 ORIGINAL。 */
public enum TaxTransactionKind implements CodeEnum {
    /** 原始销售 */
    ORIGINAL,

    /** 退款 */
    REFUND,

    /** 调整 */
    ADJUSTMENT,

    /** 人工登记进项 */
    MANUAL_INPUT;

    /** 数据库使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
