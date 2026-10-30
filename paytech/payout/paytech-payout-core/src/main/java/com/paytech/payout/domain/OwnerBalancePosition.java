package com.paytech.payout.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** account 返回的 Owner 在一个 OE × 币种下的余额（主单位金额）。 */
@Getter
@AllArgsConstructor
public class OwnerBalancePosition {
    /** Operating Entity 编号 */
    private final String entityNo;

    /** 币种 */
    private final String currency;

    /** PENDING */
    private final BigDecimal pending;

    /** AVAILABLE */
    private final BigDecimal available;

    /** LOCKED */
    private final BigDecimal locked;

    /** 保证金；Local Partner 为空 */
    private final BigDecimal securityDeposit;

    /** 追偿应收 */
    private final BigDecimal recoveryReceivable;
}
