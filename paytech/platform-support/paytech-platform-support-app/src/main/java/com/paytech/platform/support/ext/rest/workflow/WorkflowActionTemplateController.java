package com.paytech.platform.support.ext.rest.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.workflow.WorkflowActionTemplateManagementApi;
import com.paytech.platform.support.api.workflow.request.ChangeWorkflowStatusRequestBody;
import com.paytech.platform.support.api.workflow.request.CreateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.request.UpdateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.request.ValidateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateValidationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTemplateReferenceListResponseBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.workflow.core.enums.WorkflowActionType;
import com.paytech.platform.support.workflow.service.processor.ChangeWorkflowActionTemplateStatusProcessor;
import com.paytech.platform.support.workflow.service.processor.CreateWorkflowActionTemplateProcessor;
import com.paytech.platform.support.workflow.service.processor.UpdateWorkflowActionTemplateProcessor;
import com.paytech.platform.support.workflow.service.processor.WorkflowQueryProcessor;

import lombok.AllArgsConstructor;

/** 动作目录的协议适配器；规则、授权与事务委托 workflow 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowActionTemplateController implements WorkflowActionTemplateManagementApi {

    private final WorkflowConverter converter;
    private final WorkflowQueryProcessor queries;
    private final CreateWorkflowActionTemplateProcessor createProcessor;
    private final UpdateWorkflowActionTemplateProcessor updateProcessor;
    private final ChangeWorkflowActionTemplateStatusProcessor statusProcessor;

    /** 分页查询。 */
    @Override
    public PagingResponseBody<WorkflowActionTemplateResponseBody> listActionTemplates(String actor, String session,
        String actionType, String status, String keyword, Integer page, Integer size) {
        return queries.listTemplates(actor, session, actionType, status, keyword, PagingRequestBody.of(page, size));
    }

    /** 详情。 */
    @Override
    public WorkflowActionTemplateResponseBody getActionTemplate(String actor, String session, String templateId) {
        return queries.getTemplate(actor, session, converter.id(templateId, "templateId"));
    }

    /** 引用该模板的已发布版本。 */
    @Override
    public WorkflowTemplateReferenceListResponseBody listTemplateReferences(String actor, String session,
        String templateId) {
        return queries.listTemplateReferences(actor, session, converter.id(templateId, "templateId"));
    }

    /** 校验配置，不保存。 */
    @Override
    public WorkflowActionTemplateValidationResponseBody validateActionTemplate(String actor, String session,
        ValidateWorkflowActionTemplateRequestBody request) {
        return queries.validateTemplate(actor, session, EnumUtils.getEnumByCode(WorkflowActionType.class,
            request.getActionType()), converter.object(request.getConfig(), "config"));
    }

    /** 新建。 */
    @Audit(action = "OPERATIONS_WORKFLOW_TEMPLATE_CREATE", resourceType = "WORKFLOW_ACTION_TEMPLATE",
        fallbackResourceId = "WORKFLOW_ACTION_TEMPLATE_CATALOG")
    @Override
    public WorkflowMutationResponseBody createActionTemplate(String actor, String session,
        CreateWorkflowActionTemplateRequestBody request) {
        return converter.mutation(createProcessor.process(converter.createTemplate(actor, session, request)));
    }

    /** 修改。 */
    @Audit(action = "OPERATIONS_WORKFLOW_TEMPLATE_UPDATE", resourceType = "WORKFLOW_ACTION_TEMPLATE",
        resourceIdParameter = "templateId", fallbackResourceId = "WORKFLOW_ACTION_TEMPLATE_CATALOG")
    @Override
    public WorkflowMutationResponseBody updateActionTemplate(String actor, String session, String templateId,
        UpdateWorkflowActionTemplateRequestBody request) {
        return converter.mutation(updateProcessor.process(converter.updateTemplate(actor, session, templateId, request)));
    }

    /** 启停。 */
    @Audit(action = "OPERATIONS_WORKFLOW_TEMPLATE_STATUS", resourceType = "WORKFLOW_ACTION_TEMPLATE",
        resourceIdParameter = "templateId", fallbackResourceId = "WORKFLOW_ACTION_TEMPLATE_CATALOG")
    @Override
    public WorkflowMutationResponseBody changeActionTemplateStatus(String actor, String session, String templateId,
        ChangeWorkflowStatusRequestBody request) {
        return converter.mutation(statusProcessor.process(converter.status(actor, session, templateId, request)));
    }
}
