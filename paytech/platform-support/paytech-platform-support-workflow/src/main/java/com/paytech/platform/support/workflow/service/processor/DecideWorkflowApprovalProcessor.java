package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowRuntimeEngine;
import com.paytech.platform.support.workflow.service.processor.request.DecideApprovalRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.RequiredArgsConstructor;

/**
 * 审批决定（B3/B5/B5a）：同意按 ANY / ALL 放行；退回修改本节点开新一轮；驳回退回实际走过的上一个任务节点，汇合后须指定分支；同一人不能审批自己执行过动作的节点。
 * <li>锁 workflow-instance:{id}（TRY_LOCK），保存时实例版本 CAS 与各行条件更新，冲突返回 409。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class DecideWorkflowApprovalProcessor extends AbstractLockProcessor<DecideApprovalRequest, WorkflowMutation> {

    private final WorkflowProcessorSupport support;
    private final WorkflowInstanceCommands commands;
    private final WorkflowRuntimeEngine engine = new WorkflowRuntimeEngine();

    /** 按所属实例加锁。 */
    @Override
    protected LockCommand lockCommand(DecideApprovalRequest request) {
        return LockCommand.tryLock(WorkflowInstanceCommands.lock(commands.instanceOfApproval(request.getApprovalId())), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected WorkflowMutation processWithinLock(DecideApprovalRequest request) {
        return commands.run(request.getActor(), request.getSession(), commands.instanceOfApproval(request.getApprovalId()),
            (session, context) -> engine.decide(context, request.getApprovalId(), request.getDecision(),
                request.getComment(), request.getRejectTargetNodeKey(), support.actorId(session), support::nextId),
            new WorkflowInstanceCommands.AuditResource("WORKFLOW_APPROVAL", request.getApprovalId(),
                request.getDecision().getCode()),
            context -> new WorkflowMutation(WorkflowMutation.INSTANCE, context.getInstance().getInstanceId(),
                context.getInstance().getStatus().getCode(), context.getInstance().getVersion()));
    }
}
