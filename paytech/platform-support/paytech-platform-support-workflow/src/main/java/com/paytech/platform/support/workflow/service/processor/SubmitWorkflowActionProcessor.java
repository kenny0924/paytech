package com.paytech.platform.support.workflow.service.processor;

import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowRuntimeEngine;
import com.paytech.platform.support.workflow.service.processor.request.SubmitActionRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowSubmissionResult;

import lombok.RequiredArgsConstructor;

/**
 * 执行人提交动作输出并同步验证（B2/B3）：只有被指派执行人、节点 ACTIVE、动作未验证通过时可提交；
 * 必选动作全部通过后有审批人则开放审批，否则直接放行并推进
 * <li>锁 workflow-instance:{id}（TRY_LOCK），保存时实例版本 CAS 与各行条件更新。</li>
 * <li>提交内容引用的文件须由提交人以工作流附件上传、未被其他实例引用，否则拒绝且不保存；扫描未结束记为验证未通过，
 * 扫描结束后重新验证。</li>
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
        AtomicReference<WorkflowVerification> verification = new AtomicReference<>();
        return commands.run(request.getActor(), request.getSession(), instanceId,
            (session, context) -> {
                long actor = support.actorId(session);
                var files = attachments.forSubmission(context, request.getActionRunId(), request.getOutput(), actor);
                verification.set(engine.submitAction(context, request.getActionRunId(), request.getOutput(), actor,
                    files, support::nextId));
            },
            new WorkflowInstanceCommands.AuditResource("WORKFLOW_ACTION_RUN", request.getActionRunId(), "SUBMITTED"),
            context -> {
                WorkflowActionRun row = context.getActionRuns().stream()
                    .filter(run -> run.getActionRunId().equals(request.getActionRunId())).findFirst().orElseThrow();
                return new WorkflowSubmissionResult(row.getActionRunId(), row.getStatus().getCode(),
                    verification.get().getVerificationId(), verification.get().getFailureReason(),
                    context.getInstance().getStatus().getCode());
            });
    }
}
