package com.paytech.platform.support.workflow.domain.query;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 我的待办的一行：待执行的动作运行或待决定的审批行，组合实例与节点运行信息。 */
@Getter
@Setter
public class WorkflowTaskView {

    /** 待办类型。允许值：ACTION、APPROVAL。 */
    private String taskType;

    /** 动作运行 ID 或审批 ID。 */
    private Long taskId;

    /** 所属实例。 */
    private Long instanceId;

    /** 实例标题。 */
    private String instanceTitle;

    /** 所属流程定义。 */
    private Long definitionId;

    /** 实例绑定的流程版本。 */
    private Long definitionVersionId;

    /** 节点运行 ID。 */
    private Long nodeRunId;

    /** 节点 key。 */
    private String nodeKey;

    /** 节点轮次。 */
    private Integer roundNo;

    /** 动作 key；审批为空。 */
    private String actionKey;

    /** 待办开始时间：动作为节点激活时间，审批为审批行创建时间。 */
    private Instant waitingSince;
}
