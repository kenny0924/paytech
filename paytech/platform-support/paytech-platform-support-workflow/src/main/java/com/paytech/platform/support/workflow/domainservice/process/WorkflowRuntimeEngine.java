package com.paytech.platform.support.workflow.domainservice.process;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowTransfer;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.ApprovalDecision;
import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.ApprovalStatus;
import com.paytech.platform.support.workflow.core.enums.DataQueryCheckResult;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;
import com.paytech.platform.support.workflow.core.enums.TransferTargetType;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.runtime.DataQueryCheckOutcome;
import com.paytech.platform.support.workflow.domain.runtime.NavigationPlan;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunState;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;
import com.paytech.platform.support.workflow.domain.variable.WorkflowVariables;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ObjectNode;

/**
 * 运行引擎（技术方案第 4 节）：在已加载的实例上下文上执行发起、完成动作、系统完成 DATA_QUERY、审批决定、转交与终止，
 * 结合 {@link WorkflowNavigator} 推进，并按规则登记通知回调。纯内存计算，不访问数据库或外部服务。
 *
 * <p>人员与状态前提不满足抛 {@link BizCheckFailException}；节点已进入审批后改必选动作、同意时必选动作未全部完成抛
 * {@link ConcurrentRequestException}（409）；运行记录与版本不一致抛 {@link ErrorException}。
 * 写时并发由保存时的版本与旧状态条件最终保护。</p>
 */
public final class WorkflowRuntimeEngine {

    private final WorkflowNavigator navigator = new WorkflowNavigator();

    private final ActionOutputChecker checker = new ActionOutputChecker();

    private final CallbackPlanner planner = new CallbackPlanner();

    private final WorkflowRuntimeJson json = new WorkflowRuntimeJson();

    /**
     * 发起：写 START 运行并推进到第一批任务节点，登记 STARTED 与沿途事件。
     *
     * @param context 新实例的上下文（实例已构造、尚无运行记录）
     * @param ids ID 生成器
     */
    public void start(WorkflowInstanceContext context, LongSupplier ids) {
        RuntimeRecorder recorder = new RuntimeRecorder(context, ids);
        Long starter = context.getInstance().getStartedBy();
        recorder.event(RuntimeEvent.instance(NotificationTrigger.STARTED, null, WorkflowInstanceStatus.RUNNING.getCode(),
            starter, null));
        recorder.apply(navigator.start(context.getGraph(), variables(context), ids), starter);
        finish(context, recorder);
    }

    /**
     * 执行人完成动作（需求 E2 / E4）：只检查必填与技术保护；不合格返回字段问题且不改任何状态。合格即 COMPLETED，
     * 节点仍在执行中且必选动作全部完成时开放审批，没有审批人则直接放行。
     * <li>必选动作在节点进入审批前可再次完成（修改提交内容）；进入审批后锁定。</li>
     * <li>可选动作在节点放行前（执行中或待审批）均可完成，不触发放行。</li>
     *
     * @param context 实例上下文
     * @param actionRunId 动作运行
     * @param output 提交内容
     * @param actorId 执行人
     * @param files 提交内容引用文件的事实；归属已由调用方确认
     * @param ids ID 生成器
     * @return 请求体字段路径到原因；空表示已完成
     */
    public Map<String, String> completeAction(WorkflowInstanceContext context, long actionRunId, JsonNode output,
        long actorId, Map<Long, AttachmentFact> files, LongSupplier ids) {
        requireRunning(context);
        WorkflowActionRun row = actionRun(context, actionRunId);
        WorkflowNodeRun nodeRun = nodeRun(context, row.getNodeRunId());
        requireCompletable(context, row, nodeRun, actorId);
        Map<String, String> errors = checker.check(context.getTemplates().get(row.getActionTemplateId()), output, files);
        if (!errors.isEmpty()) {
            return errors;
        }
        row.setOutputJson(json.write(output));
        row.setSubmittedBy(actorId);
        row.setSubmittedTime(context.getNow());
        row.setStatus(ActionRunStatus.COMPLETED);
        row.setUpdateTime(context.getNow());
        context.getChange().updated(row);
        RuntimeRecorder recorder = new RuntimeRecorder(context, ids);
        if (nodeRun.getStatus() == NodeRunStatus.ACTIVE && missingRequired(context, nodeRun).isEmpty()) {
            advanceAfterActions(context, nodeRun, recorder, actorId);
        }
        finish(context, recorder);
        return errors;
    }

