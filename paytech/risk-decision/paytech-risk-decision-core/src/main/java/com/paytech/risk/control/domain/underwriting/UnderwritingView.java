package com.paytech.risk.control.domain.underwriting;

import java.time.Instant;

import com.paytech.risk.control.core.dal.dataobject.ClientUnderwriting;
import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingRiskLevel;
import com.paytech.risk.control.core.enums.UnderwritingStatus;

import lombok.Getter;

/** 承保版本查询结果：版本行与其规则版本号的组合（t_client_underwriting + t_underwriting_rule_version）。 */
@Getter
public final class UnderwritingView {
    /** 承保版本 ID。 */
    private final long underwritingId;

    /** Client ID。 */
    private final long clientId;

    /** 版本号。 */
    private final int versionNo;

    /** 状态。 */
    private final UnderwritingStatus status;

    /** 结论来源；未决定为空。 */
    private final UnderwritingDecisionSource decisionSource;

    /** 自动承保规则版本号；人工为空。 */
    private final Integer ruleVersionNo;

    /** 复核级别。 */
    private final UnderwritingRiskLevel reviewLevel;

    /** 输入、结论与估算敞口。 */
    private final UnderwritingContent content;

    /** 拒绝原因。 */
    private final String declineReason;

    /** 复核意见。 */
    private final String reviewComment;

    /** 修订原因。 */
    private final String revisionReason;

    /** 被替代的上一有效版本 ID。 */
    private final Long supersedesUnderwritingId;

    /** 创建人。 */
    private final long createdBy;

    /** 提交人。 */
    private final Long submittedBy;

    /** 提交时间。 */
    private final Instant submittedTime;

    /** 决定人。 */
    private final Long decidedBy;

    /** 决定时间。 */
    private final Instant decidedTime;

    /** 被替代时间。 */
    private final Instant supersededTime;

    /** 创建时间。 */
    private final Instant createTime;

    /** 更新时间。 */
    private final Instant updateTime;

    /** 并发版本。 */
    private final int version;

    /** 由版本行与规则版本号构造。 */
    private UnderwritingView(ClientUnderwriting row, Integer ruleVersionNo) {
        this.underwritingId = row.getUnderwritingId();
        this.clientId = row.getClientId();
        this.versionNo = row.getVersionNo();
        this.status = row.getStatus();
        this.decisionSource = row.getDecisionSource();
        this.ruleVersionNo = ruleVersionNo;
        this.reviewLevel = row.getReviewLevel();
        this.content = UnderwritingContent.of(row);
        this.declineReason = row.getDeclineReason();
        this.reviewComment = row.getReviewComment();
        this.revisionReason = row.getRevisionReason();
        this.supersedesUnderwritingId = row.getSupersedesUnderwritingId();
        this.createdBy = row.getCreatedBy();
        this.submittedBy = row.getSubmittedBy();
        this.submittedTime = row.getSubmittedTime();
        this.decidedBy = row.getDecidedBy();
        this.decidedTime = row.getDecidedTime();
        this.supersededTime = row.getSupersededTime();
        this.createTime = row.getCreateTime();
        this.updateTime = row.getUpdateTime();
        this.version = row.getVersion();
    }

    /**
     * 构造查询结果
     *
     * @param row 版本行
     * @param ruleVersionNo 版本行引用的规则版本号；人工结论为空
     * @return 查询结果
     */
    public static UnderwritingView of(ClientUnderwriting row, Integer ruleVersionNo) {
        return new UnderwritingView(row, ruleVersionNo);
    }
}
