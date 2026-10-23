package com.paytech.platform.support.api.workflow.response;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 完成动作的结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowSubmissionResponseBody {

    /** 动作运行 ID。 */
    private String actionRunId;

    /** 完成后动作状态：COMPLETED。 */
    private String status;

    /** 完成后实例状态。 */
    private String instanceStatus;
}
