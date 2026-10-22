package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowDefinition;
import com.paytech.platform.support.workflow.core.enums.WorkflowDefinitionStatus;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowDefinitionRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowDefinitionService;
import com.paytech.platform.support.workflow.service.processor.request.ChangeWorkflowStatusRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.AllArgsConstructor;

/**
 * 按原版本启用或停用流程定义（Q4）：停用后不能发起新实例，在途实例照常运行
 * <li>与保存草稿、发布共用 workflow-definition:{id} 资源锁，保存时版本 CAS。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeWorkflowDefinitionStatusProcessor
    extends AbstractLockProcessor<ChangeWorkflowStatusRequest, WorkflowMutation> {

    private final WorkflowProcessorSupport support;
    private final WorkflowDefinitionService service;
    private final WorkflowDefinitionRepository definitions;

    /** 与保存草稿、发布共用定义资源锁。 */
    @Override
    protected LockCommand lockCommand(ChangeWorkflowStatusRequest request) {
        return LockCommand.tryLock(WorkflowDefinitionLocks.lock(request.getTargetId()), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected WorkflowMutation processWithinLock(ChangeWorkflowStatusRequest request) {
        return support.write(() -> {
            var session = support.authorize(request.getActor(), request.getSession(),
                WorkflowPermissions.DEFINITION_MANAGE);
            WorkflowDefinition definition = definitions.find(request.getTargetId());
            if (definition == null) {
                throw support.notFound();
            }
            WorkflowDefinitionLocks.requireVersion(definition, request.getExpectedVersion());
            definition.setStatus(request.getActive() ? WorkflowDefinitionStatus.ACTIVE : WorkflowDefinitionStatus.INACTIVE);
            definition.setUpdatedBy(support.actorId(session));
            definition.setUpdateTime(support.now());
            service.changeStatus(definition);
            support.succeeded(session, WorkflowMutation.DEFINITION, definition.getDefinitionId(),
                definition.getStatus().getCode());
            return new WorkflowMutation(WorkflowMutation.DEFINITION, definition.getDefinitionId(),
                definition.getStatus().getCode(), definition.getVersion());
        });
    }
}
