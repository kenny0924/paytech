package com.paytech.risk.control.domain.underwriting;

import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.core.enums.UnderwritingStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 承保列表筛选条件，全部可空。 */
@Getter
@AllArgsConstructor
public final class UnderwritingListFilter {
    /** 状态。 */
    private final UnderwritingStatus status;

    /** CRR。 */
    private final UnderwritingRiskLevel crr;

    /** 结论来源。 */
    private final UnderwritingDecisionSource decisionSource;

    /** 关键字：Client 名称包含或编号相等。 */
    private final String keyword;

    /** 关键字为十进制数字时同时按 Client ID 相等匹配。 */
    private final Long keywordClientId;

    /** Client ID。 */
    private final Long clientId;

    /** 待该账号复核：只列 SUBMITTED 且提交人不是该账号。 */
    private final Long pendingReviewFor;
}
