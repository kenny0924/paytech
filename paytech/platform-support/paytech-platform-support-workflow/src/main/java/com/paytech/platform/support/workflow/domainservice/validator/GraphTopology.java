package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;

import com.paytech.platform.support.workflow.domain.graph.WorkflowEdge;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;

/**
 * 流程图的拓扑计算：回边（回环）、拓扑序与可达性。只沿两端都是已知节点的连线计算。
 */
final class GraphTopology {

    private final WorkflowGraph graph;

    private final Set<String> nodeKeys = new LinkedHashSet<>();

    /**
     * 以流程图构造拓扑视图。
     *
     * @param graph 流程图
     */
    GraphTopology(WorkflowGraph graph) {
        this.graph = graph;
        graph.nodes().stream().map(WorkflowNode::key).filter(Objects::nonNull).forEach(nodeKeys::add);
    }

    /**
     * 两端都是已知节点的出边。
     *
     * @param nodeKey 节点 key
     * @return 出边
     */
    List<WorkflowEdge> validOutgoing(String nodeKey) {
        return graph.outgoing(nodeKey).stream().filter(edge -> nodeKeys.contains(edge.to())).toList();
    }

    /**
     * 两端都是已知节点的入边。
     *
     * @param nodeKey 节点 key
     * @return 入边
     */
    List<WorkflowEdge> validIncoming(String nodeKey) {
        return graph.incoming(nodeKey).stream().filter(edge -> nodeKeys.contains(edge.from())).toList();
    }

    /**
     * 找出构成回环的回边：深度优先遍历中指向仍在栈内节点的连线。
     *
     * @return 回边；无环时为空列表
     */
    List<WorkflowEdge> backEdges() {
        Map<String, Integer> state = new HashMap<>();
        List<WorkflowEdge> result = new ArrayList<>();
        for (String key : nodeKeys) {
            if (!state.containsKey(key)) {
                visit(key, state, result);
            }
        }
        return result;
    }

    /**
     * 深度优先访问；state 为 1 表示在栈内，2 表示已完成。
     *
     * @param key 当前节点
     * @param state 访问状态
     * @param result 回边收集
     */
    private void visit(String key, Map<String, Integer> state, List<WorkflowEdge> result) {
        state.put(key, 1);
        for (WorkflowEdge edge : validOutgoing(key)) {
            Integer target = state.get(edge.to());
            if (target == null) {
                visit(edge.to(), state, result);
            } else if (target == 1) {
                result.add(edge);
            }
        }
        state.put(key, 2);
    }

    /**
     * 无环图的拓扑序（Kahn 算法，同层保持节点配置顺序）。调用前须确认无回边。
     *
     * @return 节点 key 的拓扑序
     */
    List<String> topologicalOrder() {
        Map<String, Integer> inDegree = new HashMap<>();
        nodeKeys.forEach(key -> inDegree.put(key, validIncoming(key).size()));
        Deque<String> ready = new ArrayDeque<>();
        nodeKeys.stream().filter(key -> inDegree.get(key) == 0).forEach(ready::add);
        List<String> order = new ArrayList<>();
        while (!ready.isEmpty()) {
            String key = ready.poll();
            order.add(key);
            for (WorkflowEdge edge : validOutgoing(key)) {
                if (inDegree.merge(edge.to(), -1, Integer::sum) == 0) {
                    ready.add(edge.to());
                }
            }
        }
        return order;
    }

    /**
     * 从给定节点沿出边可达的节点（含自身）。
     *
     * @param start 起点
     * @return 可达节点
     */
    Set<String> reachableFrom(String start) {
        return closure(Set.of(start), key -> validOutgoing(key).stream().map(WorkflowEdge::to).toList());
    }

    /**
     * 能沿出边到达任一目标的节点（含目标自身）。
     *
     * @param targets 目标节点
     * @return 能到达目标的节点
     */
    Set<String> reaching(Set<String> targets) {
        return closure(targets, key -> validIncoming(key).stream().map(WorkflowEdge::from).toList());
    }

    /**
     * 广度优先求闭包。
     *
     * @param seeds 起始节点
     * @param next 相邻节点
     * @return 闭包
     */
    private Set<String> closure(Set<String> seeds, Function<String, List<String>> next) {
        Set<String> seen = new LinkedHashSet<>(seeds);
        Deque<String> queue = new ArrayDeque<>(seeds);
        while (!queue.isEmpty()) {
            for (String neighbor : next.apply(queue.poll())) {
                if (seen.add(neighbor)) {
                    queue.add(neighbor);
                }
            }
        }
        return seen;
    }
}
