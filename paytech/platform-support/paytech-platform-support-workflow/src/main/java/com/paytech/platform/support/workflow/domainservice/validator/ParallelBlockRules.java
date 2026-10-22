package com.paytech.platform.support.workflow.domainservice.validator;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.core.enums.WorkflowProblemCode;
import com.paytech.platform.support.workflow.domain.graph.WorkflowEdge;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;

/**
 * 校验并行块（技术方案 3.4 第 4 项）：分叉与汇合一一成对；每条分支自成区域，不跨分支、不连出块外、
 * 恰好以一条连线进入汇合；不允许嵌套并行（Q8）；结束节点不能位于块内。只在无回环时执行。
 */
final class ParallelBlockRules {

    private final WorkflowGraph graph;

    private final GraphTopology topology;

    private final ProblemCollector problems;

    private final Map<String, Integer> joinPairings = new HashMap<>();

    /**
     * 绑定被校验的流程图。
     *
     * @param graph 流程图
     * @param topology 拓扑视图
     * @param problems 问题收集
     */
    ParallelBlockRules(WorkflowGraph graph, GraphTopology topology, ProblemCollector problems) {
        this.graph = graph;
        this.topology = topology;
        this.problems = problems;
    }

    /** 校验全部并行分叉，再检查每个汇合恰好与一个分叉成对。 */
    void check() {
        nodesOf(WorkflowNodeType.PARALLEL_SPLIT).forEach(this::checkSplit);
        for (WorkflowNode join : nodesOf(WorkflowNodeType.PARALLEL_JOIN)) {
            int pairings = joinPairings.getOrDefault(join.key(), 0);
            if (pairings != 1) {
                problems.node(WorkflowProblemCode.PARALLEL_UNPAIRED, join.key(),
                    "并行汇合必须恰好与一个并行分叉成对，当前与 " + pairings + " 个分叉成对");
            }
        }
    }

    /**
     * 校验以该分叉开始的并行块。
     *
     * @param split 并行分叉
     */
    private void checkSplit(WorkflowNode split) {
        List<Branch> branches = topology.validOutgoing(split.key()).stream().map(this::explore).toList();
        if (!reportNestingAndEnds(split, branches)) {
            return;
        }
        Set<String> joins = new LinkedHashSet<>();
        branches.forEach(branch -> joins.addAll(branch.joins()));
        boolean everyBranchJoins = branches.stream().noneMatch(branch -> branch.joinEntries().isEmpty());
        if (joins.size() != 1 || !everyBranchJoins) {
            problems.node(WorkflowProblemCode.PARALLEL_UNPAIRED, split.key(),
                "并行分叉的全部分支必须汇入同一个并行汇合，当前汇入 " + joins);
            return;
        }
        String join = joins.iterator().next();
        joinPairings.merge(join, 1, Integer::sum);
        checkBranchShapes(split, branches);
        checkBlockBoundary(split, join, branches);
    }

    /**
     * 报告嵌套并行与块内结束节点。
     *
     * @param split 并行分叉
     * @param branches 各分支
     * @return 没有嵌套时返回 true，可以继续成对校验
     */
    private boolean reportNestingAndEnds(WorkflowNode split, List<Branch> branches) {
        Set<String> nested = new LinkedHashSet<>();
        Set<String> ends = new LinkedHashSet<>();
        branches.forEach(branch -> {
            nested.addAll(branch.nestedSplits());
            ends.addAll(branch.ends());
        });
        nested.forEach(inner -> problems.node(WorkflowProblemCode.PARALLEL_NESTED, inner,
            "不允许嵌套并行：该分叉位于并行分叉 " + split.key() + " 的分支内，可改为串联多个并行块"));
        ends.forEach(end -> problems.node(WorkflowProblemCode.PARALLEL_END_INSIDE, end,
            "结束节点不能位于并行分叉 " + split.key() + " 的分支内"));
        return nested.isEmpty();
    }

