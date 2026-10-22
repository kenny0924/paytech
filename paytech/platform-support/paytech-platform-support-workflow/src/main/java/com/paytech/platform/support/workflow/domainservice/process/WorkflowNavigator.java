package com.paytech.platform.support.workflow.domainservice.process;

import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Deque;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.LongSupplier;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.RejectionKind;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.domain.graph.WorkflowEdge;
import com.paytech.platform.support.workflow.domain.graph.WorkflowGraph;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.runtime.NavigationPlan;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunState;
import com.paytech.platform.support.workflow.domain.runtime.RejectOption;
import com.paytech.platform.support.workflow.domain.variable.WorkflowVariables;

/**
 * 流程导航（技术方案第 4 节）：给定绑定版本的流程图与实例的节点运行，计算下一步。纯内存计算，不访问数据库。
 *
 * <ul>
 * <li>推进：TASK 新建一轮（有动作为 ACTIVE，否则直接 APPROVING）；排他网关按条件选边并写 PASSED；并行分叉写 PASSED
 * 并激活每条出边，分支内节点的 branch_key 为分叉出边 key；汇合复用 WAITING 运行，每条入边来源节点的最新一轮都 PASSED
 * 时通过并清空 branch_key；END 写 PASSED 并完成实例。</li>
 * <li>退回修改（B5a）：只对有动作的节点开放，本节点 RETURNED 并开新一轮，不触及上一节点。</li>
 * <li>驳回（B5）：沿 previous_node_run_id 跳过排他网关回到最近的 TASK 并开新一轮；越过并行分叉即分支第一个节点、到达
 * START 即流程第一个任务节点，均不能驳回；到达汇合即汇合后的第一个节点，须指定目标分支，汇合回到 WAITING。</li>
 * </ul>
 *
 * <p>业务前提不满足（状态不对、不能驳回等）抛 {@link BizCheckFailException}；运行与流程图不一致抛 {@link ErrorException}。
 * 返回的计划由调用方在同一本地事务内以条件更新落库。</p>
 */
public final class WorkflowNavigator {

    private static final Set<NodeRunStatus> OPEN = EnumSet.of(NodeRunStatus.ACTIVE, NodeRunStatus.APPROVING,
        NodeRunStatus.WAITING);

    private final WorkflowConditionEvaluator evaluator = new WorkflowConditionEvaluator();

    /**
     * 发起实例：写 START 运行（PASSED）并推进到第一批 TASK。
     *
     * @param graph 绑定版本的流程图
     * @param variables 发起时的实例变量（启动值）
     * @param nodeRunIds 新节点运行的 ID 生成器
     * @return 导航结果
     */
    public NavigationPlan start(WorkflowGraph graph, WorkflowVariables variables, LongSupplier nodeRunIds) {
        NavigationWorkspace workspace = new NavigationWorkspace(graph, List.of(), nodeRunIds);
        WorkflowNode start = graph.nodes().stream().filter(node -> node.type() == WorkflowNodeType.START).findFirst()
            .orElseThrow(() -> new ErrorException("流程版本没有开始节点"));
        NodeRunState startRun = workspace.create(start, null, null, NodeRunStatus.PASSED, null);
        advance(workspace, startRun, variables);
        return workspace.plan();
    }

    /**
     * 任务节点放行：节点运行改为 PASSED 并推进。何时放行（必选动作全部验证、ANY / ALL 审批）由调用方判断。
     *
     * @param graph 绑定版本的流程图
     * @param runs 实例全部节点运行
     * @param nodeRunId 放行的 TASK 运行，须为 ACTIVE 或 APPROVING
     * @param variables 当前实例变量（含本节点刚验证通过的输出）
     * @param nodeRunIds 新节点运行的 ID 生成器
     * @return 导航结果
     */
    public NavigationPlan completeTask(WorkflowGraph graph, Collection<NodeRunState> runs, Long nodeRunId,
        WorkflowVariables variables, LongSupplier nodeRunIds) {
        NavigationWorkspace workspace = new NavigationWorkspace(graph, runs, nodeRunIds);
        NodeRunState task = requireTask(workspace, nodeRunId, EnumSet.of(NodeRunStatus.ACTIVE, NodeRunStatus.APPROVING));
        advance(workspace, workspace.change(task.nodeRunId(), NodeRunStatus.PASSED), variables);
        return workspace.plan();
    }

