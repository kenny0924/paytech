package com.paytech.platform.support.api.workflow.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 提交动作的验证结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSubmissionResponseBody {

    /** 动作运行 ID。 */
    private String actionRunId;

    /** 提交后动作状态：VERIFIED 或 UNVERIFIED。 */
    private String status;

    /** 本次验证记录 ID。 */
    private String verificationId;

    /** 未通过原因；通过为空。 */
    private String failureReason;

    /** 提交后实例状态。 */
    private String instanceStatus;
}
