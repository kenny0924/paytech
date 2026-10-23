package com.paytech.platform.support.api.workflow.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 我已处理的一项：我提交过的动作或我已决定的审批。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowHandledTaskBody {

    /** 类型：ACTION 或 APPROVAL。 */
    private String taskType;

    /** 动作运行 ID 或审批 ID。 */
    private String taskId;

    /** 实例 ID。 */
    private String instanceId;

    /** 实例标题。 */
    private String instanceTitle;

    /** 实例当前状态。 */
    private String instanceStatus;

    /** 流程名称。 */
    private String definitionName;

    /** 节点 key。 */
    private String nodeKey;

    /** 节点名称。 */
    private String nodeName;

    /** 节点轮次。 */
    private Integer roundNo;

    /** 动作 key；审批为空。 */
    private String actionKey;

    /** 结果：动作运行当前状态（COMPLETED、CANCELLED 等）或审批决定（APPROVED、RETURNED、REJECTED）。 */
    private String result;

    /** 处理时间：最近一次提交或决定时间。 */
    private Instant handledTime;
}