    /**
     * 校验每条分支非空、恰好一条连线进入汇合，且分支之间没有公共节点。
     *
     * @param split 并行分叉
     * @param branches 各分支
     */
    private void checkBranchShapes(WorkflowNode split, List<Branch> branches) {
        Map<String, String> owner = new HashMap<>();
        for (Branch branch : branches) {
            if (branch.region().isEmpty()) {
                problems.edge(WorkflowProblemCode.PARALLEL_BRANCH_EMPTY, split.key(), branch.edgeKey(),
                    "并行分支 " + branch.edgeKey() + " 中没有任何节点");
            }
            for (WorkflowEdge entry : branch.joinEntries().subList(1, branch.joinEntries().size())) {
                problems.edge(WorkflowProblemCode.PARALLEL_BRANCH_JOIN_ENTRY, entry.from(), entry.key(),
                    "并行分支 " + branch.edgeKey() + " 只能以一条连线进入汇合，分支内的排他路径须先合并");
            }
            for (String node : branch.region()) {
                String previous = owner.putIfAbsent(node, branch.edgeKey());
                if (previous != null) {
                    problems.node(WorkflowProblemCode.PARALLEL_BRANCH_CROSS, node,
                        "节点同时属于并行分支 " + previous + " 与 " + branch.edgeKey());
                }
            }
        }
    }

    /**
     * 校验块的边界：分支节点只从本分支或分叉进入，汇合只从本块分支进入。
     *
     * @param split 并行分叉
     * @param join 成对的并行汇合
     * @param branches 各分支
     */
    private void checkBlockBoundary(WorkflowNode split, String join, List<Branch> branches) {
        Set<String> blockNodes = new LinkedHashSet<>();
        for (Branch branch : branches) {
            blockNodes.addAll(branch.region());
            for (String node : branch.region()) {
                topology.validIncoming(node).stream()
                    .filter(edge -> !edge.from().equals(split.key()) && !branch.region().contains(edge.from()))
                    .forEach(edge -> problems.edge(WorkflowProblemCode.PARALLEL_BRANCH_CROSS, node, edge.key(),
                        "连线 " + edge.key() + " 从分支 " + branch.edgeKey() + " 之外连入该分支"));
            }
        }
        topology.validIncoming(join).stream()
            .filter(edge -> !edge.from().equals(split.key()) && !blockNodes.contains(edge.from()))
            .forEach(edge -> problems.edge(WorkflowProblemCode.PARALLEL_BRANCH_CROSS, join, edge.key(),
                "连线 " + edge.key() + " 从并行块之外连入汇合 " + join));
    }

    /**
     * 沿一条分叉出边探索分支区域，遇到汇合、结束或其他分叉即停止。
     *
     * @param edge 分叉出边
     * @return 分支区域与边界
     */
    private Branch explore(WorkflowEdge edge) {
        Branch branch = new Branch(edge.key(), new LinkedHashSet<>(), new ArrayList<>(), new LinkedHashSet<>(),
            new LinkedHashSet<>(), new LinkedHashSet<>());
        Deque<String> queue = new ArrayDeque<>();
        enter(edge, branch, queue);
        while (!queue.isEmpty()) {
            for (WorkflowEdge next : topology.validOutgoing(queue.poll())) {
                enter(next, branch, queue);
            }
        }
        return branch;
    }

    /**
     * 处理分支内的一条连线：按终点类型记录边界或纳入区域。
     *
     * @param edge 连线
     * @param branch 分支
     * @param queue 待探索节点
     */
    private void enter(WorkflowEdge edge, Branch branch, Deque<String> queue) {
        WorkflowNodeType type = graph.node(edge.to()).map(WorkflowNode::type).orElseThrow();
        switch (type) {
            case PARALLEL_JOIN -> {
                branch.joinEntries().add(edge);
                branch.joins().add(edge.to());
            }
            case END -> branch.ends().add(edge.to());
            case PARALLEL_SPLIT -> branch.nestedSplits().add(edge.to());
            default -> {
                if (branch.region().add(edge.to())) {
                    queue.add(edge.to());
                }
            }
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

    /**
     * 一条并行分支的探索结果。
     *
     * @param edgeKey 分叉出边 key，即运行时的 branch_key
     * @param region 分支内的节点
     * @param joinEntries 进入汇合的连线
     * @param joins 进入的汇合节点
     * @param ends 分支内遇到的结束节点
     * @param nestedSplits 分支内遇到的并行分叉
     */
    private record Branch(String edgeKey, Set<String> region, List<WorkflowEdge> joinEntries, Set<String> joins,
        Set<String> ends, Set<String> nestedSplits) {
    }
}
