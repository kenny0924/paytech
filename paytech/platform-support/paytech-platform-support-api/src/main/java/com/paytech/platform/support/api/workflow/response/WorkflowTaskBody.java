package com.paytech.platform.support.api.workflow.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 我的待办一行。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowTaskBody {

    /** ACTION 或 APPROVAL。 */
    private String taskType;

    /** 动作运行 ID 或审批 ID。 */
    private String taskId;

    /** 实例 ID。 */
    private String instanceId;

    /** 实例标题。 */
    private String instanceTitle;

    /** 节点 key。 */
    private String nodeKey;

    /** 节点轮次。 */
    private Integer roundNo;

    /** 动作 key；审批为空。 */
    private String actionKey;

    /** 待办开始时间，用于计算停留时长。 */
    private Instant waitingSince;

    /** 流程名称。 */
    private String definitionName;

    /** 节点名称（实例绑定版本中的名称）。 */
    private String nodeName;
}
