package com.paytech.platform.support.workflow.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.workflow.core.common.WorkflowPermissions;
import com.paytech.platform.support.workflow.core.dal.dataobject.WorkflowActionTemplate;
import com.paytech.platform.support.workflow.core.enums.ActionTemplateStatus;
import com.paytech.platform.support.workflow.domainservice.repository.WorkflowActionTemplateRepository;
import com.paytech.platform.support.workflow.domainservice.service.WorkflowDefinitionService;
import com.paytech.platform.support.workflow.service.processor.request.CreateActionTemplateRequest;
import com.paytech.platform.support.workflow.service.processor.response.WorkflowMutation;

import lombok.AllArgsConstructor;

/**
 * 新建动作模板（ACTIVE）
 * <li>配置按类型解析与校验，不合法拒绝；编码已存在拒绝，唯一索引为最终保护。</li>
 * <li>新建记录没有可竞争的资源，不加锁；权限重检与保存在同一本地事务。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class CreateWorkflowActionTemplateProcessor extends AbstractProcessor<CreateActionTemplateRequest, WorkflowMutation> {

    private final WorkflowProcessorSupport support;
    private final WorkflowTemplateConfigs configs;
    private final WorkflowDefinitionService service;
    private final WorkflowActionTemplateRepository templates;

    @Override
    protected WorkflowMutation processRequest(CreateActionTemplateRequest request) {
        // 配置校验（纯内存）
        configs.requireValid(request.getActionType(), request.getConfigJson());
        try {
            return support.write(() -> save(request));
        } catch (DuplicateKeyException duplicate) {
            throw new BizCheckFailException("动作模板编码已存在：" + request.getTemplateCode());
        }
    }

    /** 权限重检、编码查重后保存。 */
    WorkflowMutation save(CreateActionTemplateRequest request) {
        var session = support.authorize(request.getActor(), request.getSession(), WorkflowPermissions.ACTION_TEMPLATE_MANAGE);
        if (templates.findByCode(request.getTemplateCode()) != null) {
            throw new BizCheckFailException("动作模板编码已存在：" + request.getTemplateCode());
        }
        var now = support.now();
        WorkflowActionTemplate row = new WorkflowActionTemplate();
        row.setActionTemplateId(support.nextId());
        row.setTemplateCode(request.getTemplateCode());
        row.setTemplateName(request.getTemplateName());
        row.setActionType(request.getActionType());
        row.setDescription(request.getDescription());
        row.setConfigJson(request.getConfigJson());
        row.setStatus(ActionTemplateStatus.ACTIVE);
        row.setVersion(1);
        row.setCreatedBy(support.actorId(session));
        row.setUpdatedBy(support.actorId(session));
        row.setCreateTime(now);
        row.setUpdateTime(now);
        service.createTemplate(row);
        support.succeeded(session, WorkflowMutation.TEMPLATE, row.getActionTemplateId(), "CREATED");
        return new WorkflowMutation(WorkflowMutation.TEMPLATE, row.getActionTemplateId(), row.getStatus().getCode(), 1);
    }
}
