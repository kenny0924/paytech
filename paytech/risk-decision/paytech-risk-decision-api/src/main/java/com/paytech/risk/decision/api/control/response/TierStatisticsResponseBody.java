package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 档位列表顶部统计；观察期到期只按天数判定。 */
@Getter
@Setter
public class TierStatisticsResponseBody {

    /** VERIFICATION 档位 Client 数。 */
    private Long verification;

    /** OBSERVATION 档位 Client 数。 */
    private Long observation;

    /** STANDARD 档位 Client 数。 */
    private Long standard;

    /** RESTRICTED 档位 Client 数。 */
    private Long restricted;

    /** 待审批申请数。 */
    private Long pendingApproval;

    /** 观察期已过目标天数、待确认的 Client 数。 */
    private Long observationDue;
}