    /**
     * 系统完成 DATA_QUERY 动作（REQ-05 R4）：查询已满足，写入规整后的输出（只含声明的输出），submitted_by 为空；
     * 节点仍在执行中且必选动作全部完成时开放审批，没有审批人则直接放行，推进触发人为空。
     * <li>调用方须在实例锁内、以本次加载的上下文确认动作版本未变；这里再要求动作仍待处理、节点未放行、实例运行中。</li>
     *
     * @param context 实例上下文
     * @param actionRunId 动作运行
     * @param outputs 规整后的输出
     * @param ids ID 生成器
     */
    public void completeDataQuery(WorkflowInstanceContext context, long actionRunId, ObjectNode outputs,
        LongSupplier ids) {
        requireRunning(context);
        WorkflowActionRun row = actionRun(context, actionRunId);
        WorkflowNodeRun nodeRun = nodeRun(context, row.getNodeRunId());
        if (!dataQueryCheckable(row, nodeRun)) {
            throw new BizCheckFailException("动作已完成、已取消或节点已结束，不能完成系统检查：" + row.getActionKey());
        }
        row.setOutputJson(json.write(outputs));
        row.setSubmittedBy(null);
        row.setSubmittedTime(context.getNow());
        row.setStatus(ActionRunStatus.COMPLETED);
        recordDataQueryCheck(row, DataQueryCheckOutcome.satisfied(outputs), context.getNow());
        context.getChange().updated(row);
        RuntimeRecorder recorder = new RuntimeRecorder(context, ids);
        if (nodeRun.getStatus() == NodeRunStatus.ACTIVE && missingRequired(context, nodeRun).isEmpty()) {
            advanceAfterActions(context, nodeRun, recorder, null);
        }
        finish(context, recorder);
    }

    /**
     * 在动作运行上记录一次 DATA_QUERY 检查：最近检查时间、结果与明细；查询成功执行（满足或未满足）时清零连续失败并刷新
     * 最近成功时间，失败时连续失败加一。只改内存对象，保存由调用方按动作版本条件更新。
     *
     * @param row 动作运行
     * @param outcome 检查结果
     * @param now 本次时间
     */
    public void recordDataQueryCheck(WorkflowActionRun row, DataQueryCheckOutcome outcome, Instant now) {
        row.setLastCheckTime(now);
        row.setCheckResult(outcome.result());
        row.setUpdateTime(now);
        if (outcome.result() == DataQueryCheckResult.FAILED) {
            row.setCheckDetail(outcome.failure().getCode());
            row.setCheckFailureCount((row.getCheckFailureCount() == null ? 0 : row.getCheckFailureCount()) + 1);
            return;
        }
        row.setCheckDetail(outcome.missing().isEmpty() ? null : String.join(",", outcome.missing()));
        row.setCheckFailureCount(0);
        row.setLastCheckSuccessTime(now);
    }

    /**
     * DATA_QUERY 动作是否仍可检查：类型为 DATA_QUERY、待处理，且所属节点执行中或待审批。
     *
     * @param row 动作运行
     * @param nodeRun 节点运行
     * @return 可检查返回 true
     */
    public boolean dataQueryCheckable(WorkflowActionRun row, WorkflowNodeRun nodeRun) {
        boolean open = nodeRun.getStatus() == NodeRunStatus.ACTIVE || nodeRun.getStatus() == NodeRunStatus.APPROVING;
        return row.getActionType() == WorkflowActionType.DATA_QUERY && row.getStatus() == ActionRunStatus.PENDING
            && open;
    }

