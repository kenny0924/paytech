package com.paytech.platform.support.workflow.domain.query;

import java.time.Instant;

import lombok.Getter;
import lombok.Setter;

/** 我已处理的一行：我提交过的动作运行或我已决定的审批行。 */
@Getter
@Setter
public class WorkflowHandledTaskView {

    /** 类型。允许值：ACTION、APPROVAL。 */
    private String taskType;

    /** 动作运行 ID 或审批 ID。 */
    private Long taskId;

    /** 实例 ID。 */
    private Long instanceId;

    /** 实例标题。 */
    private String instanceTitle;

    /** 流程定义 ID。 */
    private Long definitionId;

    /** 实例绑定的流程版本。 */
    private Long definitionVersionId;

    /** 节点 key。 */
    private String nodeKey;

    /** 轮次。 */
    private Integer roundNo;

    /** 动作 key；审批为空。 */
    private String actionKey;

    /** 结果：动作运行当前状态或审批决定状态。 */
    private String result;

    /** 处理时间：最近一次提交或决定时间。 */
    private Instant handledTime;

    /** 实例当前状态。 */
    private String instanceStatus;
}
