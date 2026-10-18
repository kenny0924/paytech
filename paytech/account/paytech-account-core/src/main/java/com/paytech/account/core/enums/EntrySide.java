package com.paytech.account.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 余额变化的借贷方向（t_balance_applied_entry.entry_side）。 */
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
