package com.paytech.platform.support.workflow.service.processor.response;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成动作的内部结果。 */
@Getter
@AllArgsConstructor
public class WorkflowSubmissionResult {

    /** 动作运行 ID。 */
    private final long actionRunId;

    /** 完成后动作状态。 */
    private final String status;

    /** 完成后实例状态。 */
    private final String instanceStatus;
}
