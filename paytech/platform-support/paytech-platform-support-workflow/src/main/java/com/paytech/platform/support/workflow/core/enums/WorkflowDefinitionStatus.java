package com.paytech.platform.support.workflow.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 流程定义状态，与 t_workflow_definition.status 一致。 */
@Getter
@RequiredArgsConstructor
public enum WorkflowDefinitionStatus implements CodeEnum {

    /** 可发起新实例。 */
    ACTIVE("ACTIVE"),

    /** 停用：不能发起新实例，在途实例照常运行。 */
    INACTIVE("INACTIVE");

    private final String code;
}