    /**
     * 审批人做出同意、退回修改或驳回决定。
     *
     * @param context 实例上下文
     * @param approvalId 审批行
     * @param decision 决定
     * @param comment 意见；退回修改与驳回必填
     * @param rejectTargetNodeKey 汇合后驳回指定的分支末任务节点；其他情形可为 null
     * @param actorId 审批人
     * @param ids ID 生成器
     */
    public void decide(WorkflowInstanceContext context, long approvalId, ApprovalDecision decision, String comment,
        String rejectTargetNodeKey, long actorId, LongSupplier ids) {
        requireRunning(context);
        WorkflowApproval approval = approval(context, approvalId);
        WorkflowNodeRun nodeRun = nodeRun(context, approval.getNodeRunId());
        requireDecidable(context, approval, nodeRun, actorId);
        if (decision != ApprovalDecision.APPROVE && WorkflowValueFormats.isBlank(comment)) {
            throw new BizCheckFailException("退回修改与驳回必须填写意见");
        }
        RuntimeRecorder recorder = new RuntimeRecorder(context, ids);
        switch (decision) {
            case APPROVE -> approve(context, approval, nodeRun, comment, recorder, actorId);
            case RETURN -> returnForRevision(context, approval, nodeRun, comment, recorder, actorId);
            case REJECT -> reject(context, approval, nodeRun, comment, rejectTargetNodeKey, recorder, actorId);
        }
        finish(context, recorder);
    }

    /**
     * 转交动作：当前执行人或有实例管理权限的人把未完成动作转给另一位 ACTIVE 人员（是否 ACTIVE 由调用方核对）。
     *
     * @param context 实例上下文
     * @param actionRunId 动作运行
     * @param toUserId 新执行人
     * @param reason 转交说明，可空
     * @param actorId 操作人
     * @param manage 操作人是否有实例管理权限
     * @param ids ID 生成器
     */
    public void transferAction(WorkflowInstanceContext context, long actionRunId, long toUserId, String reason,
        long actorId, boolean manage, LongSupplier ids) {
        requireRunning(context);
        WorkflowActionRun row = actionRun(context, actionRunId);
        WorkflowNodeRun nodeRun = nodeRun(context, row.getNodeRunId());
        if (row.getStatus() != ActionRunStatus.PENDING || nodeRun.getStatus() != NodeRunStatus.ACTIVE) {
            throw new BizCheckFailException("动作已完成或节点不在执行中，不能转交：" + row.getActionKey());
        }
        requireTransferable(row.getAssigneeId(), toUserId, actorId, manage);
        long from = row.getAssigneeId();
        row.setAssigneeId(toUserId);
        row.setUpdateTime(context.getNow());
        context.getChange().updated(row);
        RuntimeRecorder recorder = new RuntimeRecorder(context, ids);
        recordTransfer(context, TransferTargetType.ACTION_RUN, actionRunId, from, toUserId, reason, actorId, ids);
        recorder.event(RuntimeEvent.node(NotificationTrigger.TRANSFERRED, nodeRun, null, null, actorId, reason));
        finish(context, recorder);
    }

