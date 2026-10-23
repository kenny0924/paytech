package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowRuntimeEngine;
import com.paytech.platform.support.workflow.service.processor.request.SubmitActionRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowSubmissionResult;

import lombok.RequiredArgsConstructor;

/**
 * 执行人完成动作（需求 E 组，2026-10-22 起替代“提交并同步验证”）：只有被指派执行人可完成；只检查必填与技术保护，
 * 合格即 COMPLETED，必选动作全部完成后有审批人则开放审批，否则直接放行并推进
 * <li>锁 workflow-instance:{id}（TRY_LOCK），保存时实例版本 CAS 与各行条件更新。</li>
 * <li>文件地址格式不合法、必填缺失、值类型不符或超过平台长度上限返回 422 字段错误（output.{key}、output.files[i]），
 * 事务回滚、不改任何状态。</li>
 * <li>必选动作在节点进入审批后不能再完成或修改（409）；可选动作在节点放行前可完成；放行后全部锁定。</li>
 * <li>引用的文件须由提交人以工作流附件上传、未被其他实例引用，否则拒绝且不保存；扫描中或未通过扫描不挡完成。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class SubmitWorkflowActionProcessor extends AbstractLockProcessor<SubmitActionRequest, WorkflowSubmissionResult> {

    private final WorkflowProcessorSupport support;
    private final WorkflowAttachments attachments;
    private final WorkflowInstanceCommands commands;
    private final WorkflowRuntimeEngine engine = new WorkflowRuntimeEngine();

    /** 按动作运行所属实例加锁。 */
    @Override
    protected LockCommand lockCommand(SubmitActionRequest request) {
        return LockCommand.tryLock(WorkflowInstanceCommands.lock(commands.instanceOfActionRun(request.getActionRunId())),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected WorkflowSubmissionResult processWithinLock(SubmitActionRequest request) {
        long instanceId = commands.instanceOfActionRun(request.getActionRunId());
        return commands.run(request.getActor(), request.getSession(), instanceId,
            (session, context) -> {
                long actor = support.actorId(session);
                var files = attachments.forSubmission(context, request.getActionRunId(), request.getOutput(), actor);
                attachments.requireNoFieldErrors(engine.completeAction(context, request.getActionRunId(),
                    request.getOutput(), actor, files, support::nextId));
            },
            new WorkflowInstanceCommands.AuditResource("WORKFLOW_ACTION_RUN", request.getActionRunId(), "SUBMITTED"),
            context -> {
                WorkflowActionRun row = context.getActionRuns().stream()
                    .filter(run -> run.getActionRunId().equals(request.getActionRunId())).findFirst().orElseThrow();
                return new WorkflowSubmissionResult(row.getActionRunId(), row.getStatus().getCode(),
                    context.getInstance().getStatus().getCode());
            });
    }
}
