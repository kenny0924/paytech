package com.paytech.platform.support.workflow.domainservice.process;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.LongSupplier;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.runtime.NavigationPlan;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunState;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunStatusChange;

/**
 * 一次导航计算的工作区：在已有节点运行的副本上累积状态变化与新建运行，最后生成 {@link NavigationPlan}。
 */
final class NavigationWorkspace {

    private final WorkflowGraph graph;

    private final LongSupplier nodeRunIds;

    private final Map<Long, NodeRunState> runs = new LinkedHashMap<>();

    private final List<NodeRunStatusChange> changes = new ArrayList<>();

    private final List<Long> createdIds = new ArrayList<>();

    private boolean completed;

    /**
     * 以流程图与实例已有的节点运行构造工作区。
     *
     * @param graph 实例绑定版本的流程图
     * @param existingRuns 实例全部节点运行
     * @param nodeRunIds 新节点运行的 ID 生成器；不新建运行的计算可传 null
     */
    NavigationWorkspace(WorkflowGraph graph, Collection<NodeRunState> existingRuns, LongSupplier nodeRunIds) {
        this.graph = graph;
        this.nodeRunIds = nodeRunIds;
        existingRuns.forEach(run -> runs.put(run.nodeRunId(), run));
    }

    /** 流程图。 */
    WorkflowGraph graph() {
        return graph;
    }

    /**
     * 按 ID 取节点运行，不存在属于数据不一致。
     *
     * @param nodeRunId 节点运行 ID
     * @return 节点运行
     */
    NodeRunState run(Long nodeRunId) {
        NodeRunState run = runs.get(nodeRunId);
        if (run == null) {
            throw new ErrorException("实例中不存在节点运行：" + nodeRunId);
        }
        return run;
    }

    /**
     * 按 ID 取节点运行；ID 为空或不存在时为空。
     *
     * @param nodeRunId 节点运行 ID
     * @return 节点运行
     */
    Optional<NodeRunState> find(Long nodeRunId) {
        return nodeRunId == null ? Optional.empty() : Optional.ofNullable(runs.get(nodeRunId));
    }

    /**
     * 节点在流程图中的定义，缺失属于数据不一致。
     *
     * @param nodeKey 节点 key
     * @return 节点
     */
    WorkflowNode node(String nodeKey) {
        return graph.node(nodeKey).orElseThrow(() -> new ErrorException("流程版本中不存在节点：" + nodeKey));
    }

    /**
     * 节点最新一轮的运行。
     *
     * @param nodeKey 节点 key
     * @return 最新一轮；从未运行时为空
     */
    Optional<NodeRunState> latest(String nodeKey) {
        return runs.values().stream().filter(run -> run.nodeKey().equals(nodeKey))
            .max(Comparator.comparingInt(NodeRunState::roundNo));
    }

    /**
     * 节点处于 WAITING 的运行（并行汇合）。
     *
     * @param nodeKey 节点 key
     * @return WAITING 运行；没有时为空
     */
    Optional<NodeRunState> waiting(String nodeKey) {
        return runs.values().stream()
            .filter(run -> run.nodeKey().equals(nodeKey) && run.status() == NodeRunStatus.WAITING).findFirst();
    }

    /**
     * 实例全部节点运行（含本次变化）。
     *
     * @return 节点运行
     */
    Collection<NodeRunState> runs() {
        return runs.values();
    }

    /**
     * 新建一轮节点运行，轮次为该节点已有最大轮次加一。
     *
     * @param node 节点
     * @param branchKey 并行分支；不在分支内为 null
     * @param previousNodeRunId 实际来源；START 与汇合为 null
     * @param status 初始状态
     * @param selectedEdgeKey 排他网关选中的出边；其他为 null
     * @return 新运行
     */
    NodeRunState create(WorkflowNode node, String branchKey, Long previousNodeRunId, NodeRunStatus status,
        String selectedEdgeKey) {
        Objects.requireNonNull(nodeRunIds, "新建节点运行需要 ID 生成器");
        int round = latest(node.key()).map(NodeRunState::roundNo).orElse(0) + 1;
        NodeRunState run = new NodeRunState(nodeRunIds.getAsLong(), node.key(), node.type(), round, branchKey,
            previousNodeRunId, status, selectedEdgeKey);
        runs.put(run.nodeRunId(), run);
        createdIds.add(run.nodeRunId());
        return run;
    }

    /**
     * 改变节点运行状态；本次新建的运行直接改写初始状态，已有运行记为一次状态变化。
     *
     * @param nodeRunId 节点运行 ID
     * @param toStatus 新状态
     * @return 改变后的运行
     */
    NodeRunState change(Long nodeRunId, NodeRunStatus toStatus) {
        NodeRunState before = run(nodeRunId);
        NodeRunState after = before.withStatus(toStatus);
        runs.put(nodeRunId, after);
        if (!createdIds.contains(nodeRunId)) {
            changes.add(new NodeRunStatusChange(nodeRunId, before.nodeKey(), before.status(), toStatus));
        }
        return after;
    }

    /** 标记到达结束节点。 */
    void complete() {
        completed = true;
    }

    /**
     * 生成导航结果。
     *
     * @return 导航结果
     */
    NavigationPlan plan() {
        return new NavigationPlan(changes, createdIds.stream().map(runs::get).toList(), completed);
    }
}