    /**
     * 转交审批：当前审批人或有实例管理权限的人把待决定审批转给另一位 ACTIVE 人员；不能转给同一节点的其他审批人。
     *
     * @param context 实例上下文
     * @param approvalId 审批行
     * @param toUserId 新审批人
     * @param reason 转交说明，可空
     * @param actorId 操作人
     * @param manage 操作人是否有实例管理权限
     * @param ids ID 生成器
     */
    public void transferApproval(WorkflowInstanceContext context, long approvalId, long toUserId, String reason,
        long actorId, boolean manage, LongSupplier ids) {
        requireRunning(context);
        WorkflowApproval row = approval(context, approvalId);
        WorkflowNodeRun nodeRun = nodeRun(context, row.getNodeRunId());
        if (row.getStatus() != ApprovalStatus.PENDING || nodeRun.getStatus() != NodeRunStatus.APPROVING) {
            throw new BizCheckFailException("审批已处理，不能转交");
        }
        requireTransferable(row.getApproverId(), toUserId, actorId, manage);
        if (context.approvalsOf(nodeRun.getNodeRunId()).stream().anyMatch(other -> other.getApproverId() == toUserId)) {
            throw new BizCheckFailException("不能转交给同一节点的其他审批人");
        }
        long from = row.getApproverId();
        row.setApproverId(toUserId);
        row.setUpdateTime(context.getNow());
        context.getChange().updated(row);
        RuntimeRecorder recorder = new RuntimeRecorder(context, ids);
        recordTransfer(context, TransferTargetType.APPROVAL, approvalId, from, toUserId, reason, actorId, ids);
        recorder.event(RuntimeEvent.node(NotificationTrigger.TRANSFERRED, nodeRun, null, null, actorId, reason));
        finish(context, recorder);
    }

    /**
     * 终止实例：进行中的节点运行取消，未完成动作取消，待决定审批撤回；终止后只读。
     *
     * @param context 实例上下文
     * @param reason 终止原因
     * @param actorId 操作人
     * @param manage 操作人是否有实例管理权限
     * @param ids ID 生成器
     */
    public void terminate(WorkflowInstanceContext context, String reason, long actorId, boolean manage,
        LongSupplier ids) {
        requireRunning(context);
        if (!manage && context.getInstance().getStartedBy() != actorId) {
            throw new BizCheckFailException("只有发起人或有实例管理权限的人可以终止实例");
        }
        RuntimeRecorder recorder = new RuntimeRecorder(context, ids);
        NavigationPlan plan = navigator.cancelOpenRuns(context.getGraph(), states(context));
        plan.statusChanges().forEach(change -> recorder.changeNodeRun(change, actorId));
        context.getApprovals().stream().filter(row -> row.getStatus() == ApprovalStatus.PENDING)
            .forEach(row -> recorder.withdrawPending(row.getNodeRunId(), null));
        context.getInstance().setStatus(WorkflowInstanceStatus.TERMINATED);
        context.getInstance().setEndTime(context.getNow());
        context.getInstance().setTerminatedBy(actorId);
        context.getInstance().setTerminationReason(reason);
        recorder.event(RuntimeEvent.instance(NotificationTrigger.TERMINATED, WorkflowInstanceStatus.RUNNING.getCode(),
            WorkflowInstanceStatus.TERMINATED.getCode(), actorId, reason));
        finish(context, recorder);
    }

    /**
     * 当前实例变量：start.* 取启动值，动作输出取最新一轮 COMPLETED 的 output_json（技术方案 3.3）。
     *
     * @param context 实例上下文
     * @return 实例变量
     */
    public WorkflowVariables variables(WorkflowInstanceContext context) {
        WorkflowVariables.Builder builder = WorkflowVariables.builder().add(
            VariableDeclaration.ofForm(VariableDeclaration.START_PREFIX, context.getStartForm()),
            json.tree(context.getInstance().getStartValuesJson()));
        for (WorkflowNode node : context.getGraph().nodes()) {
            node.actions().forEach(action -> context.completedOutput(action.key()).ifPresent(run -> builder.add(
                context.getTemplates().get(action.templateId()).config().outputs(action.key()),
                json.tree(run.getOutputJson()))));
        }
        return builder.build();
    }

