package com.paytech.client.master.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Risk 审核结果事件解析后的内部请求。 */
@Getter
@AllArgsConstructor
public class ApplyClientReviewResultRequest {
    /** CCT 申请 ID。 */
    @Positive
    private final long applicationId;
    /** Risk 主审核任务 ID。 */
    @Positive
    private final long caseId;
    /** 来源提交编号。 */
    @NotBlank
    private final String submissionId;
    /** RETURNED、APPROVED、REJECTED 或 EXPIRED（v2：退回超期）。 */
    @NotBlank
    @Pattern(regexp = "^(RETURNED|APPROVED|REJECTED|EXPIRED)$")
    private final String outcome;
    /** Risk 主任务最终决定 ID；退回为空。 */
    private final Long decisionId;
    /** 最终决定人 Account ID；退回与超期为空。 */
    private final Long decidedBy;
    /** 退回补件截止时间（v2 RETURNED 填写）；其他结果或 v1 为空。 */
    private final java.time.Instant dueTime;
}
