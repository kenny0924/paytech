package com.paytech.risk.control.domain.limittier;

import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.ClientLimitTierChange;
import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierApprovalLevel;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;
import com.paytech.risk.control.core.enums.LimitTierSource;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;

import lombok.Getter;

/** 档位变更记录的跨模块只读结果。 */
@Getter
public final class ClientTierChangeView {
    /** 变更记录 ID。 */
    private final long changeId;

    /** Client ID。 */
    private final long clientId;

    /** 变更来源。 */
    private final LimitTierSource changeType;

    /** 变更前档位；建档为空。 */
    private final LimitTier fromTier;

    /** 目标档位。 */
    private final LimitTier toTier;

    /** 状态。 */
    private final LimitTierChangeStatus status;

    /** 审批级别；建档与迁移为空。 */
    private final LimitTierApprovalLevel approvalLevel;

    /** 申请时 CRR；可空。 */
    private final UnderwritingRiskLevel crrSnapshot;

    /** 有效承保版本 ID；可空。 */
    private final Long underwritingId;

    /** 变更理由。 */
    private final String reason;

    /** 上线复核说明；可空。 */
    private final String goLiveNote;

    /** 复核清单；非上线复核为空。 */
    private final TierChecklist checklist;

    /** 观察期目标天数；可空。 */
    private final Integer observationTargetDays;

    /** 观察期目标放行笔数；可空。 */
    private final Integer observationTargetTransactions;

    /** 申请时已过观察期天数；可空。 */
    private final Integer observationDaysAtRequest;

    /** 申请时观察期放行笔数；可空。 */
    private final Long observationAllowedAtRequest;

    /** 申请人；可空。 */
    private final Long requestedBy;

    /** 申请时间。 */
    private final Instant requestedTime;

    /** 审批、驳回或撤回人；可空。 */
    private final Long decidedBy;

    /** 审批、驳回或撤回时间；可空。 */
    private final Instant decidedTime;

    /** 审批意见；可空。 */
    private final String decisionComment;

    /** 并发版本。 */
    private final int version;

    /** 由变更记录构造。 */
    private ClientTierChangeView(ClientLimitTierChange row) {
        this.changeId = row.getChangeId();
        this.clientId = row.getClientId();
        this.changeType = row.getChangeType();
        this.fromTier = row.getFromTier();
        this.toTier = row.getToTier();
        this.status = row.getStatus();
        this.approvalLevel = row.getApprovalLevel();
        this.crrSnapshot = row.getCrrSnapshot();
        this.underwritingId = row.getUnderwritingId();
        this.reason = row.getReason();
        this.goLiveNote = row.getGoLiveNote();
        this.checklist = TierChecklist.of(row);
        this.observationTargetDays = row.getObservationTargetDays();
        this.observationTargetTransactions = row.getObservationTargetTransactions();
        this.observationDaysAtRequest = row.getObservationDaysAtRequest();
        this.observationAllowedAtRequest = row.getObservationAllowedAtRequest();
        this.requestedBy = row.getRequestedBy();
        this.requestedTime = row.getRequestedTime();
        this.decidedBy = row.getDecidedBy();
        this.decidedTime = row.getDecidedTime();
        this.decisionComment = row.getDecisionComment();
        this.version = row.getVersion();
    }

    /** 由变更记录构造只读结果；空记录返回空。 */
    public static ClientTierChangeView of(ClientLimitTierChange row) {
        return row == null ? null : new ClientTierChangeView(row);
    }
}