    /**
     * 同意：先兜底确认必选动作全部已完成（放行规则 A），再按 ANY 任一人同意即放行并撤回其余、ALL 全部同意才放行。
     *
     * @param context 实例上下文
     * @param approval 审批行
     * @param nodeRun 节点运行
     * @param comment 意见
     * @param recorder 记录器
     * @param actorId 审批人
     */
    void approve(WorkflowInstanceContext context, WorkflowApproval approval, WorkflowNodeRun nodeRun, String comment,
        RuntimeRecorder recorder, long actorId) {
        List<String> missing = missingRequired(context, nodeRun);
        if (!missing.isEmpty()) {
            throw new ConcurrentRequestException("必选动作尚未全部完成，不能同意：" + String.join("、", missing));
        }
        decideRow(context, approval, ApprovalStatus.APPROVED, comment, null);
        ApprovalMode mode = context.node(nodeRun.getNodeKey()).approval().mode();
        boolean all = context.approvalsOf(nodeRun.getNodeRunId()).stream()
            .allMatch(row -> row.getStatus() == ApprovalStatus.APPROVED);
        if (mode == ApprovalMode.ANY || all) {
            recorder.withdrawPending(nodeRun.getNodeRunId(), approval);
            recorder.apply(navigator.completeTask(context.getGraph(), states(context), nodeRun.getNodeRunId(),
                variables(context), recorder.ids()), actorId);
        }
    }

    /**
     * 退回修改：本节点开新一轮，不触及上一节点（B5a）。
     *
     * @param context 实例上下文
     * @param approval 审批行
     * @param nodeRun 节点运行
     * @param comment 意见
     * @param recorder 记录器
     * @param actorId 审批人
     */
    void returnForRevision(WorkflowInstanceContext context, WorkflowApproval approval, WorkflowNodeRun nodeRun,
        String comment, RuntimeRecorder recorder, long actorId) {
        NavigationPlan plan = navigator.returnForRevision(context.getGraph(), states(context), nodeRun.getNodeRunId(),
            recorder.ids());
        decideRow(context, approval, ApprovalStatus.RETURNED, comment, null);
        recorder.withdrawPending(nodeRun.getNodeRunId(), approval);
        recorder.event(RuntimeEvent.node(NotificationTrigger.RETURNED_FOR_REVISION, nodeRun, NodeRunStatus.APPROVING,
            NodeRunStatus.RETURNED, actorId, comment));
        recorder.apply(plan, actorId);
    }

    /**
     * 驳回：退回实际走过的上一个任务节点（B5）。
     *
     * @param context 实例上下文
     * @param approval 审批行
     * @param nodeRun 节点运行
     * @param comment 意见
     * @param target 汇合后驳回指定的分支末任务节点
     * @param recorder 记录器
     * @param actorId 审批人
     */
    void reject(WorkflowInstanceContext context, WorkflowApproval approval, WorkflowNodeRun nodeRun, String comment,
        String target, RuntimeRecorder recorder, long actorId) {
        NavigationPlan plan = navigator.reject(context.getGraph(), states(context), nodeRun.getNodeRunId(), target,
            recorder.ids());
        decideRow(context, approval, ApprovalStatus.REJECTED, comment, target);
        recorder.withdrawPending(nodeRun.getNodeRunId(), approval);
        recorder.event(RuntimeEvent.node(NotificationTrigger.REJECTED, nodeRun, NodeRunStatus.APPROVING,
            NodeRunStatus.REJECTED, actorId, comment));
        recorder.apply(plan, actorId);
    }

    /**
     * 必选动作全部通过后：有审批人则开放审批，否则直接放行并推进。
     *
     * @param context 实例上下文
     * @param nodeRun 节点运行
     * @param recorder 记录器
     * @param actorId 提交人；系统完成 DATA_QUERY 时为 null
     */
    void advanceAfterActions(WorkflowInstanceContext context, WorkflowNodeRun nodeRun, RuntimeRecorder recorder,
        Long actorId) {
        if (context.node(nodeRun.getNodeKey()).hasApprovers()) {
            recorder.setStatus(nodeRun, NodeRunStatus.APPROVING);
            recorder.createApprovals(nodeRun, actorId, NodeRunStatus.ACTIVE);
            return;
        }
        recorder.apply(navigator.completeTask(context.getGraph(), states(context), nodeRun.getNodeRunId(),
            variables(context), recorder.ids()), actorId);
    }