    /**
     * 审批退回修改：本节点 RETURNED，同一节点开新一轮 ACTIVE（沿用原来源与分支），不触及上一节点。
     *
     * @param graph 绑定版本的流程图
     * @param runs 实例全部节点运行
     * @param nodeRunId 待审批的 TASK 运行
     * @param nodeRunIds 新节点运行的 ID 生成器
     * @return 导航结果
     */
    public NavigationPlan returnForRevision(WorkflowGraph graph, Collection<NodeRunState> runs, Long nodeRunId,
        LongSupplier nodeRunIds) {
        NavigationWorkspace workspace = new NavigationWorkspace(graph, runs, nodeRunIds);
        NodeRunState task = requireTask(workspace, nodeRunId, EnumSet.of(NodeRunStatus.APPROVING));
        WorkflowNode node = workspace.node(task.nodeKey());
        if (!node.hasActions()) {
            throw new BizCheckFailException("纯审批节点没有可修改的提交内容，不能退回修改：" + node.key());
        }
        workspace.change(task.nodeRunId(), NodeRunStatus.RETURNED);
        workspace.create(node, task.branchKey(), task.previousNodeRunId(), NodeRunStatus.ACTIVE, null);
        return workspace.plan();
    }

    /**
     * 计算当前 TASK 运行能否驳回及驳回目标。
     *
     * @param graph 绑定版本的流程图
     * @param runs 实例全部节点运行
     * @param nodeRunId TASK 运行
     * @return 驳回选项
     */
    public RejectOption rejectOption(WorkflowGraph graph, Collection<NodeRunState> runs, Long nodeRunId) {
        NavigationWorkspace workspace = new NavigationWorkspace(graph, runs, null);
        NodeRunState task = requireTask(workspace, nodeRunId, EnumSet.allOf(NodeRunStatus.class));
        RejectTrace trace = trace(workspace, task);
        return new RejectOption(trace.kind(), trace.candidates().stream().map(NodeRunState::nodeKey).toList());
    }

    /**
     * 审批驳回：本节点 REJECTED，回到实际走过的上一个 TASK 并开新一轮；汇合后的第一个节点须指定目标分支，汇合回到 WAITING。
     *
     * @param graph 绑定版本的流程图
     * @param runs 实例全部节点运行
     * @param nodeRunId 待审批的 TASK 运行
     * @param rejectTargetNodeKey 汇合后驳回时指定的分支末 TASK 节点；其他情形可为 null，填写时须与唯一目标一致
     * @param nodeRunIds 新节点运行的 ID 生成器
     * @return 导航结果
     */
    public NavigationPlan reject(WorkflowGraph graph, Collection<NodeRunState> runs, Long nodeRunId,
        String rejectTargetNodeKey, LongSupplier nodeRunIds) {
        NavigationWorkspace workspace = new NavigationWorkspace(graph, runs, nodeRunIds);
        NodeRunState task = requireTask(workspace, nodeRunId, EnumSet.of(NodeRunStatus.APPROVING));
        RejectTrace trace = trace(workspace, task);
        NodeRunState target = chooseTarget(task, trace, rejectTargetNodeKey);
        workspace.change(task.nodeRunId(), NodeRunStatus.REJECTED);
        if (trace.kind() == RejectionKind.CHOOSE_BRANCH) {
            workspace.change(trace.join().nodeRunId(), NodeRunStatus.WAITING);
        }
        WorkflowNode targetNode = workspace.node(target.nodeKey());
        workspace.create(targetNode, target.branchKey(), target.previousNodeRunId(), initialTaskStatus(targetNode),
            null);
        return workspace.plan();
    }

    /**
     * 终止实例：仍在进行（ACTIVE、APPROVING、WAITING）的节点运行全部改为 CANCELLED。
     *
     * @param graph 绑定版本的流程图
     * @param runs 实例全部节点运行
     * @return 导航结果
     */
    public NavigationPlan cancelOpenRuns(WorkflowGraph graph, Collection<NodeRunState> runs) {
        NavigationWorkspace workspace = new NavigationWorkspace(graph, runs, null);
        List<Long> open = workspace.runs().stream().filter(run -> OPEN.contains(run.status()))
            .map(NodeRunState::nodeRunId).toList();
        open.forEach(id -> workspace.change(id, NodeRunStatus.CANCELLED));
        return workspace.plan();
    }

    /**
     * 从已通过的运行出发推进，直到每条路径停在 TASK、等待中的汇合或 END。
     *
     * @param workspace 工作区
     * @param source 已通过的运行
     * @param variables 实例变量
     */
    private void advance(NavigationWorkspace workspace, NodeRunState source, WorkflowVariables variables) {
        Deque<Step> queue = new ArrayDeque<>();
        enqueueOutgoing(workspace, source, queue);
        int guard = workspace.graph().nodes().size() * 2 + 2;
        while (!queue.isEmpty()) {
            if (guard-- < 0) {
                throw new ErrorException("流程推进步数超出节点规模，流程图可能存在回环");
            }
            enter(workspace, queue.poll(), variables, queue);
        }
    }

