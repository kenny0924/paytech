package com.paytech.platform.support.workflow.domain.runtime;

import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;

/**
 * 一条节点运行的导航所需事实，字段与 t_workflow_node_run 同名列一一对应。
 *
 * @param nodeRunId 节点运行 ID（node_run_id）
 * @param nodeKey 节点 key（node_key）
 * @param nodeType 节点类型（node_type）
 * @param roundNo 轮次（round_no），从 1 开始
 * @param branchKey 所在并行分支，即分叉出边 key（branch_key）；不在分支内为 null
 * @param previousNodeRunId 实际来源节点运行（previous_node_run_id）；START 与并行汇合为 null
 * @param status 状态（status）
 * @param selectedEdgeKey 排他网关选中的出边（selected_edge_key）；其他节点为 null
 */
public record NodeRunState(Long nodeRunId, String nodeKey, WorkflowNodeType nodeType, int roundNo, String branchKey,
    Long previousNodeRunId, NodeRunStatus status, String selectedEdgeKey) {

    /**
     * 返回状态改变后的副本。
     *
     * @param newStatus 新状态
     * @return 副本
     */
    public NodeRunState withStatus(NodeRunStatus newStatus) {
        return new NodeRunState(nodeRunId, nodeKey, nodeType, roundNo, branchKey, previousNodeRunId, newStatus,
            selectedEdgeKey);
    }
}
