package com.paytech.platform.support.workflow.domain.runtime;

import java.util.List;

import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;

/**
 * 一次导航计算的结果：需要更新的已有节点运行、需要新建的节点运行，以及实例是否完成。
 *
 * <p>调用方在同一本地事务内按 statusChanges（条件更新，行数不符即冲突）与 createdNodeRuns（插入）落库，
 * 再为新激活的 TASK 创建动作运行（ACTIVE）或审批行（APPROVING）。导航本身不访问数据库。</p>
 *
 * @param statusChanges 已有节点运行的状态变化，按发生顺序
 * @param createdNodeRuns 新建的节点运行，按创建顺序；ID 由调用方提供的生成器分配
 * @param instanceCompleted 是否到达结束节点，实例应进入 COMPLETED
 */
public record NavigationPlan(List<NodeRunStatusChange> statusChanges, List<NodeRunState> createdNodeRuns,
    boolean instanceCompleted) {

    /** 复制列表，保持不可变。 */
    public NavigationPlan {
        statusChanges = List.copyOf(statusChanges);
        createdNodeRuns = List.copyOf(createdNodeRuns);
    }

    /**
     * 本次新激活的任务节点运行（ACTIVE 或 APPROVING）。
     *
     * @return 新激活的 TASK 运行
     */
    public List<NodeRunState> activatedTasks() {
        return createdNodeRuns.stream()
            .filter(run -> run.nodeType() == WorkflowNodeType.TASK)
            .filter(run -> run.status() == NodeRunStatus.ACTIVE || run.status() == NodeRunStatus.APPROVING)
            .toList();
    }
}
