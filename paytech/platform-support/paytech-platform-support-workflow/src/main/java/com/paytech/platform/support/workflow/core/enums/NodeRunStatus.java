package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 节点运行状态，与 t_workflow_node_run.status 一致。 */
@Getter
@RequiredArgsConstructor
public enum NodeRunStatus implements CodeEnum {

    /** TASK 等待执行人完成动作。 */
    ACTIVE("ACTIVE"),

    /** TASK 等待审批。 */
    APPROVING("APPROVING"),

    /** 并行汇合等待分支完成。 */
    WAITING("WAITING"),

    /** 已通过；网关与起止节点写入即为该状态。 */
    PASSED("PASSED"),

    /** 审批退回修改，本节点已开新一轮。 */
    RETURNED("RETURNED"),

    /** 审批驳回，已退回上一节点。 */
    REJECTED("REJECTED"),

    /** 实例终止时仍在进行。 */
    CANCELLED("CANCELLED");

    private final String code;
}
