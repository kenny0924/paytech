package com.paytech.risk.decision.api.control.response;

import lombok.Getter;
import lombok.Setter;

/** 一条档位变更记录：承保建档、存量迁移或运营申请（含复核清单快照与四眼审批）。 */
@Getter
@Setter
public class ClientTierChangeResponseBody {

    /** 变更记录 ID。 */
    private String changeId;

    /** Client ID。 */
    private String clientId;

    /** 变更来源：UNDERWRITING_INIT、MIGRATION、REQUEST。 */
    private String changeType;

    /** 变更前档位；建档为空。 */
    private String fromTier;

    /** 目标档位。 */
    private String toTier;

    /** 状态：PENDING、APPROVED、REJECTED、WITHDRAWN。 */
    private String status;

    /** 审批级别：STANDARD 或 ELEVATED；建档与迁移为空。 */
    private String approvalLevel;

    /** 申请时有效承保的 CRR；无承保或未评为空。 */
    private String crrSnapshot;

    /** 建档或申请时的有效承保版本 ID；无承保为空。 */
    private String underwritingId;

    /** 变更理由。 */
    private String reason;

    /** 上线复核说明；可空。 */
    private String goLiveNote;

    /** 复核清单快照；非上线复核为空。 */
    private ClientTierChecklistBody checklist;

    /** 进入或延长观察期时设定的目标天数；其他为空。 */
    private Integer observationTargetDays;

    /** 进入或延长观察期时设定的目标放行笔数；其他为空。 */
    private Integer observationTargetTransactions;

    /** 申请时已过的观察期天数（当前档位为 OBSERVATION 时冻结）；其他为空。 */
    private Integer observationDaysAtRequest;

    /** 申请时观察期内已放行笔数（当前档位为 OBSERVATION 时冻结）；其他为空。 */
    private Long observationAllowedAtRequest;

    /** 申请人 Operations 账号 ID；承保建档为空，迁移为执行人。 */
    private String requestedBy;

    /** 申请（或建档、迁移）UTC 时间。 */
    private String requestedTime;

    /** 审批、驳回或撤回人账号 ID；建档与迁移为空。 */
    private String decidedBy;

    /** 审批、驳回或撤回 UTC 时间。 */
    private String decidedTime;

    /** 审批意见；驳回必有。 */
    private String decisionComment;

    /** 申请并发版本。 */
    private Integer version;
}
