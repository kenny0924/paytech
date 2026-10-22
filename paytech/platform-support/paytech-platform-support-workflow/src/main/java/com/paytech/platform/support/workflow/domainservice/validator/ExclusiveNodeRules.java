package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.graph.EdgeCondition;
import com.paytech.platform.support.workflow.domain.graph.WorkflowEdge;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;

/**
 * 校验排他网关的条件结构：有且只有一条默认出边、条件引用本网关出边、每条出边要么有条件要么是默认边
 * （技术方案 3.4 第 3 项）。子句的变量与类型由 {@link VariableFlowRules} 校验。
 */
final class ExclusiveNodeRules {

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
    ExclusiveNodeRules(WorkflowGraph graph, GraphTopology topology, ProblemCollector problems) {
        this.graph = graph;
        this.topology = topology;
        this.problems = problems;
    }

    /** 校验全部排他网关。 */
    void check() {
        graph.nodes().stream().filter(node -> node.type() == WorkflowNodeType.EXCLUSIVE).forEach(this::checkNode);
    }

    /**
     * 校验一个排他网关。
     *
     * @param node 排他网关
     */
    private void checkNode(WorkflowNode node) {
        Set<String> outgoing = topology.validOutgoing(node.key()).stream().map(WorkflowEdge::key)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        checkDefaultEdge(node, outgoing);
        Set<String> conditioned = new HashSet<>();
        for (EdgeCondition condition : node.conditions()) {
            checkCondition(node, condition, outgoing, conditioned);
        }
        for (String edgeKey : outgoing) {
            if (!conditioned.contains(edgeKey) && !edgeKey.equals(node.defaultEdgeKey())) {
                problems.edge(WorkflowProblemCode.EXCLUSIVE_EDGE_UNUSED, node.key(), edgeKey,
                    "出边 " + edgeKey + " 既没有条件也不是默认边，永远不会被选中");
            }
        }
    }

    /**
     * 校验默认出边存在且属于本网关。
     *
     * @param node 排他网关
     * @param outgoing 本网关出边 key
     */
    private void checkDefaultEdge(WorkflowNode node, Set<String> outgoing) {
        if (node.defaultEdgeKey() == null) {
            problems.node(WorkflowProblemCode.EXCLUSIVE_DEFAULT_MISSING, node.key(), "排他网关必须指定一条默认出边");
        } else if (!outgoing.contains(node.defaultEdgeKey())) {
            problems.edge(WorkflowProblemCode.EXCLUSIVE_DEFAULT_INVALID, node.key(), node.defaultEdgeKey(),
                "默认出边 " + node.defaultEdgeKey() + " 不是本网关的出边");
        }
    }

    /**
     * 校验一组条件引用的出边与子句数量。
     *
     * @param node 排他网关
     * @param condition 条件组
     * @param outgoing 本网关出边 key
     * @param conditioned 已配置条件的出边，用于发现重复
     */
    private void checkCondition(WorkflowNode node, EdgeCondition condition, Set<String> outgoing,
        Set<String> conditioned) {
        String edgeKey = condition.edgeKey();
        if (!outgoing.contains(edgeKey)) {
            problems.edge(WorkflowProblemCode.CONDITION_EDGE_INVALID, node.key(), edgeKey,
                "条件引用的连线 " + edgeKey + " 不是本网关的出边");
        } else if (edgeKey.equals(node.defaultEdgeKey()) || !conditioned.add(edgeKey)) {
            problems.edge(WorkflowProblemCode.CONDITION_EDGE_DUPLICATE, node.key(), edgeKey,
                "出边 " + edgeKey + " 只能配置一组条件，且默认边不能再配条件");
        }
        if (condition.all().isEmpty()) {
            problems.edge(WorkflowProblemCode.CONDITION_EMPTY, node.key(), edgeKey,
                "出边 " + edgeKey + " 的条件组没有任何子句");
        }
    }
}
