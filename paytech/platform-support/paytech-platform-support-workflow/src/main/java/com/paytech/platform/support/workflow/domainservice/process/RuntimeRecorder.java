package com.paytech.platform.support.workflow.domainservice.process;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;
import java.util.function.LongSupplier;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.ApprovalStatus;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;
import com.paytech.platform.support.workflow.core.enums.WorkflowNodeType;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.WorkflowRuntimeChange.NodeRunUpdate;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.graph.NodeAction;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.runtime.NavigationPlan;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunState;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunStatusChange;

/**
 * 运行引擎的记录器：把导航计划与人工操作落到上下文的数据对象上，同时登记变化与状态事件。
 *
 * <p>只做内存修改；保存由运行服务按 {@link com.paytech.platform.support.workflow.domain.WorkflowRuntimeChange} 完成。</p>
 */
final class RuntimeRecorder {

    private static final Set<NodeRunStatus> CLOSED = EnumSet.of(NodeRunStatus.PASSED, NodeRunStatus.RETURNED,
        NodeRunStatus.REJECTED, NodeRunStatus.CANCELLED);

    private final WorkflowInstanceContext context;

    private final LongSupplier ids;

    private final List<RuntimeEvent> events = new ArrayList<>();

    /**
     * 绑定上下文与 ID 生成器。
     *
     * @param context 运行上下文
     * @param ids 新记录的 ID 生成器
     */
    RuntimeRecorder(WorkflowInstanceContext context, LongSupplier ids) {
        this.context = context;
        this.ids = ids;
    }

    /** 本次登记的状态事件，按发生顺序。 */
    List<RuntimeEvent> events() {
        return events;
    }

    /** 新记录的 ID 生成器。 */
    LongSupplier ids() {
        return ids;
    }

    /**
     * 登记一个状态事件。
     *
     * @param event 事件
     */
    void event(RuntimeEvent event) {
        events.add(event);
    }

    /**
     * 应用导航计划：先改已有节点运行，再新建节点运行并为新激活的 TASK 建动作运行或审批行；到达 END 时完成实例。
     *
     * @param plan 导航计划
     * @param actorId 触发推进的账号
     */
    void apply(NavigationPlan plan, Long actorId) {
        plan.statusChanges().forEach(change -> changeNodeRun(change, actorId));
        plan.createdNodeRuns().forEach(state -> createNodeRun(state, actorId));
        if (plan.instanceCompleted()) {
            context.getInstance().setStatus(WorkflowInstanceStatus.COMPLETED);
            context.getInstance().setEndTime(context.getNow());
            events.add(RuntimeEvent.instance(NotificationTrigger.COMPLETED, WorkflowInstanceStatus.RUNNING.getCode(),
                WorkflowInstanceStatus.COMPLETED.getCode(), actorId, null));
        }
    }

    /**
     * 改变一个已有节点运行的状态；TASK 离开进行状态时未完成的动作取消。
     *
     * @param change 状态变化
     * @param actorId 触发推进的账号
     */
    void changeNodeRun(NodeRunStatusChange change, Long actorId) {
        WorkflowNodeRun run = context.nodeRun(change.nodeRunId())
            .orElseThrow(() -> new ErrorException("实例中不存在节点运行：" + change.nodeRunId()));
        setStatus(run, change.toStatus());
        if (run.getNodeType() == WorkflowNodeType.TASK && CLOSED.contains(change.toStatus())) {
            cancelOpenActions(run);
        }
        if (run.getNodeType() == WorkflowNodeType.TASK && change.toStatus() == NodeRunStatus.PASSED) {
            events.add(RuntimeEvent.node(NotificationTrigger.PASSED, run, change.fromStatus(), change.toStatus(),
                actorId, null));
        }
    }

    /**
     * 设置节点运行状态并登记变化。
     *
     * @param run 节点运行
     * @param to 新状态
     */
    void setStatus(WorkflowNodeRun run, NodeRunStatus to) {
        NodeRunStatus from = run.getStatus();
        run.setStatus(to);
        run.setCompletedTime(CLOSED.contains(to) ? context.getNow() : null);
        run.setUpdateTime(context.getNow());
        if (context.getChange().getNewNodeRuns().stream().noneMatch(created -> created == run)) {
            context.getChange().getNodeRunUpdates().add(new NodeRunUpdate(run, from, to));
        }
    }

    /**
     * 节点离开进行状态时，取消仍未完成（PENDING、UNVERIFIED）的动作运行。
     *
     * @param run 节点运行
     */
    void cancelOpenActions(WorkflowNodeRun run) {
        context.actionRunsOf(run.getNodeRunId()).stream()
            .filter(action -> action.getStatus() == ActionRunStatus.PENDING
                || action.getStatus() == ActionRunStatus.UNVERIFIED)
            .forEach(action -> {
                action.setStatus(ActionRunStatus.CANCELLED);
                action.setUpdateTime(context.getNow());
                context.getChange().updated(action);
            });
    }

