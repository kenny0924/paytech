package com.paytech.risk.control.domain.underwriting;

import java.math.BigDecimal;
import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 当前有效承保结论的摘要，供 REQ-04 限额档位在同进程读取。 */
@Getter
@AllArgsConstructor
public final class EffectiveUnderwriting {
    /** 有效版本 ID。 */
    private final long underwritingId;

    /** 版本号。 */
    private final int versionNo;

    /** 观察期天数。 */
    private final int observationDays;

    /** 观察期笔数。 */
    private final int observationTransactions;

    /** 是否要求最低保证金。 */
    private final boolean requireDeposit;

    /** 最低保证金金额；不要求时为空。 */
    private final BigDecimal depositAmount;

    /** 最低保证金币种；不要求时为空。 */
    private final String depositCurrency;

    /** 到账前是否建议阻塞收款。 */
    private final boolean depositBlocksCollection;

    /** 结算周期建议 T+N 的 N；未给出为空。 */
    private final Integer suggestedSettlementDays;

    /** 是否需卡组织高风险登记。 */
    private final boolean highRiskMcc;

    /** 作出结论的时间。 */
    private final Instant decidedTime;
}
