package com.paytech.platform.support.integration.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 流程的有向图：节点与出口为顶点，连线为边；提供可达、环检测与支配节点计算。
 *
 * <p>支配节点用于“必经”校验：从入口到某顶点的每条路径都经过 d 时，d 支配该顶点（流程结构第 6 节第 4 项）。纯 Java，无副作用。
 */
public final class FlowGraph {

    private final String entry;
    private final Set<String> vertices;
    private final Map<String, List<String>> successors = new LinkedHashMap<>();
    private final Map<String, List<String>> predecessors = new HashMap<>();

    /**
     * 构建图。
     *
     * @param entry 入口顶点
     * @param vertices 全部顶点（节点与出口），保持声明顺序
     * @param edges 边，每项为 [from, to]；端点不在顶点集合中的边被忽略，由调用方另行报告
     */
    public FlowGraph(String entry, Set<String> vertices, List<String[]> edges) {
        this.entry = entry;
        this.vertices = new LinkedHashSet<>(vertices);
        vertices.forEach(vertex -> {
            successors.put(vertex, new ArrayList<>());
            predecessors.put(vertex, new ArrayList<>());
        });
        for (String[] edge : edges) {
            if (vertices.contains(edge[0]) && vertices.contains(edge[1])) {
                successors.get(edge[0]).add(edge[1]);
                predecessors.get(edge[1]).add(edge[0]);
            }
        }
    }

    /** 从入口可达的顶点。 */
    public Set<String> reachable() {
        var seen = new LinkedHashSet<String>();
        if (!vertices.contains(entry)) {
            return seen;
        }
        var queue = new ArrayDeque<String>(List.of(entry));
        while (!queue.isEmpty()) {
            String vertex = queue.poll();
            if (seen.add(vertex)) {
                queue.addAll(successors.get(vertex));
            }
        }
        return seen;
    }

    /** 是否存在环（含自环），按 Kahn 拓扑排序判断。 */
    public boolean hasCycle() {
        return topologicalOrder().size() != vertices.size();
    }

    /** 拓扑序；存在环时只返回无环部分。 */
    public List<String> topologicalOrder() {
        var indegree = new HashMap<String, Integer>();
        vertices.forEach(vertex -> indegree.put(vertex, predecessors.get(vertex).size()));
        var queue = new ArrayDeque<String>();
        vertices.stream().filter(vertex -> indegree.get(vertex) == 0).forEach(queue::add);
        var order = new ArrayList<String>();
        while (!queue.isEmpty()) {
            String vertex = queue.poll();
            order.add(vertex);
            for (String next : successors.get(vertex)) {
                if (indegree.merge(next, -1, Integer::sum) == 0) {
                    queue.add(next);
                }
            }
        }
        return order;
    }

    /**
     * 每个可达顶点的支配集（含自身）；要求图无环，按拓扑序一次求交。
     *
     * @return 顶点到支配集的映射，不可达顶点不出现
     */
    public Map<String, Set<String>> dominators() {
        var reachable = reachable();
        var result = new HashMap<String, Set<String>>();
        for (String vertex : topologicalOrder()) {
            if (!reachable.contains(vertex)) {
                continue;
            }
            Set<String> dominators = null;
            for (String predecessor : predecessors.get(vertex)) {
                if (!result.containsKey(predecessor)) {
                    continue;
                }
                if (dominators == null) {
                    dominators = new HashSet<>(result.get(predecessor));
                } else {
                    dominators.retainAll(result.get(predecessor));
                }
            }
            var own = dominators == null ? new HashSet<String>() : dominators;
            own.add(vertex);
            result.put(vertex, own);
        }
        return result;
    }
}
