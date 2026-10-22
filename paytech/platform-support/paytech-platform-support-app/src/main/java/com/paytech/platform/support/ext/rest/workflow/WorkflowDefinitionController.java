package com.paytech.platform.support.ext.rest.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.workflow.WorkflowDefinitionManagementApi;
import com.paytech.platform.support.api.workflow.request.ChangeWorkflowStatusRequestBody;
import com.paytech.platform.support.api.workflow.request.CreateWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.PublishWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.SaveWorkflowDraftRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDraftResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowVersionBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.workflow.service.processor.ChangeWorkflowDefinitionStatusProcessor;
import com.paytech.platform.support.workflow.service.processor.CreateWorkflowDefinitionProcessor;
import com.paytech.platform.support.workflow.service.processor.PublishWorkflowDefinitionProcessor;
import com.paytech.platform.support.workflow.service.processor.SaveWorkflowDraftProcessor;
import com.paytech.platform.support.workflow.service.processor.WorkflowQueryProcessor;
import com.paytech.platform.support.workflow.service.processor.request.PublishDefinitionRequest;

import lombok.AllArgsConstructor;

/** 流程定义的协议适配器；规则、授权与事务委托 workflow 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowDefinitionController implements WorkflowDefinitionManagementApi {

    private final WorkflowConverter converter;
    private final WorkflowQueryProcessor queries;
    private final SaveWorkflowDraftProcessor draftProcessor;
    private final CreateWorkflowDefinitionProcessor createProcessor;
    private final PublishWorkflowDefinitionProcessor publishProcessor;
    private final ChangeWorkflowDefinitionStatusProcessor statusProcessor;

    /** 分页查询。 */
    @Override
    public PagingResponseBody<WorkflowDefinitionSummaryBody> listDefinitions(String actor, String session, String status,
        String keyword, Integer page, Integer size) {
        return queries.listDefinitions(actor, session, status, keyword, false, PagingRequestBody.of(page, size));
    }

    /** 详情。 */
    @Override
    public WorkflowDefinitionDetailResponseBody getDefinition(String actor, String session, String definitionId) {
        return queries.getDefinition(actor, session, converter.id(definitionId, "definitionId"));
    }

    /** 新建。 */
    @Audit(action = "OPERATIONS_WORKFLOW_DEFINITION_CREATE", resourceType = "WORKFLOW_DEFINITION",
        fallbackResourceId = "WORKFLOW_DEFINITION_CATALOG")
    @Override
    public WorkflowMutationResponseBody createDefinition(String actor, String session,
        CreateWorkflowDefinitionRequestBody request) {
        return converter.mutation(createProcessor.process(converter.createDefinition(actor, session, request)));
    }

    /** 保存草稿。 */
    @Audit(action = "OPERATIONS_WORKFLOW_DRAFT_SAVE", resourceType = "WORKFLOW_DEFINITION",
        resourceIdParameter = "definitionId", fallbackResourceId = "WORKFLOW_DEFINITION_CATALOG")
    @Override
    public WorkflowDraftResponseBody saveDraft(String actor, String session, String definitionId,
        SaveWorkflowDraftRequestBody request) {
        return converter.draft(draftProcessor.process(converter.draft(actor, session, definitionId, request)));
    }

    /** 发布。 */
    @Audit(action = "OPERATIONS_WORKFLOW_PUBLISH", resourceType = "WORKFLOW_DEFINITION",
        resourceIdParameter = "definitionId", fallbackResourceId = "WORKFLOW_DEFINITION_CATALOG")
    @Override
    public WorkflowMutationResponseBody publishDefinition(String actor, String session, String definitionId,
        PublishWorkflowDefinitionRequestBody request) {
        return converter.mutation(publishProcessor.process(new PublishDefinitionRequest(actor, session,
            converter.id(definitionId, "definitionId"), request.getExpectedVersion())));
    }

    /** 启停。 */
    @Audit(action = "OPERATIONS_WORKFLOW_DEFINITION_STATUS", resourceType = "WORKFLOW_DEFINITION",
        resourceIdParameter = "definitionId", fallbackResourceId = "WORKFLOW_DEFINITION_CATALOG")
    @Override
    public WorkflowMutationResponseBody changeDefinitionStatus(String actor, String session, String definitionId,
        ChangeWorkflowStatusRequestBody request) {
        return converter.mutation(statusProcessor.process(converter.status(actor, session, definitionId, request)));
    }

    /** 查看版本。 */
    @Override
    public WorkflowVersionBody getVersion(String actor, String session, String definitionId, Integer versionNo) {
        return queries.getVersion(actor, session, converter.id(definitionId, "definitionId"), versionNo);
    }
}
