package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.platform.support.workflow.core.common.WorkflowLimits;
import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.graph.WorkflowEdge;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;

/**
 * 校验流程图形状：上限、key、连线端点、起止节点、各类节点的出入边数量、配置归属、回环与可达性
 * （技术方案 3.4 第 1、2、3、8 项及上限）。
 */
final class GraphShapeRules {

    /** graph_json 上限：48 KiB。 */
    static final int MAX_GRAPH_BYTES = WorkflowLimits.MAX_GRAPH_BYTES;

    /** 节点数上限。 */
    static final int MAX_NODES = 100;

    private final WorkflowGraph graph;

    private final GraphTopology topology;

    private final ProblemCollector problems;

    /**
     * 绑定被校验的流程图。
     *
     * @param graph 流程图
     * @param topology 拓扑视图
     * @param problems 问题收集
     */
    GraphShapeRules(WorkflowGraph graph, GraphTopology topology, ProblemCollector problems) {
        this.graph = graph;
        this.topology = topology;
        this.problems = problems;
    }

    /**
     * 执行全部形状校验。
     *
     * @param graphJsonBytes graph_json 字节数
     * @return 是否无回环；有回环时依赖拓扑序的后续校验应跳过
     */
    boolean check(int graphJsonBytes) {
        checkLimits(graphJsonBytes);
        checkNodeKeys();
        checkEdges();
        checkStartAndEnd();
        graph.nodes().forEach(this::checkDegree);
        graph.nodes().forEach(this::checkConfigOwnership);
        boolean acyclic = checkCycles();
        checkReachability();
        return acyclic;
    }

    /**
     * 校验 graph_json 大小与节点数量上限。
     *
     * @param graphJsonBytes graph_json 字节数
     */
    private void checkLimits(int graphJsonBytes) {
        if (graphJsonBytes > MAX_GRAPH_BYTES) {
            problems.graph(WorkflowProblemCode.GRAPH_TOO_LARGE,
                "流程图大小 " + graphJsonBytes + " 字节，超过上限 " + MAX_GRAPH_BYTES + " 字节");
        }
        if (graph.nodes().size() > MAX_NODES) {
            problems.graph(WorkflowProblemCode.TOO_MANY_NODES,
                "节点有 " + graph.nodes().size() + " 个，超过上限 " + MAX_NODES + " 个");
        }
    }

    /** 校验节点 key 格式、唯一性与必要的名称。 */
    private void checkNodeKeys() {
        Set<String> seen = new HashSet<>();
        for (WorkflowNode node : graph.nodes()) {
            if (!WorkflowValueFormats.isKey(node.key())) {
                problems.node(WorkflowProblemCode.NODE_KEY_INVALID, node.key(),
                    "节点 key 必须以字母开头，只含字母、数字或下划线，最长 64：" + node.key());
            } else if (!seen.add(node.key())) {
                problems.node(WorkflowProblemCode.NODE_KEY_DUPLICATE, node.key(), "节点 key 重复：" + node.key());
            }
            boolean named = node.type() == WorkflowNodeType.TASK || node.type() == WorkflowNodeType.EXCLUSIVE;
            if (named && WorkflowValueFormats.isBlank(node.name())) {
                problems.node(WorkflowProblemCode.NODE_NAME_MISSING, node.key(), "任务节点与排他网关必须填写名称");
            }
        }
    }

    /** 校验连线 key、端点与重复连线。 */
    private void checkEdges() {
        Set<String> keys = new HashSet<>();
        Set<String> pairs = new HashSet<>();
        for (WorkflowEdge edge : graph.edges()) {
            if (!WorkflowValueFormats.isKey(edge.key())) {
                problems.edge(WorkflowProblemCode.EDGE_KEY_INVALID, null, edge.key(),
                    "连线 key 必须以字母开头，只含字母、数字或下划线，最长 64：" + edge.key());
            } else if (!keys.add(edge.key())) {
                problems.edge(WorkflowProblemCode.EDGE_KEY_DUPLICATE, null, edge.key(), "连线 key 重复：" + edge.key());
            }
            if (graph.node(edge.from()).isEmpty() || graph.node(edge.to()).isEmpty()) {
                problems.edge(WorkflowProblemCode.EDGE_ENDPOINT_UNKNOWN, null, edge.key(),
                    "连线 " + edge.key() + " 的起点或终点不存在：" + edge.from() + " → " + edge.to());
            } else if (!pairs.add(edge.from() + "\u0000" + edge.to())) {
                problems.edge(WorkflowProblemCode.EDGE_DUPLICATE, edge.from(), edge.key(),
                    "重复连线：" + edge.from() + " → " + edge.to());
            }
        }
    }

