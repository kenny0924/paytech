package com.paytech.reconciliation.api.enums;

import com.paytech.foundation.common.enums.CodeEnum;

/**
 * 银行账户资金用途（t_cash_account.account_purpose）
 * <li>同主体 + 币种 + 用途只允许一个 ACTIVE；创建后不可改，换用途即关旧户、开新户。</li>
 */
public enum AccountPurpose implements CodeEnum {
    /** 渠道结算资金进出 */
    CHANNEL_SETTLEMENT,

    /** 出款 */
    PAYOUT,

    /** 缴税 */
    TAX;

    /** 数据库、接口与事件使用的稳定编码，与常量名一致。 */
    @Override
    public String getCode() {
        return name();
    }
}
