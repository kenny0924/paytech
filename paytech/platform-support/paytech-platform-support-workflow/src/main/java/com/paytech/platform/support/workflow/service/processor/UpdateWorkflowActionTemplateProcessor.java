package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ConcurrentRequestException;
import com.paytech.foundation.redis.core.processor.AbstractLockProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionTemplateRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowDefinitionService;
import com.paytech.platform.support.workflow.service.processor.request.UpdateActionTemplateRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.AllArgsConstructor;

/**
 * 按原版本修改动作模板的名称、说明与配置；类型与编码不可改
 * <li>锁 workflow-action-template:{id}（TRY_LOCK），保存时版本 CAS；已发布流程使用冻结快照不受影响。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class UpdateWorkflowActionTemplateProcessor
    extends AbstractLockProcessor<UpdateActionTemplateRequest, WorkflowMutation> {

    private final WorkflowProcessorSupport support;
    private final WorkflowTemplateConfigs configs;
    private final WorkflowDefinitionService service;
    private final WorkflowActionTemplateRepository templates;

    /** 与启停共用模板资源锁。 */
    @Override
    protected LockCommand lockCommand(UpdateActionTemplateRequest request) {
        return LockCommand.tryLock(templateLock(request.getTemplateId()), support.lockWaitMillis(),
            support.lockLeaseMillis());
    }

    @Override
    protected WorkflowMutation processWithinLock(UpdateActionTemplateRequest request) {
        return support.write(() -> {
            var session = support.authorize(request.getActor(), request.getSession(),
                WorkflowPermissions.ACTION_TEMPLATE_MANAGE);
            WorkflowActionTemplate row = lockedTemplate(request.getTemplateId(), request.getExpectedVersion());
            configs.requireValid(row.getActionType(), request.getConfigJson());
            row.setTemplateName(request.getTemplateName());
            row.setDescription(request.getDescription());
            row.setConfigJson(request.getConfigJson());
            row.setUpdatedBy(support.actorId(session));
            row.setUpdateTime(support.now());
            service.updateTemplate(row);
            support.succeeded(session, WorkflowMutation.TEMPLATE, row.getActionTemplateId(), "UPDATED");
            return new WorkflowMutation(WorkflowMutation.TEMPLATE, row.getActionTemplateId(), row.getStatus().getCode(),
                row.getVersion());
        });
    }

    /** 读取并核对版本；不存在为 404，版本不符为 409。 */
    WorkflowActionTemplate lockedTemplate(long templateId, int expectedVersion) {
        WorkflowActionTemplate row = templates.find(templateId);
        if (row == null) {
            throw support.notFound();
        }
        if (row.getVersion() != expectedVersion) {
            throw new ConcurrentRequestException("动作模板已被其他操作修改，请刷新后重试，当前版本 " + row.getVersion());
        }
        return row;
    }

    /** 模板资源锁 Key。 */
    static String templateLock(long templateId) {
        return "workflow-action-template:" + templateId;
    }
}