    /**
     * 沿一条连线进入下一个节点。
     *
     * @param workspace 工作区
     * @param step 来源运行与连线
     * @param variables 实例变量
     * @param queue 待推进步骤
     */
    private void enter(NavigationWorkspace workspace, Step step, WorkflowVariables variables, Deque<Step> queue) {
        WorkflowNode node = workspace.node(step.edge().to());
        NodeRunState source = step.source();
        String branchKey = source.nodeType() == WorkflowNodeType.PARALLEL_SPLIT ? step.edge().key() : source.branchKey();
        switch (node.type()) {
            case TASK -> workspace.create(node, branchKey, source.nodeRunId(), initialTaskStatus(node), null);
            case EXCLUSIVE -> {
                String selected = evaluator.selectEdge(node, variables);
                WorkflowEdge edge = workspace.graph().edge(selected)
                    .orElseThrow(() -> new ErrorException("排他网关 " + node.key() + " 选中的出边不存在：" + selected));
                NodeRunState gateway = workspace.create(node, branchKey, source.nodeRunId(), NodeRunStatus.PASSED,
                    selected);
                queue.add(new Step(gateway, edge));
            }
            case PARALLEL_SPLIT -> enqueueOutgoing(workspace,
                workspace.create(node, branchKey, source.nodeRunId(), NodeRunStatus.PASSED, null), queue);
            case PARALLEL_JOIN -> arriveAtJoin(workspace, node, queue);
            case END -> {
                workspace.create(node, branchKey, source.nodeRunId(), NodeRunStatus.PASSED, null);
                workspace.complete();
            }
            default -> throw new ErrorException("连线不能进入开始节点：" + node.key());
        }
    }

    /**
     * 一条分支到达汇合：复用或新建 WAITING 运行；全部入边来源的最新一轮都 PASSED 时通过并继续推进。
     *
     * @param workspace 工作区
     * @param join 汇合节点
     * @param queue 待推进步骤
     */
    private void arriveAtJoin(NavigationWorkspace workspace, WorkflowNode join, Deque<Step> queue) {
        NodeRunState run = workspace.waiting(join.key())
            .orElseGet(() -> workspace.create(join, null, null, NodeRunStatus.WAITING, null));
        boolean satisfied = workspace.graph().incoming(join.key()).stream()
            .allMatch(edge -> workspace.latest(edge.from()).map(latest -> latest.status() == NodeRunStatus.PASSED)
                .orElse(false));
        if (satisfied) {
            enqueueOutgoing(workspace, workspace.change(run.nodeRunId(), NodeRunStatus.PASSED), queue);
        }
    }

    /**
     * 把运行节点的全部出边加入待推进步骤，保持连线配置顺序。
     *
     * @param workspace 工作区
     * @param run 已通过的运行
     * @param queue 待推进步骤
     */
    private void enqueueOutgoing(NavigationWorkspace workspace, NodeRunState run, Deque<Step> queue) {
        workspace.graph().outgoing(run.nodeKey()).forEach(edge -> queue.add(new Step(run, edge)));
    }

    /**
     * 沿 previous_node_run_id 跳过排他网关，确定驳回的处理方式与候选目标。
     *
     * @param workspace 工作区
     * @param task 驳回的 TASK 运行
     * @return 追溯结果
     */
    private RejectTrace trace(NavigationWorkspace workspace, NodeRunState task) {
        NodeRunState current = previousOf(workspace, task);
        while (current.nodeType() == WorkflowNodeType.EXCLUSIVE) {
            current = previousOf(workspace, current);
        }
        return switch (current.nodeType()) {
            case TASK -> new RejectTrace(RejectionKind.PREVIOUS_TASK, List.of(current), null);
            case START -> new RejectTrace(RejectionKind.FIRST_TASK, List.of(), null);
            case PARALLEL_SPLIT -> new RejectTrace(RejectionKind.BRANCH_FIRST_NODE, List.of(), null);
            case PARALLEL_JOIN -> new RejectTrace(RejectionKind.CHOOSE_BRANCH, branchTargets(workspace, current),
                current);
            default -> throw new ErrorException("节点运行的来源不合法：" + current.nodeKey());
        };
    }