    /** 校验恰好一个开始节点、至少一个结束节点以及起止节点的连线。 */
    private void checkStartAndEnd() {
        List<WorkflowNode> starts = nodesOf(WorkflowNodeType.START);
        if (starts.size() != 1) {
            problems.graph(WorkflowProblemCode.START_COUNT, "开始节点必须恰好一个，当前为 " + starts.size() + " 个");
        }
        if (nodesOf(WorkflowNodeType.END).isEmpty()) {
            problems.graph(WorkflowProblemCode.END_MISSING, "至少需要一个结束节点");
        }
        for (WorkflowNode start : starts) {
            if (!topology.validIncoming(start.key()).isEmpty()) {
                problems.node(WorkflowProblemCode.START_HAS_INCOMING, start.key(), "开始节点不能有入边");
            }
            if (topology.validOutgoing(start.key()).size() != 1) {
                problems.node(WorkflowProblemCode.START_OUTGOING, start.key(), "开始节点必须恰好有一条出边");
            }
        }
        for (WorkflowNode end : nodesOf(WorkflowNodeType.END)) {
            if (!topology.validOutgoing(end.key()).isEmpty()) {
                problems.node(WorkflowProblemCode.END_HAS_OUTGOING, end.key(), "结束节点不能有出边");
            }
        }
    }

    /**
     * 校验任务节点、排他网关与并行网关的出入边数量。
     *
     * @param node 节点
     */
    private void checkDegree(WorkflowNode node) {
        int out = topology.validOutgoing(node.key()).size();
        int in = topology.validIncoming(node.key()).size();
        switch (node.type()) {
            case TASK -> require(out == 1, WorkflowProblemCode.TASK_OUTGOING, node, "任务节点必须恰好有一条出边");
            case EXCLUSIVE -> require(out >= 2, WorkflowProblemCode.EXCLUSIVE_OUTGOING, node, "排他网关至少需要两条出边");
            case PARALLEL_SPLIT -> require(out >= 2, WorkflowProblemCode.PARALLEL_SPLIT_OUTGOING, node,
                "并行分叉至少需要两条出边");
            case PARALLEL_JOIN -> {
                require(in >= 2, WorkflowProblemCode.PARALLEL_JOIN_INCOMING, node, "并行汇合至少需要两条入边");
                require(out == 1, WorkflowProblemCode.PARALLEL_JOIN_OUTGOING, node, "并行汇合必须恰好有一条出边");
            }
            default -> {
            }
        }
    }

    /**
     * 校验审批、动作只出现在任务节点，条件与默认边只出现在排他网关。
     *
     * @param node 节点
     */
    private void checkConfigOwnership(WorkflowNode node) {
        boolean task = node.type() == WorkflowNodeType.TASK;
        if (!task && (node.approval() != null || node.hasActions())) {
            problems.node(WorkflowProblemCode.NODE_CONFIG_NOT_ALLOWED, node.key(), "只有任务节点可以配置审批人与动作");
        }
        boolean exclusive = node.type() == WorkflowNodeType.EXCLUSIVE;
        if (!exclusive && (!node.conditions().isEmpty() || node.defaultEdgeKey() != null)) {
            problems.node(WorkflowProblemCode.NODE_CONFIG_NOT_ALLOWED, node.key(), "只有排他网关可以配置条件与默认出边");
        }
    }

    /**
     * 校验没有回环；驳回由运行时规则实现，不靠图上的回边。
     *
     * @return 无回环返回 true
     */
    private boolean checkCycles() {
        List<WorkflowEdge> backEdges = topology.backEdges();
        for (WorkflowEdge edge : backEdges) {
            problems.edge(WorkflowProblemCode.GRAPH_CYCLE, edge.from(), edge.key(),
                "连线 " + edge.key() + "（" + edge.from() + " → " + edge.to() + "）构成回环；驳回无需画回边");
        }
        return backEdges.isEmpty();
    }

    /** 校验所有节点从开始可达、且都能到达某个结束节点。 */
    private void checkReachability() {
        List<WorkflowNode> starts = nodesOf(WorkflowNodeType.START);
        if (starts.size() == 1) {
            Set<String> reachable = topology.reachableFrom(starts.get(0).key());
            graph.nodes().stream().filter(node -> !reachable.contains(node.key()))
                .forEach(node -> problems.node(WorkflowProblemCode.NODE_UNREACHABLE, node.key(), "节点从开始节点不可达"));
        }
        Set<String> ends = nodesOf(WorkflowNodeType.END).stream().map(WorkflowNode::key).collect(Collectors.toSet());
        if (!ends.isEmpty()) {
            Set<String> reachingEnd = topology.reaching(ends);
            graph.nodes().stream().filter(node -> !reachingEnd.contains(node.key()))
                .forEach(node -> problems.node(WorkflowProblemCode.NODE_CANNOT_REACH_END, node.key(),
                    "节点不能到达任何结束节点"));
        }
    }

    /**
     * 条件不成立时记录节点问题。
     *
     * @param condition 应成立的条件
     * @param code 问题码
     * @param node 节点
     * @param message 中文说明
     */
    private void require(boolean condition, WorkflowProblemCode code, WorkflowNode node, String message) {
        if (!condition) {
            problems.node(code, node.key(), message);
        }
    }

    /**
     * 某类型的全部节点。
     *
     * @param type 节点类型
     * @return 节点
     */
    private List<WorkflowNode> nodesOf(WorkflowNodeType type) {
        return graph.nodes().stream().filter(node -> node.type() == type).toList();
    }
}
