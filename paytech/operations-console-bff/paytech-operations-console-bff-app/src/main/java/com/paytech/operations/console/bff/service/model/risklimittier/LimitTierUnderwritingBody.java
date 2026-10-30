package com.paytech.operations.console.bff.service.model.risklimittier;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 工作台中的有效承保摘要（risk-decision REQ-03），用于展示 CRR、观察期目标与 T+N。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LimitTierUnderwritingBody {
    /** 有效承保版本 ID。 */
    private String underwritingId;

    /** 版本号。 */
    private Integer versionNo;

    /** 快照时 CRR；未评为 null。 */
    private String crr;

    /** 观察期天数。 */
    private Integer observationDays;

    /** 观察期笔数。 */
    private Integer observationTransactions;

    /** 结算周期建议 T+N 的 N；未给出为 null。 */
    private Integer suggestedSettlementDays;

    /** 结论来源：MANUAL 或 AUTO_RULE。 */
    private String decisionSource;
}
