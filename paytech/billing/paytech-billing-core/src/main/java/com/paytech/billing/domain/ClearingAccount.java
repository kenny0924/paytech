package com.paytech.billing.domain;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** reconciliation 按 MID + 币种解析的 ACTIVE 清算账户（Owner 为 reconciliation，只读取得）。 */
@Getter
@AllArgsConstructor
public class ClearingAccount {
    /** 清算账户主键：CLEARING_ACCOUNT 引用与渠道付方 / 收方标识 */
    private final long clearingAccountId;

    /** 清算账户所属 Operating Entity */
    private final String entityNo;

    /** 清算币种 */
    private final String currency;
}
