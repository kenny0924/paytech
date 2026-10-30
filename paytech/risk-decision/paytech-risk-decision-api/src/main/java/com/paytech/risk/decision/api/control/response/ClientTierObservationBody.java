package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 观察期进度；放行笔数按风控放行的支付尝试计，近似值。 */
@Getter
@Setter
public class ClientTierObservationBody {

    /** 观察期开始 UTC 时间。 */
    private String startedTime;

    /** 已过整天数。 */
    private Integer daysElapsed;

    /** 目标天数。 */
    private Integer targetDays;

    /** 观察期内放行的支付尝试数。 */
    private Long allowedTransactions;

    /** 目标放行笔数。 */
    private Integer targetTransactions;

    /** 天数或笔数任一达到目标即为 true（只提示，不自动升档）。 */
    private Boolean due;
}
