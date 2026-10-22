package com.paytech.platform.support.workflow.service.processor;

import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionRun;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowVerification;
import com.paytech.platform.support.workflow.domain.WorkflowInstanceContext;
import com.paytech.platform.support.workflow.domain.attachment.AttachmentFact;
import com.paytech.platform.support.workflow.domainservice.convert.WorkflowRuntimeJson;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowRuntimeEngine;
import com.paytech.platform.support.workflow.domainservice.validator.AttachmentRules;
import com.paytech.platform.support.workflow.service.processor.request.ReverifyActionRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowSubmissionResult;

import lombok.RequiredArgsConstructor;

/**
 * 重新验证动作（技术方案第 4 节、第 7 节 #20）：文件扫描是异步的，接收文件的动作提交时可能验证未通过；扫描结束后，
 * 当前执行人按原提交内容再验证一次，与提交走同一验证规则，通过后同样开放审批或放行
 * <li>只适用于 UNVERIFIED、已提交且接收文件的动作（FILE_UPLOAD 或含 FILE 字段的 FORM）；不改提交内容与提交人。</li>
 * <li>锁 workflow-instance:{id}（TRY_LOCK），与提交、审批、转交、终止共用；保存时实例版本 CAS 与各行条件更新。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ReverifyWorkflowActionProcessor
    extends AbstractLockProcessor<ReverifyActionRequest, WorkflowSubmissionResult> {

    private final WorkflowProcessorSupport support;
    private final WorkflowAttachments attachments;
    private final WorkflowInstanceCommands commands;
    private final WorkflowRuntimeJson json = new WorkflowRuntimeJson();
    private final WorkflowRuntimeEngine engine = new WorkflowRuntimeEngine();

    /** 按动作运行所属实例加锁。 */
    @Override
    protected LockCommand lockCommand(ReverifyActionRequest request) {
        return LockCommand.tryLock(WorkflowInstanceCommands.lock(commands.instanceOfActionRun(request.getActionRunId())),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected WorkflowSubmissionResult processWithinLock(ReverifyActionRequest request) {
        long instanceId = commands.instanceOfActionRun(request.getActionRunId());
        AtomicReference<WorkflowVerification> verification = new AtomicReference<>();
        return commands.run(request.getActor(), request.getSession(), instanceId,
            (session, context) -> verification.set(engine.reverifyAction(context, request.getActionRunId(),
                support.actorId(session), submittedFiles(context, request.getActionRunId()), support::nextId)),
            new WorkflowInstanceCommands.AuditResource("WORKFLOW_ACTION_RUN", request.getActionRunId(), "REVERIFIED"),
            context -> {
                WorkflowActionRun row = actionRun(context, request.getActionRunId());
                return new WorkflowSubmissionResult(row.getActionRunId(), row.getStatus().getCode(),
                    verification.get().getVerificationId(), verification.get().getFailureReason(),
                    context.getInstance().getStatus().getCode());
            });
    }

    /** 原提交内容引用文件的当前事实；归属已在提交时确认。 */
    Map<Long, AttachmentFact> submittedFiles(
        WorkflowInstanceContext context, long actionRunId) {
        WorkflowActionRun row = context.getActionRuns().stream().filter(run -> run.getActionRunId() == actionRunId)
            .findFirst().orElse(null);
        var template = row == null ? null : context.getTemplates().get(row.getActionTemplateId());
        if (template == null || row.getOutputJson() == null) {
            return Map.of();
        }
        return attachments.facts(AttachmentRules.referenced(template.config(), json.tree(row.getOutputJson())));
    }

    /** 上下文中的动作运行。 */
    WorkflowActionRun actionRun(WorkflowInstanceContext context, long actionRunId) {
        return context.getActionRuns().stream().filter(run -> run.getActionRunId() == actionRunId).findFirst()
            .orElseThrow();
    }
}
