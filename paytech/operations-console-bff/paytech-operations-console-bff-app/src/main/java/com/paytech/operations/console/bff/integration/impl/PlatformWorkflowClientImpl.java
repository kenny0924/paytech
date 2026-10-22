package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.WorkflowContractConverter.mutation;
import static com.paytech.operations.console.bff.integration.impl.WorkflowContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.WorkflowContractConverter.toPlatform;
import static com.paytech.operations.console.bff.integration.impl.WorkflowContractConverter.write;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformWorkflowClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.WorkflowModels.ChangeWorkflowStatusRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.CreateWorkflowActionTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.CreateWorkflowDefinitionRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.DecideWorkflowApprovalRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.PublishWorkflowDefinitionRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.SaveWorkflowDraftRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.StartWorkflowAttachmentUploadRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.StartWorkflowInstanceRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.SubmitWorkflowActionRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.TerminateWorkflowInstanceRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.TransferWorkflowTaskRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.UpdateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.WorkflowActionTemplateManagementApi;
import com.paytech.platform.support.api.workflow.WorkflowDefinitionManagementApi;
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
import com.paytech.platform.support.api.workflow.WorkflowAttachmentApi;
import com.paytech.platform.support.api.workflow.WorkflowInstanceApi;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActivityListResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowAssigneeCandidateListResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDraftResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowHandledTaskBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowLaunchResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowSubmissionResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTaskBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTemplateReferenceListResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowVersionBody;

import lombok.AllArgsConstructor;

/**
 * Platform 工作流的 HTTP 转发：每个入口只调用一次 Platform，不重试，请求幂等由 Gateway 负责
 * <li>会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id。</li>
 * <li>写回执核对对象类型与路径对象 ID；422 字段路径投影到本次请求体。</li>
 */
@Component
@AllArgsConstructor
public class PlatformWorkflowClientImpl implements PlatformWorkflowClient {

    private static final String TEMPLATE = "WORKFLOW_ACTION_TEMPLATE";
    private static final String DEFINITION = "WORKFLOW_DEFINITION";
    private static final String INSTANCE = "WORKFLOW_INSTANCE";
    private static final String CALLBACK = "WORKFLOW_CALLBACK";

    private final WorkflowActionTemplateManagementApi templates;
    private final WorkflowDefinitionManagementApi definitions;
    private final WorkflowInstanceApi runtime;
    private final WorkflowAttachmentApi attachments;

    @Override
    public PagingResponseBody<WorkflowActionTemplateResponseBody> listActionTemplates(OperatorContext context,
        String actionType, String status, String keyword, Integer page, Integer size) {
        return require(templates.listActionTemplates(context.accountId(), context.sessionId(), actionType, status,
            keyword, page, size));
    }

    @Override
    public WorkflowActionTemplateResponseBody getActionTemplate(OperatorContext context, String templateId) {
        return require(templates.getActionTemplate(context.accountId(), context.sessionId(), templateId));
    }

