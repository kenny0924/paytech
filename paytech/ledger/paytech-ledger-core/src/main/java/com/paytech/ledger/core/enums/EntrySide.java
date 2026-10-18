package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 指令行、分录行或调整明细的借贷方向。 */
public enum EntrySide implements CodeEnum {
    /** 借方 */
    DEBIT,

    /** 贷方 */
    CREDIT;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
