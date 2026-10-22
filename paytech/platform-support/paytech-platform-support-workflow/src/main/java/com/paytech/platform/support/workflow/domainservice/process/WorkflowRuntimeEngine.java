package com.paytech.platform.support.workflow.domainservice.process;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.LongSupplier;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowApproval;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowNodeRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowTransfer;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;
import com.paytech.platform.support.workflow.core.enums.ActionRunStatus;
import com.paytech.platform.support.workflow.core.enums.ApprovalDecision;
import com.paytech.platform.support.workflow.core.enums.ApprovalMode;
import com.paytech.platform.support.workflow.core.enums.ApprovalStatus;
import com.paytech.platform.support.workflow.core.enums.NodeRunStatus;
import com.paytech.platform.support.workflow.core.enums.NotificationTrigger;
import com.paytech.platform.support.workflow.core.enums.TransferTargetType;
import com.paytech.platform.support.workflow.core.enums.VerificationResult;
import com.paytech.platform.support.workflow.core.enums.WorkflowInstanceStatus;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.action.ActionTemplateReference;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.runtime.NavigationPlan;
import com.paytech.platform.support.workflow.domain.runtime.NodeRunState;
import com.paytech.platform.support.workflow.domain.variable.VariableDeclaration;
import com.paytech.platform.support.workflow.domain.variable.WorkflowVariables;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

/**
 * 运行引擎（技术方案第 4 节）：在已加载的实例上下文上执行发起、提交动作、审批决定、转交与终止，
 * 结合 {@link WorkflowNavigator} 推进，并按规则登记通知回调。纯内存计算，不访问数据库或外部服务。
 *
 * <p>人员与状态前提不满足抛 {@link BizCheckFailException}；运行记录与版本不一致抛 {@link ErrorException}。
 * 写时并发由保存时的版本与旧状态条件最终保护。</p>
 */
public final class WorkflowRuntimeEngine {

    /** 失败原因列的上限。 */
    private static final int REASON_LIMIT = 512;

    private final WorkflowNavigator navigator = new WorkflowNavigator();

    private final ActionOutputVerifier verifier = new ActionOutputVerifier();

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
     * 执行人提交动作输出并同步验证；必选动作全部通过后开放审批或直接放行。
     *
     * @param context 实例上下文
     * @param actionRunId 动作运行
     * @param output 提交内容
     * @param actorId 提交人
     * @param files 提交内容引用文件的事实；归属已由调用方确认
     * @param ids ID 生成器
     * @return 本次验证记录
     */
    public WorkflowVerification submitAction(WorkflowInstanceContext context, long actionRunId, JsonNode output,
        long actorId, Map<Long, AttachmentFact> files, LongSupplier ids) {
        requireRunning(context);
        WorkflowActionRun row = actionRun(context, actionRunId);
        WorkflowNodeRun nodeRun = nodeRun(context, row.getNodeRunId());
        requireSubmittable(row, nodeRun, actorId);
        ActionTemplateReference template = context.getTemplates().get(row.getActionTemplateId());
        List<String> problems = verifier.verify(template, output, files);
        row.setOutputJson(json.write(output));
        row.setSubmittedBy(actorId);
        row.setSubmittedTime(context.getNow());
        return recordVerification(context, row, nodeRun, template, problems, actorId, ids);
    }

