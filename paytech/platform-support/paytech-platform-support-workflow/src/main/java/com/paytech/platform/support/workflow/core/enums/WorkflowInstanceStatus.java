package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 流程实例状态，与 t_workflow_instance.status 一致。 */
@Getter
@RequiredArgsConstructor
public enum WorkflowInstanceStatus implements CodeEnum {

    /** 运行中。 */
    RUNNING("RUNNING"),

    /** 已到达结束节点。 */
    COMPLETED("COMPLETED"),

    /** 已终止。 */
    TERMINATED("TERMINATED");

    private final String code;
}
