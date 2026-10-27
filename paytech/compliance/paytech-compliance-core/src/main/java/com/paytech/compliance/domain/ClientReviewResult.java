package com.paytech.compliance.domain;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 已结束（退回、批准、拒绝、过期）的 Client 入驻审核结果；发布给 client-master 的业务事实来源。 */
@Getter
@AllArgsConstructor
public class ClientReviewResult {
    /** 主案件 ID。 */
    private final long caseId;
    /** 主案件版本，作为事件聚合版本。 */
    private final long caseVersion;
    /** 申请 ID。 */
    private final long applicationId;
    /** 来源提交编号。 */
    private final String sourceSubmissionId;
    /** 结果：RETURNED、APPROVED、REJECTED 或 EXPIRED。 */
    private final String outcome;
    /** 兼容退回分区；可空。 */
    private final String returnSection;
    /** 客户可见说明；可空。 */
    private final String publicNote;
    /** 结束时间。 */
    private final Instant decidedAt;
    /** 主案件最终决定 ID；退回与过期为空。 */
    private final Long decisionId;
    /** 最终决定人 Account ID；退回与过期为空。 */
    private final Long decidedBy;
    /** 案件类型。 */
    private final String caseType;
    /** 提交轮次。 */
    private final int roundNo;
    /** 冻结档次；批准 / 拒绝填写。 */
    private final String tier;
    /** 冻结评级；批准 / 拒绝且已评级填写。 */
    private final String riskRating;
    /** 退回补件期限；仅退回填写。 */
    private final Instant dueTime;
    /** 退回的需求级错误；非退回为空列表。 */
    private final List<RequirementError> errors;
}
