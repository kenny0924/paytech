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
 * 转交审批（R6）：当前审批人或有 WORKFLOW_INSTANCE_MANAGE 的人把待决定审批转给另一位 ACTIVE 的 Operations 人员；不能转给同一节点的其他审批人。
 * <li>锁 workflow-instance:{id}（TRY_LOCK），保存时实例版本 CAS 与各行条件更新，冲突返回 409。</li>
 */
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class TransferWorkflowApprovalProcessor extends AbstractLockProcessor<TransferTaskRequest, WorkflowMutation> {

    private final WorkflowProcessorSupport support;
    private final WorkflowInstanceCommands commands;
    private final WorkflowRuntimeEngine engine = new WorkflowRuntimeEngine();

    /** 按所属实例加锁。 */
    @Override
    protected LockCommand lockCommand(TransferTaskRequest request) {
        return LockCommand.tryLock(WorkflowInstanceCommands.lock(commands.instanceOfApproval(request.getTargetId())), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected WorkflowMutation processWithinLock(TransferTaskRequest request) {
        support.requireActive(List.of(request.getToUserId()), "新审批人");
        return commands.run(request.getActor(), request.getSession(), commands.instanceOfApproval(request.getTargetId()),
            (session, context) -> engine.transferApproval(context, request.getTargetId(), request.getToUserId(),
                request.getReason(), support.actorId(session), support.has(session, WorkflowPermissions.INSTANCE_MANAGE),
                support::nextId),
            new WorkflowInstanceCommands.AuditResource("WORKFLOW_APPROVAL", request.getTargetId(), "TRANSFERRED"),
            context -> new WorkflowMutation(WorkflowMutation.INSTANCE, context.getInstance().getInstanceId(),
                context.getInstance().getStatus().getCode(), context.getInstance().getVersion()));
    }
}
