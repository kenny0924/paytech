package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 承保列表顶部统计；"本月"按 decided_time 所在 UTC 自然月。 */
@Getter
@Setter
public class UnderwritingStatisticsResponseBody {

    /** 生效中的承保结论数。 */
    private Long effective;

    /** 草稿数。 */
    private Long draft;

    /** 待复核数。 */
    private Long submitted;

    /** 本月作出 APPROVED 的版本数（含之后被替代的版本）。 */
    private Long approvedThisMonth;

    /** 本月作出 DECLINED 的版本数。 */
    private Long declinedThisMonth;
}
