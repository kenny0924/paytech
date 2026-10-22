package com.paytech.platform.support.workflow.service.processor;

import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.domainservice.process.WorkflowRuntimeEngine;
import com.paytech.platform.support.workflow.service.processor.request.TransferTaskRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.RequiredArgsConstructor;

/**
 * 转交动作（R6）：当前执行人或有 WORKFLOW_INSTANCE_MANAGE 的人把未完成动作转给另一位 ACTIVE 的 Operations 人员，保留转交记录。
 * <li>锁 workflow-instance:{id}（TRY_LOCK），保存时实例版本 CAS 与各行条件更新，冲突返回 409。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TransferWorkflowActionProcessor extends AbstractLockProcessor<TransferTaskRequest, WorkflowMutation> {

    private final WorkflowProcessorSupport support;
    private final WorkflowInstanceCommands commands;
    private final WorkflowRuntimeEngine engine = new WorkflowRuntimeEngine();

    /** 按所属实例加锁。 */
    @Override
    protected LockCommand lockCommand(TransferTaskRequest request) {
        return LockCommand.tryLock(WorkflowInstanceCommands.lock(commands.instanceOfActionRun(request.getTargetId())), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected WorkflowMutation processWithinLock(TransferTaskRequest request) {
        support.requireActive(List.of(request.getToUserId()), "新执行人");
        return commands.run(request.getActor(), request.getSession(), commands.instanceOfActionRun(request.getTargetId()),
            (session, context) -> engine.transferAction(context, request.getTargetId(), request.getToUserId(),
                request.getReason(), support.actorId(session), support.has(session, WorkflowPermissions.INSTANCE_MANAGE),
                support::nextId),
            new WorkflowInstanceCommands.AuditResource("WORKFLOW_ACTION_RUN", request.getTargetId(), "TRANSFERRED"),
            context -> new WorkflowMutation(WorkflowMutation.INSTANCE, context.getInstance().getInstanceId(),
                context.getInstance().getStatus().getCode(), context.getInstance().getVersion()));
    }
}
