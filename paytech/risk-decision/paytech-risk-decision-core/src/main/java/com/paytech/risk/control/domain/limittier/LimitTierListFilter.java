package com.paytech.risk.control.domain.limittier;

import java.time.Instant;

import com.paytech.risk.control.core.enums.LimitTier;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 档位列表筛选条件。 */
@Getter
@AllArgsConstructor
public final class LimitTierListFilter {
    /** 档位；空为全部。 */
    private final LimitTier tier;

    /** true 只列有待审批申请的 Client。 */
    private final boolean pendingApproval;

    /** true 只列 OBSERVATION 且已过天数达到目标天数的 Client。 */
    private final boolean observationDue;

    /** 关键字：Client 名称包含或编号相等；空为不筛选。 */
    private final String keyword;

    /** 关键字为正整数时同时按 Client ID 相等匹配，否则为空。 */
    private final Long keywordClientId;

    /** 判断观察期到期的当前时间。 */
    private final Instant now;
}
