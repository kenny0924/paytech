package com.paytech.compliance.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** I01 结果：主审核任务。 */
@Getter
@AllArgsConstructor
public class RegisterClientReviewResponse {
    /** 来源提交编号。 */
    private final String sourceSubmissionId;
    /** 主审核任务 ID。 */
    private final long caseId;
}
