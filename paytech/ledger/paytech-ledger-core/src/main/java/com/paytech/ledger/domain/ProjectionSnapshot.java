package com.paytech.ledger.domain;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 账户按分录聚合的四个余额值，用于投影重建解决摘要。 */
@Getter
@AllArgsConstructor
public class ProjectionSnapshot {
    /** 累计借方。 */
    private final BigDecimal debitTotal;

    /** 累计贷方。 */
    private final BigDecimal creditTotal;

    /** 按正常方向的有符号净余额。 */
    private final BigDecimal netBalance;

    /** 涉及该账户的最大分录序号。 */
    private final long lastJournalSequence;
}