    @Override
    public WorkflowTemplateReferenceListResponseBody listTemplateReferences(OperatorContext context, String templateId) {
        WorkflowTemplateReferenceListResponseBody result = require(templates.listTemplateReferences(context.accountId(),
            context.sessionId(), templateId));
        if (!templateId.equals(result.getTemplateId())) {
            throw new com.paytech.foundation.common.error.ErrorException(
                com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return result;
    }

    @Override
    public WorkflowMutationResponseBody createActionTemplate(OperatorContext context,
        CreateWorkflowActionTemplateRequestBody request) {
        return mutation(write(request, () -> templates.createActionTemplate(context.accountId(), context.sessionId(),
            toPlatform(request, com.paytech.platform.support.api.workflow.request.CreateWorkflowActionTemplateRequestBody.class))),
            TEMPLATE, null);
    }

    @Override
    public WorkflowMutationResponseBody updateActionTemplate(OperatorContext context, String templateId,
        UpdateWorkflowActionTemplateRequestBody request) {
        return mutation(write(request, () -> templates.updateActionTemplate(context.accountId(), context.sessionId(),
            templateId, toPlatform(request,
                com.paytech.platform.support.api.workflow.request.UpdateWorkflowActionTemplateRequestBody.class))),
            TEMPLATE, templateId);
    }

    @Override
    public WorkflowMutationResponseBody changeActionTemplateStatus(OperatorContext context, String templateId,
        ChangeWorkflowStatusRequestBody request) {
        return mutation(write(request, () -> templates.changeActionTemplateStatus(context.accountId(),
            context.sessionId(), templateId, status(request))), TEMPLATE, templateId);
    }

    @Override
    public PagingResponseBody<WorkflowDefinitionSummaryBody> listDefinitions(OperatorContext context, String status,
        String keyword, Integer page, Integer size) {
        return require(definitions.listDefinitions(context.accountId(), context.sessionId(), status, keyword, page,
            size));
    }

    @Override
    public WorkflowDefinitionDetailResponseBody getDefinition(OperatorContext context, String definitionId) {
        return require(definitions.getDefinition(context.accountId(), context.sessionId(), definitionId));
    }

    @Override
    public WorkflowMutationResponseBody createDefinition(OperatorContext context,
        CreateWorkflowDefinitionRequestBody request) {
        return mutation(write(request, () -> definitions.createDefinition(context.accountId(), context.sessionId(),
            toPlatform(request, com.paytech.platform.support.api.workflow.request.CreateWorkflowDefinitionRequestBody.class))),
            DEFINITION, null);
    }

    @Override
    public WorkflowDraftResponseBody saveDraft(OperatorContext context, String definitionId,
        SaveWorkflowDraftRequestBody request) {
        WorkflowDraftResponseBody result = write(request, () -> definitions.saveDraft(context.accountId(),
            context.sessionId(), definitionId,
            toPlatform(request, com.paytech.platform.support.api.workflow.request.SaveWorkflowDraftRequestBody.class)));
        if (!definitionId.equals(result.getDefinitionId())) {
            throw new com.paytech.foundation.common.error.ErrorException(
                com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return result;
    }

    @Override
    public WorkflowMutationResponseBody publishDefinition(OperatorContext context, String definitionId,
        PublishWorkflowDefinitionRequestBody request) {
        return mutation(write(request, () -> definitions.publishDefinition(context.accountId(), context.sessionId(),
            definitionId, toPlatform(request,
                com.paytech.platform.support.api.workflow.request.PublishWorkflowDefinitionRequestBody.class))),
            DEFINITION, definitionId);
    }

    @Override
    public WorkflowMutationResponseBody changeDefinitionStatus(OperatorContext context, String definitionId,
        ChangeWorkflowStatusRequestBody request) {
        return mutation(write(request, () -> definitions.changeDefinitionStatus(context.accountId(),
            context.sessionId(), definitionId, status(request))), DEFINITION, definitionId);
    }

    @Override
    public WorkflowVersionBody getVersion(OperatorContext context, String definitionId, Integer versionNo) {
        return require(definitions.getVersion(context.accountId(), context.sessionId(), definitionId, versionNo));
    }

    @Override
    public PagingResponseBody<WorkflowDefinitionSummaryBody> listLaunchableDefinitions(OperatorContext context,
        String keyword, Integer page, Integer size) {
        return require(runtime.listLaunchableDefinitions(context.accountId(), context.sessionId(), keyword, page, size));
    }

    @Override
    public WorkflowLaunchResponseBody getLaunchableDefinition(OperatorContext context, String definitionId) {
        return require(runtime.getLaunchableDefinition(context.accountId(), context.sessionId(), definitionId));
    }

    @Override
    public WorkflowMutationResponseBody startInstance(OperatorContext context, StartWorkflowInstanceRequestBody request) {
        return mutation(write(request, () -> runtime.startInstance(context.accountId(), context.sessionId(),
            toPlatform(request, com.paytech.platform.support.api.workflow.request.StartWorkflowInstanceRequestBody.class))),
            INSTANCE, null);
    }

    @Override
    public PagingResponseBody<WorkflowInstanceSummaryBody> listInstances(OperatorContext context, String scope,
        String definitionId, String status, String startedBy, String businessKey, String keyword, Integer page,
        Integer size) {
        return require(runtime.listInstances(context.accountId(), context.sessionId(), scope, definitionId, status,
            startedBy, businessKey, keyword, page, size));
    }

    @Override
    public WorkflowInstanceDetailResponseBody getInstance(OperatorContext context, String instanceId) {
        return require(runtime.getInstance(context.accountId(), context.sessionId(), instanceId));
    }

    @Override
    public WorkflowActivityListResponseBody listActivities(OperatorContext context, String instanceId) {
        return require(runtime.listActivities(context.accountId(), context.sessionId(), instanceId));
    }

    @Override
    public WorkflowMutationResponseBody terminateInstance(OperatorContext context, String instanceId,
        TerminateWorkflowInstanceRequestBody request) {
        return mutation(write(request, () -> runtime.terminateInstance(context.accountId(), context.sessionId(),
            instanceId, toPlatform(request,
                com.paytech.platform.support.api.workflow.request.TerminateWorkflowInstanceRequestBody.class))),
            INSTANCE, instanceId);
    }

    @Override
    public WorkflowSubmissionResponseBody submitAction(OperatorContext context, String actionRunId,
        SubmitWorkflowActionRequestBody request) {
        WorkflowSubmissionResponseBody result = write(request, () -> runtime.submitAction(context.accountId(),
            context.sessionId(), actionRunId,
            toPlatform(request, com.paytech.platform.support.api.workflow.request.SubmitWorkflowActionRequestBody.class)));
        if (!actionRunId.equals(result.getActionRunId())) {
            throw new com.paytech.foundation.common.error.ErrorException(
                com.paytech.foundation.common.error.CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return result;
    }

    @Override
    public WorkflowSubmissionResponseBody reverifyAction(OperatorContext context, String actionRunId) {
        WorkflowSubmissionResponseBody result = require(runtime.reverifyAction(context.accountId(),
            context.sessionId(), actionRunId));
        if (!actionRunId.equals(result.getActionRunId())) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return result;
    }

    @Override
    public FileUploadResponseBody startAttachmentUpload(OperatorContext context,
        StartWorkflowAttachmentUploadRequestBody request) {
        FileUploadResponseBody result = write(request, () -> attachments.startUpload(context.accountId(),
            context.sessionId(), toPlatform(request, com.paytech.platform.support.api.workflow.request
                .StartWorkflowAttachmentUploadRequestBody.class)));
        if (result.getUploadId() == null || result.getAuthorization() == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return result;
    }

    @Override
    public FileUploadCompletionResponseBody completeAttachmentUpload(OperatorContext context, String uploadId) {
        FileUploadCompletionResponseBody result = require(attachments.completeUpload(context.accountId(),
            context.sessionId(), uploadId));
        if (!uploadId.equals(result.getUploadId()) || result.getFileId() == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return result;
    }

    @Override
    public FileDownloadResponseBody authorizeAttachmentDownload(OperatorContext context, String instanceId,
        String fileId, String disposition) {
        FileDownloadResponseBody result = require(attachments.authorizeDownload(context.accountId(),
            context.sessionId(), instanceId, fileId, disposition));
        if (result.getUrl() == null || result.getExpiresAt() == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE);
        }
        return result;
    }

    @Override
    public WorkflowMutationResponseBody transferAction(OperatorContext context, String actionRunId,
        TransferWorkflowTaskRequestBody request) {
        return mutation(write(request, () -> runtime.transferAction(context.accountId(), context.sessionId(),
            actionRunId, transfer(request))), INSTANCE, null);
    }

    @Override
    public WorkflowMutationResponseBody decideApproval(OperatorContext context, String approvalId,
        DecideWorkflowApprovalRequestBody request) {
        return mutation(write(request, () -> runtime.decideApproval(context.accountId(), context.sessionId(),
            approvalId, toPlatform(request,
                com.paytech.platform.support.api.workflow.request.DecideWorkflowApprovalRequestBody.class))),
            INSTANCE, null);
    }

    @Override
    public WorkflowMutationResponseBody transferApproval(OperatorContext context, String approvalId,
        TransferWorkflowTaskRequestBody request) {
        return mutation(write(request, () -> runtime.transferApproval(context.accountId(), context.sessionId(),
            approvalId, transfer(request))), INSTANCE, null);
    }

    @Override
    public PagingResponseBody<WorkflowTaskBody> listMyTasks(OperatorContext context, String taskType, String keyword,
        Integer page, Integer size) {
        return require(runtime.listMyTasks(context.accountId(), context.sessionId(), taskType, keyword, page, size));
    }

    @Override
    public PagingResponseBody<WorkflowHandledTaskBody> listHandledTasks(OperatorContext context, String taskType,
        String keyword, Integer page, Integer size) {
        return require(runtime.listHandledTasks(context.accountId(), context.sessionId(), taskType, keyword, page,
            size));
    }

    @Override
    public WorkflowMutationResponseBody retryCallback(OperatorContext context, String callbackId) {
        return mutation(runtime.retryCallback(context.accountId(), context.sessionId(), callbackId), CALLBACK,
            callbackId);
    }

    @Override
    public WorkflowAssigneeCandidateListResponseBody listAssigneeCandidates(OperatorContext context, String keyword) {
        return require(runtime.listAssigneeCandidates(context.accountId(), context.sessionId(), keyword));
    }

    private static com.paytech.platform.support.api.workflow.request.ChangeWorkflowStatusRequestBody status(
        ChangeWorkflowStatusRequestBody request) {
        return toPlatform(request, com.paytech.platform.support.api.workflow.request.ChangeWorkflowStatusRequestBody.class);
    }

    private static com.paytech.platform.support.api.workflow.request.TransferWorkflowTaskRequestBody transfer(
        TransferWorkflowTaskRequestBody request) {
        return toPlatform(request, com.paytech.platform.support.api.workflow.request.TransferWorkflowTaskRequestBody.class);
    }
}
