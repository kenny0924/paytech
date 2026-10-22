package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionTemplateRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowDefinitionService;
import com.paytech.platform.support.workflow.service.processor.request.ChangeWorkflowStatusRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.AllArgsConstructor;

/**
 * 按原版本启用或停用动作模板；停用后新发布的流程不能引用，已发布版本照常运行
 * <li>与修改共用 workflow-action-template:{id} 资源锁，保存时版本 CAS。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ChangeWorkflowActionTemplateStatusProcessor
    extends AbstractLockProcessor<ChangeWorkflowStatusRequest, WorkflowMutation> {

    private final WorkflowProcessorSupport support;
    private final WorkflowDefinitionService service;
    private final WorkflowActionTemplateRepository templates;

    /** 与修改共用模板资源锁。 */
    @Override
    protected LockCommand lockCommand(ChangeWorkflowStatusRequest request) {
        return LockCommand.tryLock(UpdateWorkflowActionTemplateProcessor.templateLock(request.getTargetId()),
            support.lockWaitMillis(), support.lockLeaseMillis());
    }

    @Override
    protected WorkflowMutation processWithinLock(ChangeWorkflowStatusRequest request) {
        return support.write(() -> {
            var session = support.authorize(request.getActor(), request.getSession(),
                WorkflowPermissions.ACTION_TEMPLATE_MANAGE);
            WorkflowActionTemplate row = templates.find(request.getTargetId());
            if (row == null) {
                throw support.notFound();
            }
            if (row.getVersion() != request.getExpectedVersion().intValue()) {
                throw new ConcurrentRequestException("动作模板已被其他操作修改，请刷新后重试，当前版本 " + row.getVersion());
            }
            row.setStatus(request.getActive() ? ActionTemplateStatus.ACTIVE : ActionTemplateStatus.INACTIVE);
            row.setUpdatedBy(support.actorId(session));
            row.setUpdateTime(support.now());
            service.updateTemplate(row);
            support.succeeded(session, WorkflowMutation.TEMPLATE, row.getActionTemplateId(), row.getStatus().getCode());
            return new WorkflowMutation(WorkflowMutation.TEMPLATE, row.getActionTemplateId(), row.getStatus().getCode(),
                row.getVersion());
        });
    }
}
