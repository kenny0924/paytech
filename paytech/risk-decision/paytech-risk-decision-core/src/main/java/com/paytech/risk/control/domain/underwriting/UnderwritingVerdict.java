package com.paytech.risk.control.domain.underwriting;

import java.time.Instant;

import com.paytech.risk.control.core.enums.UnderwritingDecisionSource;
import com.paytech.risk.control.core.enums.UnderwritingStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次承保决定写入版本行的事实：APPROVED 或 DECLINED 及其来源、决定人、原因与替代关系
 * <li>LOW / MEDIUM 提交即决定时同时带提交人与提交时间；HIGH 复核时二者保持原值（为空）。</li>
 */
@Getter
@AllArgsConstructor
public final class UnderwritingVerdict {
    /** 决定后的状态：APPROVED 或 DECLINED。 */
    private final UnderwritingStatus status;

    /** 结论来源。 */
    private final UnderwritingDecisionSource decisionSource;

    /** 决定人账号 ID。 */
    private final long decidedBy;

    /** 拒绝原因；批准为空。 */
    private final String declineReason;

    /** 复核意见；非复核或拒绝为空。 */
    private final String reviewComment;

    /** 批准时替代的上一有效版本 ID；没有为空。 */
    private final Long supersedesUnderwritingId;

    /** 同时提交时的提交人；复核为空（保留原提交人）。 */
    private final Long submittedBy;

    /** 决定时间，同时也是同时提交的提交时间。 */
    private final Instant decidedTime;

    /** 是否为批准。 */
    public boolean approved() {
        return status == UnderwritingStatus.APPROVED;
    }
}
