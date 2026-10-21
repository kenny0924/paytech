package com.paytech.reconciliation.api.cash.response;

import lombok.Getter;
import lombok.Setter;

/** 主体 + 币种 + 用途的当前 ACTIVE 银行账户。 */
@Getter
@Setter
public class CurrentCashAccountResponseBody {
    /** 银行账户主键 */
    private String cashAccountId;

    /** 银行账号末四位 */
    private String accountLast4;
}
