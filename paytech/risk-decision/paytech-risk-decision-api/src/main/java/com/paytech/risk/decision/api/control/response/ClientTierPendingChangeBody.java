package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** Client 当前待审批的档位变更申请摘要。 */
@Getter
@Setter
public class ClientTierPendingChangeBody {

    /** 申请 ID。 */
    private String changeId;

    /** 变更前档位。 */
    private String fromTier;

    /** 目标档位。 */
    private String toTier;

    /** 审批级别：STANDARD 或 ELEVATED。 */
    private String approvalLevel;

    /** 申请人 Operations 账号 ID。 */
    private String requestedBy;

    /** 申请 UTC 时间。 */
    private String requestedTime;
}