    /**
     * 节点运行中尚未完成的必选动作，按动作名（版本快照冻结的模板名称，缺失时为动作 key）列出。
     *
     * @param context 实例上下文
     * @param nodeRun 节点运行
     * @return 未完成的必选动作名；全部完成为空
     */
    List<String> missingRequired(WorkflowInstanceContext context, WorkflowNodeRun nodeRun) {
        List<WorkflowActionRun> missing = context.actionRunsOf(nodeRun.getNodeRunId()).stream()
            .filter(WorkflowActionRun::getRequired).filter(run -> run.getStatus() != ActionRunStatus.COMPLETED).toList();
        if (missing.isEmpty()) {
            return List.of();
        }
        return actionNames(context, missing);
    }

    /**
     * 动作名：版本快照冻结的模板名称，缺失时为动作 key。
     *
     * @param context 实例上下文
     * @param runs 动作运行
     * @return 与 runs 同序的动作名
     */
    List<String> actionNames(WorkflowInstanceContext context, List<WorkflowActionRun> runs) {
        Map<Long, String> names = json.snapshotNames(context.getVersion().getActionSnapshotJson());
        return runs.stream().map(run -> names.getOrDefault(run.getActionTemplateId(), run.getActionKey())).toList();
    }

    /**
     * 追加转交记录。
     *
     * @param context 实例上下文
     * @param type 转交对象类型
     * @param targetId 动作运行或审批行
     * @param from 原处理人
     * @param to 新处理人
     * @param reason 说明
     * @param actorId 操作人
     * @param ids ID 生成器
     */
    void recordTransfer(WorkflowInstanceContext context, TransferTargetType type, long targetId, long from, long to,
        String reason, long actorId, LongSupplier ids) {
        WorkflowTransfer row = new WorkflowTransfer();
        row.setTransferId(ids.getAsLong());
        row.setInstanceId(context.getInstance().getInstanceId());
        row.setTargetType(type);
        row.setTargetId(targetId);
        row.setFromUserId(from);
        row.setToUserId(to);
        row.setReason(reason);
        row.setTransferredBy(actorId);
        row.setCreateTime(context.getNow());
        context.getTransfers().add(row);
        context.getChange().getNewTransfers().add(row);
    }

    /**
     * 记录审批行的决定。
     *
     * @param context 实例上下文
     * @param approval 审批行
     * @param status 决定后状态
     * @param comment 意见
     * @param target 驳回目标
     */
    void decideRow(WorkflowInstanceContext context, WorkflowApproval approval, ApprovalStatus status, String comment,
        String target) {
        approval.setStatus(status);
        approval.setDecisionComment(comment);
        approval.setRejectTargetNodeKey(target);
        approval.setDecidedTime(context.getNow());
        approval.setUpdateTime(context.getNow());
        context.getChange().updated(approval);
    }

    /**
     * 收尾：按事件登记通知回调，推进实例更新时间。
     *
     * @param context 实例上下文
     * @param recorder 记录器
     */
    void finish(WorkflowInstanceContext context, RuntimeRecorder recorder) {
        planner.plan(context, recorder.events(), recorder.ids());
        context.getInstance().setUpdateTime(context.getNow());
    }

    /**
     * 完成前提：节点未放行、操作人是当前执行人、动作未取消；必选动作在节点进入审批后锁定。
     *
     * @param context 实例上下文
     * @param row 动作运行
     * @param nodeRun 节点运行
     * @param actorId 操作人
     */
    void requireCompletable(WorkflowInstanceContext context, WorkflowActionRun row, WorkflowNodeRun nodeRun,
        long actorId) {
        boolean open = nodeRun.getStatus() == NodeRunStatus.ACTIVE || nodeRun.getStatus() == NodeRunStatus.APPROVING;
        if (!open) {
            throw new BizCheckFailException("节点已结束，不再接受提交：" + nodeRun.getNodeKey());
        }
        if (row.getAssigneeId() != actorId) {
            throw new BizCheckFailException("只有被指派的执行人可以完成该动作");
        }
        if (row.getStatus() == ActionRunStatus.CANCELLED) {
            throw new BizCheckFailException("动作已取消");
        }
        if (row.getRequired() && nodeRun.getStatus() == NodeRunStatus.APPROVING) {
            throw new ConcurrentRequestException("节点已进入审批，必选动作不能再完成或修改：" + actionNames(context,
                List.of(row)).get(0)
                + "；需要修改请由审批人退回修改");
        }
    }

