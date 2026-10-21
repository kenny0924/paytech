package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 渠道账单净额方向，相对平台（t_channel_statement.cash_direction）
 */
public enum CashDirection implements CodeEnum {
    /** 渠道净额打入平台银行账户 */
    CREDIT,

    /** 渠道净额从平台银行账户扣出 */
    DEBIT,

    /** 净额为 0，对平后不生成预期银行流水 */
    NONE;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