    /**
     * 新建一条节点运行；TASK 新一轮同时建动作运行（ACTIVE）或审批行（APPROVING）。
     *
     * @param state 导航给出的新运行
     * @param actorId 触发推进的账号
     */
    void createNodeRun(NodeRunState state, Long actorId) {
        WorkflowNodeRun run = new WorkflowNodeRun();
        run.setNodeRunId(state.nodeRunId());
        run.setInstanceId(context.getInstance().getInstanceId());
        run.setNodeKey(state.nodeKey());
        run.setNodeType(state.nodeType());
        run.setRoundNo(state.roundNo());
        run.setBranchKey(state.branchKey());
        run.setPreviousNodeRunId(state.previousNodeRunId());
        run.setStatus(state.status());
        run.setSelectedEdgeKey(state.selectedEdgeKey());
        run.setActivatedTime(context.getNow());
        run.setCompletedTime(CLOSED.contains(state.status()) ? context.getNow() : null);
        run.setVersion(1);
        run.setCreateTime(context.getNow());
        run.setUpdateTime(context.getNow());
        context.getNodeRuns().add(run);
        context.getChange().getNewNodeRuns().add(run);
        if (state.nodeType() == WorkflowNodeType.TASK) {
            activateTask(run, actorId);
        }
    }

    /**
     * TASK 新一轮：有动作建动作运行，纯审批节点直接开放审批。
     *
     * @param run 新节点运行
     * @param actorId 触发推进的账号
     */
    void activateTask(WorkflowNodeRun run, Long actorId) {
        WorkflowNode node = context.node(run.getNodeKey());
        events.add(RuntimeEvent.node(NotificationTrigger.ACTIVATED, run, (NodeRunStatus) null, run.getStatus(), actorId,
            null));
        node.actions().forEach(action -> createActionRun(run, action));
        if (run.getStatus() == NodeRunStatus.APPROVING) {
            createApprovals(run, actorId, null);
        }
    }

    /**
     * 为节点运行建一个动作运行（PENDING）。
     *
     * @param run 节点运行
     * @param action 动作定义
     */
    void createActionRun(WorkflowNodeRun run, NodeAction action) {
        ActionTemplateReference template = context.getTemplates().get(action.templateId());
        if (template == null) {
            throw new ErrorException("版本快照缺少动作模板：" + action.templateId());
        }
        WorkflowActionRun row = new WorkflowActionRun();
        row.setActionRunId(ids.getAsLong());
        row.setInstanceId(run.getInstanceId());
        row.setNodeRunId(run.getNodeRunId());
        row.setActionKey(action.key());
        row.setActionTemplateId(action.templateId());
        row.setActionType(template.actionType());
        row.setRequired(action.required());
        row.setAssigneeId(context.executorFor(action.key()));
        row.setStatus(ActionRunStatus.PENDING);
        row.setVersion(1);
        row.setCreateTime(context.getNow());
        row.setUpdateTime(context.getNow());
        context.getActionRuns().add(row);
        context.getChange().getNewActionRuns().add(row);
    }

    /**
     * 开放审批：为每位审批人建一行 PENDING，并登记进入待审批事件。
     *
     * @param run 节点运行（已为 APPROVING）
     * @param actorId 触发推进的账号
     * @param from 进入审批前的状态；新一轮直接进入审批时为 null
     */
    void createApprovals(WorkflowNodeRun run, Long actorId, NodeRunStatus from) {
        for (Long approverId : context.approversFor(run.getNodeKey())) {
            WorkflowApproval row = new WorkflowApproval();
            row.setApprovalId(ids.getAsLong());
            row.setInstanceId(run.getInstanceId());
            row.setNodeRunId(run.getNodeRunId());
            row.setApproverId(approverId);
            row.setStatus(ApprovalStatus.PENDING);
            row.setVersion(1);
            row.setCreateTime(context.getNow());
            row.setUpdateTime(context.getNow());
            context.getApprovals().add(row);
            context.getChange().getNewApprovals().add(row);
        }
        events.add(RuntimeEvent.node(NotificationTrigger.APPROVAL_OPENED, run, from, NodeRunStatus.APPROVING, actorId,
            null));
    }

    /**
     * 撤回节点运行中除给定行外的待决定审批。
     *
     * @param nodeRunId 节点运行
     * @param except 本次做出决定的审批行
     */
    void withdrawPending(Long nodeRunId, WorkflowApproval except) {
        context.approvalsOf(nodeRunId).stream()
            .filter(row -> row != except && row.getStatus() == ApprovalStatus.PENDING)
            .forEach(row -> {
                row.setStatus(ApprovalStatus.WITHDRAWN);
                row.setDecidedTime(context.getNow());
                row.setUpdateTime(context.getNow());
                context.getChange().updated(row);
            });
    }
}
