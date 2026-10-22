package com.paytech.platform.support.workflow.domain;

import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowCallback;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinitionVersion;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowInstance;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.domain.runtime.CallbackTarget;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一条通知回调投递所需的本库事实：记录本身、解析后的目标、实例、节点运行与流程定义版本。 */
@Getter
@AllArgsConstructor
public class WorkflowCallbackDelivery {

    /** 通知回调。 */
    private final WorkflowCallback callback;

    /** 解析后的目标。 */
    private final CallbackTarget target;

    /** 实例。 */
    private final WorkflowInstance instance;

    /** 节点级触发的节点运行；实例级为 null。 */
    private final WorkflowNodeRun nodeRun;

    /** 节点名称；实例级为 null。 */
    private final String nodeName;

    /** 流程定义。 */
    private final WorkflowDefinition definition;

    /** 实例绑定的版本。 */
    private final WorkflowDefinitionVersion version;
}
