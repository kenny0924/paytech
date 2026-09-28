package com.paytech.finance.accounting.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 账户余额增加时采用的会计方向。 */
public enum NormalSide implements CodeEnum {
    DEBIT,

    CREDIT;

    @Override
    public String getCode() {
        return name();
    }
}
