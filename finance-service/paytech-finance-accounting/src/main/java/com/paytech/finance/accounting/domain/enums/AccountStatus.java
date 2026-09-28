package com.paytech.finance.accounting.domain.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 账户是否允许新的会计分录过账；终态账户仍可查询历史余额。 */
public enum AccountStatus implements CodeEnum {
    ACTIVE,

    INACTIVE,

    CLOSED;

    @Override
    public String getCode() {
        return name();
    }
}