    /**
     * 审批前提：节点待审批、本行待决定、操作人是该行审批人且未提交过该节点动作。
     *
     * @param context 实例上下文
     * @param approval 审批行
     * @param nodeRun 节点运行
     * @param actorId 操作人
     */
    void requireDecidable(WorkflowInstanceContext context, WorkflowApproval approval, WorkflowNodeRun nodeRun,
        long actorId) {
        if (approval.getStatus() != ApprovalStatus.PENDING || nodeRun.getStatus() != NodeRunStatus.APPROVING) {
            throw new BizCheckFailException("审批已处理或节点不在待审批状态");
        }
        if (approval.getApproverId() != actorId) {
            throw new BizCheckFailException("只有被指派的审批人可以审批");
        }
        if (context.submittersOf(nodeRun.getNodeRunId()).contains(actorId)) {
            throw new BizCheckFailException("同一人不能既执行该节点的动作又审批该节点");
        }
    }

    /**
     * 转交前提：操作人是当前处理人或有实例管理权限；不能转给自己或当前处理人。
     *
     * @param current 当前处理人
     * @param toUserId 新处理人
     * @param actorId 操作人
     * @param manage 是否有实例管理权限
     */
    void requireTransferable(long current, long toUserId, long actorId, boolean manage) {
        if (current != actorId && !manage) {
            throw new BizCheckFailException("只有当前处理人或有实例管理权限的人可以转交");
        }
        if (toUserId == actorId || toUserId == current) {
            throw new BizCheckFailException("不能转交给自己或当前处理人");
        }
    }

    /**
     * 实例须在运行中；结束后只读。
     *
     * @param context 实例上下文
     */
    void requireRunning(WorkflowInstanceContext context) {
        if (context.getInstance().getStatus() != WorkflowInstanceStatus.RUNNING) {
            throw new BizCheckFailException("实例已结束，只读：" + context.getInstance().getStatus().getCode());
        }
    }

    /**
     * 导航所需的节点运行事实。
     *
     * @param context 实例上下文
     * @return 节点运行状态
     */
    List<NodeRunState> states(WorkflowInstanceContext context) {
        return context.getNodeRuns().stream().map(run -> new NodeRunState(run.getNodeRunId(), run.getNodeKey(),
            run.getNodeType(), run.getRoundNo(), run.getBranchKey(), run.getPreviousNodeRunId(), run.getStatus(),
            run.getSelectedEdgeKey())).toList();
    }

    /**
     * 取实例内的动作运行。
     *
     * @param context 实例上下文
     * @param id 动作运行 ID
     * @return 动作运行
     */
    WorkflowActionRun actionRun(WorkflowInstanceContext context, long id) {
        return context.getActionRuns().stream().filter(row -> row.getActionRunId() == id).findFirst()
            .orElseThrow(() -> new ErrorException("实例中不存在动作运行：" + id));
    }

    /**
     * 取实例内的审批行。
     *
     * @param context 实例上下文
     * @param id 审批 ID
     * @return 审批行
     */
    WorkflowApproval approval(WorkflowInstanceContext context, long id) {
        return context.getApprovals().stream().filter(row -> row.getApprovalId() == id).findFirst()
            .orElseThrow(() -> new ErrorException("实例中不存在审批：" + id));
    }

    /**
     * 取实例内的节点运行。
     *
     * @param context 实例上下文
     * @param id 节点运行 ID
     * @return 节点运行
     */
    WorkflowNodeRun nodeRun(WorkflowInstanceContext context, Long id) {
        return context.nodeRun(id).orElseThrow(() -> new ErrorException("实例中不存在节点运行：" + id));
    }
}
