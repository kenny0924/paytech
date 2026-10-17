package com.paytech.risk.compliance.domain;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已结束的 Client 入驻审核结果；发布给 CCT 的业务事实来源。 */
@Getter
@AllArgsConstructor
public class ClientReviewResult {
    /** 主案件 ID。 */
    private final long caseId;
    /** 主案件版本，作为事件来源版本。 */
    private final long caseVersion;
    /** CCT 申请 ID。 */
    private final long applicationId;
    /** 来源提交编号。 */
    private final String sourceSubmissionId;
    /** 结果：RETURNED、APPROVED 或 REJECTED。 */
    private final String outcome;
    /** 退回分区；非退回为空。 */
    private final String returnSection;
    /** 客户可见说明；可空。 */
    private final String publicNote;
    /** 结束时间。 */
    private final Instant decidedAt;
    /** 主任务最终决定 ID；退回为空。 */
    private final Long decisionId;
    /** 最终决定人 Account ID；退回为空。 */
    private final Long decidedBy;
}