    /**
     * 汇合后驳回的候选：每条入边来源的最新一轮，跳过排他网关后的最近 TASK，即各分支最后一个 TASK。
     *
     * @param workspace 工作区
     * @param join 汇合运行
     * @return 候选 TASK 运行，按汇合入边顺序
     */
    private List<NodeRunState> branchTargets(NavigationWorkspace workspace, NodeRunState join) {
        Set<NodeRunState> targets = new LinkedHashSet<>();
        for (WorkflowEdge edge : workspace.graph().incoming(join.nodeKey())) {
            Optional<NodeRunState> current = workspace.latest(edge.from());
            while (current.isPresent() && current.get().nodeType() == WorkflowNodeType.EXCLUSIVE) {
                current = workspace.find(current.get().previousNodeRunId());
            }
            current.filter(run -> run.nodeType() == WorkflowNodeType.TASK).ifPresent(targets::add);
        }
        return List.copyOf(targets);
    }

    /**
     * 按驳回方式确定目标运行；不能驳回或目标不符时拒绝。
     *
     * @param task 驳回的 TASK 运行
     * @param trace 追溯结果
     * @param rejectTargetNodeKey 审批人指定的目标节点
     * @return 目标 TASK 运行（被退回的那一轮）
     */
    private NodeRunState chooseTarget(NodeRunState task, RejectTrace trace, String rejectTargetNodeKey) {
        switch (trace.kind()) {
            case FIRST_TASK -> throw new BizCheckFailException(
                "流程第一个任务节点没有上一个任务节点，不能驳回；发起人可以终止实例：" + task.nodeKey());
            case BRANCH_FIRST_NODE -> throw new BizCheckFailException(
                "并行分支的第一个节点不能驳回，退回会牵连其他分支：" + task.nodeKey());
            case PREVIOUS_TASK -> {
                NodeRunState target = trace.candidates().get(0);
                if (rejectTargetNodeKey != null && !rejectTargetNodeKey.equals(target.nodeKey())) {
                    throw new BizCheckFailException("驳回只能退回实际走过的上一个任务节点：" + target.nodeKey());
                }
                return target;
            }
            default -> {
                return trace.candidates().stream().filter(run -> run.nodeKey().equals(rejectTargetNodeKey)).findFirst()
                    .orElseThrow(() -> new BizCheckFailException("汇合后的第一个节点驳回时必须指定目标分支的最后一个任务节点，可选："
                        + trace.candidates().stream().map(NodeRunState::nodeKey).toList()));
            }
        }
    }

    /**
     * 取运行的实际来源；缺失属于数据不一致。
     *
     * @param workspace 工作区
     * @param run 节点运行
     * @return 来源运行
     */
    private NodeRunState previousOf(NavigationWorkspace workspace, NodeRunState run) {
        return workspace.find(run.previousNodeRunId())
            .orElseThrow(() -> new ErrorException("节点运行缺少来源，无法定位上一节点：" + run.nodeKey()));
    }

    /**
     * 校验运行是 TASK 且处于允许的状态。
     *
     * @param workspace 工作区
     * @param nodeRunId 节点运行 ID
     * @param allowed 允许的状态
     * @return TASK 运行
     */
    private NodeRunState requireTask(NavigationWorkspace workspace, Long nodeRunId, Set<NodeRunStatus> allowed) {
        NodeRunState run = workspace.run(nodeRunId);
        if (run.nodeType() != WorkflowNodeType.TASK) {
            throw new ErrorException("只有任务节点运行可以放行、退回修改或驳回：" + run.nodeKey());
        }
        if (!allowed.contains(run.status())) {
            throw new BizCheckFailException("节点 " + run.nodeKey() + " 当前状态为 " + run.status().getCode() + "，不能执行该操作");
        }
        return run;
    }

    /**
     * TASK 新一轮的初始状态：有动作为 ACTIVE，纯审批节点直接 APPROVING。
     *
     * @param node 任务节点
     * @return 初始状态
     */
    private NodeRunStatus initialTaskStatus(WorkflowNode node) {
        return node.hasActions() ? NodeRunStatus.ACTIVE : NodeRunStatus.APPROVING;
    }

    /**
     * 待推进的一步：从已通过的来源运行沿连线进入下一节点。
     *
     * @param source 来源运行
     * @param edge 连线
     */
    private record Step(NodeRunState source, WorkflowEdge edge) {
    }

    /**
     * 驳回追溯结果。
     *
     * @param kind 处理方式
     * @param candidates 候选目标 TASK 运行
     * @param join CHOOSE_BRANCH 时追溯到的汇合运行；其他为 null
     */
    private record RejectTrace(RejectionKind kind, List<NodeRunState> candidates, NodeRunState join) {
    }
}
