package com.paytech.platform.support.api.workflow.response;

import java.time.Instant;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 实例摘要。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class WorkflowInstanceSummaryBody {

    /** 实例 ID。 */
    private String instanceId;

    /** 流程定义 ID。 */
    private String definitionId;

    /** 标题。 */
    private String title;

    /** 业务关联键；可空。 */
    private String businessKey;

    /** 状态。 */
    private String status;

    /** 发起人。 */
    private String startedBy;

    /** 发起人显示名。 */
    private String startedByName;

    /** 发起时间。 */
    private Instant startTime;

    /** 结束时间；运行中为空。 */
    private Instant endTime;

    /** 流程名称；仅列表返回。 */
    private String definitionName;

    /** 实例绑定的流程版本号；仅列表返回。 */
    private Integer versionNo;

    /** 已完成的任务节点数：最新一轮已放行的 TASK 节点（不同节点计一次）；仅列表返回。 */
    private Integer completedTaskCount;

    /** 流程版本中的任务节点总数（含未走到的排他分支）；仅列表返回。 */
    private Integer totalTaskCount;

    /** 当前进行中的节点及处理人；仅列表返回，已结束的实例为空列表。 */
    private List<WorkflowCurrentNodeBody> currentNodes;

    /**
     * 基本摘要（不含列表附加信息）。
     *
     * @param instanceId 实例 ID
     * @param definitionId 流程定义 ID
     * @param title 标题
     * @param businessKey 业务关联键
     * @param status 状态
     * @param startedBy 发起人
     * @param startedByName 发起人显示名
     * @param startTime 发起时间
     * @param endTime 结束时间
     */
    public WorkflowInstanceSummaryBody(String instanceId, String definitionId, String title, String businessKey,
        String status, String startedBy, String startedByName, Instant startTime, Instant endTime) {
        this(instanceId, definitionId, title, businessKey, status, startedBy, startedByName, startTime, endTime, null,
            null, null, null, null);
    }
}
