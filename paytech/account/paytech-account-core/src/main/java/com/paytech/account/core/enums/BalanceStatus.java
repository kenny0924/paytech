package com.paytech.account.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/** 余额账户状态（t_account_balance.status）。 */
public enum BalanceStatus implements CodeEnum {
    /** 正常 */
    ACTIVE;

    /** 数据库与接口使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
