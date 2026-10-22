package com.paytech.platform.support.workflow.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 提交动作的内部结果。 */
@Getter
@AllArgsConstructor
public class WorkflowSubmissionResult {

    /** 动作运行 ID。 */
    private final long actionRunId;

    /** 提交后动作状态。 */
    private final String status;

    /** 验证记录 ID。 */
    private final long verificationId;

    /** 未通过原因；通过为空。 */
    private final String failureReason;

    /** 提交后实例状态。 */
    private final String instanceStatus;
}
