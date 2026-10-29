package com.paytech.risk.control.domain.underwriting;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 承保列表顶部统计；"本月"按决定时间所在 UTC 自然月。 */
@Getter
@AllArgsConstructor
public final class UnderwritingStatistics {
    /** 生效中的承保结论数。 */
    private final long effective;

    /** 草稿数。 */
    private final long draft;

    /** 待复核数。 */
    private final long submitted;

    /** 本月作出 APPROVED 的版本数（含之后被替代的版本）。 */
    private final long approvedThisMonth;

    /** 本月作出 DECLINED 的版本数。 */
    private final long declinedThisMonth;
}