    /**
     * 重新验证（技术方案第 4 节）：对已提交但未通过、接收文件的动作，按原提交内容再验证一次，用于文件扫描结束后；
     * 不改提交内容与提交人。只有当前执行人可以触发，节点须在执行中。
     *
     * @param context 实例上下文
     * @param actionRunId 动作运行
     * @param actorId 操作人
     * @param files 原提交内容引用文件的事实
     * @param ids ID 生成器
     * @return 本次验证记录
     */
    public WorkflowVerification reverifyAction(WorkflowInstanceContext context, long actionRunId, long actorId,
        Map<Long, AttachmentFact> files, LongSupplier ids) {
        requireRunning(context);
        WorkflowActionRun row = actionRun(context, actionRunId);
        WorkflowNodeRun nodeRun = nodeRun(context, row.getNodeRunId());
        requireSubmittable(row, nodeRun, actorId);
        ActionTemplateReference template = context.getTemplates().get(row.getActionTemplateId());
        if (row.getStatus() != ActionRunStatus.UNVERIFIED || row.getOutputJson() == null) {
            throw new BizCheckFailException("动作尚未提交，不能重新验证：" + row.getActionKey());
        }
        if (template == null || !AttachmentRules.acceptsFiles(template.config())) {
            throw new BizCheckFailException("只有含文件的动作可以重新验证：" + row.getActionKey());
        }
        List<String> problems = verifier.verify(template, json.tree(row.getOutputJson()), files);
        return recordVerification(context, row, nodeRun, template, problems, actorId, ids);
    }

