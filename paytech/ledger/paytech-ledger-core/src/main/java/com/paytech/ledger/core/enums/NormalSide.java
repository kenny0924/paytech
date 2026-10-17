package com.paytech.ledger.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 账户或科目余额增加时采用的会计方向。 */
public enum NormalSide implements CodeEnum {
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
