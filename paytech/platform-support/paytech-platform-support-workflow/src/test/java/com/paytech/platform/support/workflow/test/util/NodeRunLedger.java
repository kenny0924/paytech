package com.paytech.platform.support.workflow.test.util;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Assertions;

import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.domain.runtime.NavigationPlan;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunState;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunStatusChange;

/**
 * 测试内存台账：按 t_workflow_node_run 的条件更新语义应用导航计划（旧状态不符即失败），代替后续 Service 的落库。
 */
public final class NodeRunLedger {

    private final AtomicLong ids = new AtomicLong(700200);

    private final List<NodeRunState> runs = new ArrayList<>();

    private boolean completed;

    /**
     * 下一个节点运行 ID。
     *
     * @return ID
     */
    public long nextId() {
        return ids.incrementAndGet();
    }

    /**
     * 应用导航计划。
     *
     * @param plan 导航计划
     * @return 同一计划，便于断言
     */
    public NavigationPlan apply(NavigationPlan plan) {
        for (NodeRunStatusChange change : plan.statusChanges()) {
            NodeRunState current = run(change.nodeRunId());
            Assertions.assertEquals(change.fromStatus(), current.status(), "条件更新的旧状态不符：" + change);
            replace(current.withStatus(change.toStatus()));
        }
        runs.addAll(plan.createdNodeRuns());
        completed = completed || plan.instanceCompleted();
        return plan;
    }

    /**
     * 模拟 Service 自行推进的状态（如 ACTIVE → APPROVING）。
     *
     * @param nodeKey 节点 key（取最新一轮）
     * @param status 新状态
     */
    public void setStatus(String nodeKey, NodeRunStatus status) {
        replace(latest(nodeKey).withStatus(status));
    }

    /**
     * 当前全部节点运行。
     *
     * @return 节点运行
     */
    public List<NodeRunState> runs() {
        return List.copyOf(runs);
    }

    /**
     * 节点最新一轮。
     *
     * @param nodeKey 节点 key
     * @return 最新一轮
     */
    public NodeRunState latest(String nodeKey) {
        return runs.stream().filter(run -> run.nodeKey().equals(nodeKey))
            .max(Comparator.comparingInt(NodeRunState::roundNo)).orElseThrow(() -> new AssertionError("没有运行：" + nodeKey));
    }

    /**
     * 节点的轮次数。
     *
     * @param nodeKey 节点 key
     * @return 轮次数
     */
    public long rounds(String nodeKey) {
        return runs.stream().filter(run -> run.nodeKey().equals(nodeKey)).count();
    }

    /**
     * 按 ID 取运行。
     *
     * @param nodeRunId ID
     * @return 运行
     */
    public NodeRunState run(Long nodeRunId) {
        return runs.stream().filter(run -> run.nodeRunId().equals(nodeRunId)).findFirst()
            .orElseThrow(() -> new AssertionError("没有运行：" + nodeRunId));
    }

    /**
     * 是否已到达结束节点。
     *
     * @return 已完成返回 true
     */
    public boolean completed() {
        return completed;
    }

    /**
     * 替换同 ID 运行。
     *
     * @param updated 新状态的运行
     */
    private void replace(NodeRunState updated) {
        runs.replaceAll(run -> run.nodeRunId().equals(updated.nodeRunId()) ? updated : run);
    }
}