    /**
     * 按验证结果更新动作状态、追加验证记录，并在必选动作全部通过后开放审批或放行。
     *
     * @param context 实例上下文
     * @param row 动作运行（提交内容已就位）
     * @param nodeRun 节点运行
     * @param template 模板快照
     * @param problems 验证问题
     * @param actorId 操作人
     * @param ids ID 生成器
     * @return 验证记录
     */
    WorkflowVerification recordVerification(WorkflowInstanceContext context, WorkflowActionRun row,
        WorkflowNodeRun nodeRun, ActionTemplateReference template, List<String> problems, long actorId,
        LongSupplier ids) {
        RuntimeRecorder recorder = new RuntimeRecorder(context, ids);
        WorkflowVerification verification = recordResult(context, row, template, problems, actorId, ids);
        if (!problems.isEmpty()) {
            recorder.event(RuntimeEvent.node(NotificationTrigger.VERIFICATION_FAILED, nodeRun, null, null, actorId,
                verification.getFailureReason()));
        } else if (requiredVerified(context, nodeRun)) {
            advanceAfterActions(context, nodeRun, recorder, actorId);
        }
        finish(context, recorder);
        return verification;
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
        boolean open = row.getStatus() == ActionRunStatus.PENDING || row.getStatus() == ActionRunStatus.UNVERIFIED;
        if (!open || nodeRun.getStatus() != NodeRunStatus.ACTIVE) {
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
     * 当前实例变量：start.* 取启动值，动作输出取最新一轮 VERIFIED 的 output_json（技术方案 3.3）。
     *
     * @param context 实例上下文
     * @return 实例变量
     */
    public WorkflowVariables variables(WorkflowInstanceContext context) {
        WorkflowVariables.Builder builder = WorkflowVariables.builder().add(
            VariableDeclaration.ofForm(VariableDeclaration.START_PREFIX, context.getStartForm()),
            json.tree(context.getInstance().getStartValuesJson()));
        for (WorkflowNode node : context.getGraph().nodes()) {
            node.actions().forEach(action -> context.verifiedOutput(action.key()).ifPresent(run -> builder.add(
                context.getTemplates().get(action.templateId()).config().outputs(action.key()),
                json.tree(run.getOutputJson()))));
        }
        return builder.build();
    }

    /**
     * 同意：ANY 任一人同意即放行并撤回其余；ALL 全部同意才放行。
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
     * @param actorId 提交人
     */
    void advanceAfterActions(WorkflowInstanceContext context, WorkflowNodeRun nodeRun, RuntimeRecorder recorder,
        long actorId) {
        if (context.node(nodeRun.getNodeKey()).hasApprovers()) {
            recorder.setStatus(nodeRun, NodeRunStatus.APPROVING);
            recorder.createApprovals(nodeRun, actorId, NodeRunStatus.ACTIVE);
            return;
        }
        recorder.apply(navigator.completeTask(context.getGraph(), states(context), nodeRun.getNodeRunId(),
            variables(context), recorder.ids()), actorId);
    }

    /**
     * 节点运行的全部必选动作是否都已 VERIFIED。
     *
     * @param context 实例上下文
     * @param nodeRun 节点运行
     * @return 全部通过返回 true
     */
    boolean requiredVerified(WorkflowInstanceContext context, WorkflowNodeRun nodeRun) {
        return context.actionRunsOf(nodeRun.getNodeRunId()).stream().filter(WorkflowActionRun::getRequired)
            .allMatch(run -> run.getStatus() == ActionRunStatus.VERIFIED);
    }

    /**
     * 更新动作状态并追加验证记录；以最后一次验证为准。接收文件的动作以引用的文件地址作为证据。
     *
     * @param context 实例上下文
     * @param row 动作运行
     * @param template 模板快照
     * @param problems 验证问题
     * @param actorId 验证触发人
     * @param ids ID 生成器
     * @return 验证记录
     */
    WorkflowVerification recordResult(WorkflowInstanceContext context, WorkflowActionRun row,
        ActionTemplateReference template, List<String> problems, long actorId, LongSupplier ids) {
        row.setStatus(problems.isEmpty() ? ActionRunStatus.VERIFIED : ActionRunStatus.UNVERIFIED);
        row.setUpdateTime(context.getNow());
        context.getChange().updated(row);
        WorkflowVerification verification = new WorkflowVerification();
        verification.setVerificationId(ids.getAsLong());
        verification.setActionRunId(row.getActionRunId());
        verification.setInstanceId(row.getInstanceId());
        verification.setResult(problems.isEmpty() ? VerificationResult.PASSED : VerificationResult.FAILED);
        verification.setFailureReason(problems.isEmpty() ? null : truncate(String.join("；", problems)));
        verification.setEvidenceJson(fileEvidence(template, row));
        verification.setVerifiedBy(actorId);
        verification.setVerifiedTime(context.getNow());
        verification.setCreateTime(context.getNow());
        context.getVerifications().add(verification);
        context.getChange().getNewVerifications().add(verification);
        return verification;
    }

    /**
     * 接收文件的动作的验证证据：{@code {"files": ["/files/v1/{fileId}", ...]}}（文件地址）；其他动作没有证据。
     *
     * @param template 模板快照
     * @param row 动作运行
     * @return 证据 JSON；没有为 null
     */
    String fileEvidence(ActionTemplateReference template, WorkflowActionRun row) {
        if (template == null || !AttachmentRules.acceptsFiles(template.config())) {
            return null;
        }
        Collection<Long> fileIds = AttachmentRules.referenced(template.config(), json.tree(row.getOutputJson()));
        ObjectNode evidence = JsonNodeFactory.instance.objectNode();
        ArrayNode files = evidence.putArray("files");
        fileIds.forEach(id -> files.add(AttachmentRules.address(id)));
        return json.write(evidence);
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
     * 提交前提：节点在执行中、提交人是当前执行人、动作尚未验证通过。
     *
     * @param row 动作运行
     * @param nodeRun 节点运行
     * @param actorId 提交人
     */
    void requireSubmittable(WorkflowActionRun row, WorkflowNodeRun nodeRun, long actorId) {
        if (nodeRun.getStatus() != NodeRunStatus.ACTIVE) {
            throw new BizCheckFailException("节点当前不接受提交：" + nodeRun.getNodeKey());
        }
        if (row.getAssigneeId() != actorId) {
            throw new BizCheckFailException("只有被指派的执行人可以提交该动作");
        }
        if (row.getStatus() == ActionRunStatus.VERIFIED) {
            throw new BizCheckFailException("动作已验证通过，不再接受提交；需要修改请由审批人退回修改");
        }
        if (row.getStatus() == ActionRunStatus.CANCELLED) {
            throw new BizCheckFailException("动作已取消");
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

    /**
     * 截断失败原因到列上限。
     *
     * @param reason 原因
     * @return 不超过 512 字符的原因
     */
    static String truncate(String reason) {
        return reason.length() <= REASON_LIMIT ? reason : reason.substring(0, REASON_LIMIT - 1) + "…";
    }
}
