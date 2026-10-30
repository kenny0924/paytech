package com.paytech.risk.control.core.dal.dataobject;

import java.time.Instant;

import com.paytech.risk.control.core.enums.LimitTier;
import com.paytech.risk.control.core.enums.LimitTierApprovalLevel;
import com.paytech.risk.control.core.enums.LimitTierChangeStatus;
import com.paytech.risk.control.core.enums.LimitTierSource;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;

import lombok.Data;

/** 档位变更记录（t_client_limit_tier_change）：承保建档、存量迁移与运营申请；追加为主，只有 PENDING 行可变。 */
@Data
public class ClientLimitTierChange {
    /** 变更记录 ID。 */
    private long changeId;

    /** Client ID。 */
    private long clientId;

    /** 变更来源。 */
    private LimitTierSource changeType;

    /** 变更前档位；建档为空。 */
    private LimitTier fromTier;

    /** 目标档位。 */
    private LimitTier toTier;

    /** 状态。 */
    private LimitTierChangeStatus status;

    /**
     * 待审批占位
     * <li>PENDING 为 true，其余为空；唯一索引保证每个 Client 至多一个待审批申请。</li>
     */
    private Boolean pendingSlot;

    /** 审批级别；建档与迁移为空。 */
    private LimitTierApprovalLevel approvalLevel;

    /** 申请时有效承保的 CRR；无承保或未评为空。 */
    private UnderwritingRiskLevel crrSnapshot;

    /** 建档或申请时的有效承保版本 ID；无承保为空。 */
    private Long underwritingId;

    /** 变更理由。 */
    private String reason;

    /** 上线复核说明；可空。 */
    private String goLiveNote;

    /** 复核清单：资格状态。 */
    private String chkQualificationStatus;

    /** 复核清单：资格有效期。 */
    private Instant chkQualificationValidTo;

    /** 复核清单：有效承保版本号。 */
    private Integer chkUnderwritingVersionNo;

    /** 复核清单：CLIENT 合同 ID。 */
    private Long chkAgreementId;

    /** 复核清单：合同状态。 */
    private String chkAgreementStatus;

    /** 复核清单：ENABLED 的支付方式开通项代码，逗号分隔。 */
    private String chkEnabledCapabilities;

    /** 复核清单：未 ENABLED 的支付方式开通项代码，逗号分隔。 */
    private String chkPendingCapabilities;

    /** 复核清单人工项：渠道报备已确认。 */
    private Boolean chkChannelRegistration;

    /** 复核清单人工项：技术接入已确认。 */
    private Boolean chkIntegration;

    /** 复核清单人工项：生产交易验证已完成。 */
    private Boolean chkProductionVerified;

    /** 复核清单人工项：未结事项已处理。 */
    private Boolean chkOpenIssuesCleared;

    /** 进入或延长观察期时设定的目标天数；其他为空。 */
    private Integer observationTargetDays;

    /** 进入或延长观察期时设定的目标放行笔数；其他为空。 */
    private Integer observationTargetTransactions;

    /** 申请时已过的观察期天数；当前档位非 OBSERVATION 为空。 */
    private Integer observationDaysAtRequest;

    /** 申请时观察期内已放行笔数；当前档位非 OBSERVATION 为空。 */
    private Long observationAllowedAtRequest;

    /** 申请人账号 ID；承保建档为空，迁移为执行人。 */
    private Long requestedBy;

    /** 申请（或建档、迁移）UTC 时间。 */
    private Instant requestedTime;

    /** 审批、驳回或撤回人账号 ID；建档与迁移为空。 */
    private Long decidedBy;

    /** 审批、驳回或撤回 UTC 时间。 */
    private Instant decidedTime;

    /** 审批意见；驳回必填。 */
    private String decisionComment;

    /** 并发版本，从 1 开始。 */
    private int version;

    /** 创建 UTC 时间。 */
    private Instant createTime;

    /** 更新 UTC 时间。 */
    private Instant updateTime;
}
