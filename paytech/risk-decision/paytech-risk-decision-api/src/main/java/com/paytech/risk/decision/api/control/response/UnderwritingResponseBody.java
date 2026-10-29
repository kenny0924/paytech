package com.paytech.risk.decision.api.control.response;

import com.paytech.risk.decision.api.control.request.UnderwritingDecisionBody;

import lombok.Getter;
import lombok.Setter;

/** 单个承保版本的详情。 */
@Getter
@Setter
public class UnderwritingResponseBody {

    /** 承保版本 ID，十进制字符串。 */
    private String underwritingId;

    /** Client ID，十进制字符串。 */
    private String clientId;

    /** 同一 Client 内从 1 递增的版本号。 */
    private Integer versionNo;

    /** 状态：DRAFT、SUBMITTED、APPROVED、DECLINED 或 SUPERSEDED。 */
    private String status;

    /** 结论来源：MANUAL 或 AUTO_RULE；未决定为空。 */
    private String decisionSource;

    /** 自动承保使用的规则版本号；人工结论为空。 */
    private Integer ruleVersionNo;

    /** 复核级别：LOW、MEDIUM 或 HIGH；CRR 缺失为 MEDIUM，HIGH 须四眼复核。 */
    private String reviewLevel;

    /** 输入快照；提交后冻结。 */
    private UnderwritingSnapshotBody input;

    /** 结论字段。 */
    private UnderwritingDecisionBody decision;

    /** 估算敞口；无法估算为空。 */
    private UnderwritingExposureBody estimatedExposure;

    /** 无法估算的原因：VOLUME_MISSING、FULFILLMENT_TYPE_MISSING 或 FULFILLMENT_DAYS_MISSING；可估算时为空。 */
    private String exposureUnavailableReason;

    /** DECLINED 的拒绝原因。 */
    private String declineReason;

    /** HIGH 复核批准时的复核意见。 */
    private String reviewComment;

    /** 由修订创建时的修订原因。 */
    private String revisionReason;

    /** 本版本批准后替代的上一有效版本 ID；未替代为空。 */
    private String supersedesUnderwritingId;

    /** 建草稿或触发自动承保的账号 ID。 */
    private String createdBy;

    /** 提交人账号 ID；自动承保为空。 */
    private String submittedBy;

    /** 提交时间，UTC ISO-8601；自动承保为生成时间。 */
    private String submittedAt;

    /** 决定人账号 ID；LOW / MEDIUM 等于提交人，HIGH 为复核人，自动承保为空。 */
    private String decidedBy;

    /** 决定时间，UTC ISO-8601。 */
    private String decidedAt;

    /** 被新版本替代的时间，UTC ISO-8601。 */
    private String supersededAt;

    /** 创建时间，UTC ISO-8601。 */
    private String createdAt;

    /** 最后更新时间，UTC ISO-8601。 */
    private String updatedAt;

    /** 并发版本；保存、提交、复核与修订以此为 expectedVersion。 */
    private Integer version;
}
