package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowRuntimeEngine;
import com.paytech.platform.support.workflow.service.processor.request.TerminateInstanceRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.RequiredArgsConstructor;

/**
 * 终止实例（Q1）：发起人或有 WORKFLOW_INSTANCE_MANAGE 的人；进行中的节点与动作取消、待决定审批撤回，终止后只读。
 * <li>锁 workflow-instance:{id}（TRY_LOCK），保存时实例版本 CAS 与各行条件更新，冲突返回 409。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TerminateWorkflowInstanceProcessor extends AbstractLockProcessor<TerminateInstanceRequest, WorkflowMutation> {

    private final WorkflowProcessorSupport support;
    private final WorkflowInstanceCommands commands;
    private final WorkflowRuntimeEngine engine = new WorkflowRuntimeEngine();

    /** 按所属实例加锁。 */
    @Override
    protected LockCommand lockCommand(TerminateInstanceRequest request) {
        return LockCommand.tryLock(WorkflowInstanceCommands.lock(request.getInstanceId()), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected WorkflowMutation processWithinLock(TerminateInstanceRequest request) {
        return commands.run(request.getActor(), request.getSession(), request.getInstanceId(),
            (session, context) -> engine.terminate(context, request.getReason(), support.actorId(session),
                support.has(session, WorkflowPermissions.INSTANCE_MANAGE), support::nextId),
            new WorkflowInstanceCommands.AuditResource(WorkflowMutation.INSTANCE, request.getInstanceId(), "TERMINATED"),
            context -> new WorkflowMutation(WorkflowMutation.INSTANCE, context.getInstance().getInstanceId(),
                context.getInstance().getStatus().getCode(), context.getInstance().getVersion()));
    }
}
