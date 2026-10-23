package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
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
import com.paytech.platform.support.api.files.response.FileDownloadResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadCompletionResponseBody;
import com.paytech.platform.support.api.files.response.FileUploadResponseBody;
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

/**
 * Platform 工作流的本地端口：流程、实例、待办与人员事实及 WORKFLOW_* 权限的再次校验归 Platform
 * <li>全部入口以会话 Account ID 与 Session ID 作为操作人 Header 转发（工作流查询也按参与人收窄，需要操作人）。</li>
 * <li>每个方法只调用一次 Platform，同路径转发，不聚合。</li>
 */
public interface PlatformWorkflowClient {

    /** 动作模板分页。 */
    PagingResponseBody<WorkflowActionTemplateResponseBody> listActionTemplates(OperatorContext context,
        String actionType, String status, String keyword, Integer page, Integer size);

    /** 动作模板详情。 */
    WorkflowActionTemplateResponseBody getActionTemplate(OperatorContext context, String templateId);

    /** 引用动作模板的已发布版本。 */
    WorkflowTemplateReferenceListResponseBody listTemplateReferences(OperatorContext context, String templateId);

    /** 新建动作模板。 */
    WorkflowMutationResponseBody createActionTemplate(OperatorContext context,
        CreateWorkflowActionTemplateRequestBody request);

    /** 修改动作模板。 */
    WorkflowMutationResponseBody updateActionTemplate(OperatorContext context, String templateId,
        UpdateWorkflowActionTemplateRequestBody request);

    /** 启停动作模板。 */
    WorkflowMutationResponseBody changeActionTemplateStatus(OperatorContext context, String templateId,
        ChangeWorkflowStatusRequestBody request);

    /** 流程定义分页。 */
    PagingResponseBody<WorkflowDefinitionSummaryBody> listDefinitions(OperatorContext context, String status,
        String keyword, Integer page, Integer size);

    /** 流程定义详情。 */
    WorkflowDefinitionDetailResponseBody getDefinition(OperatorContext context, String definitionId);

    /** 新建流程定义。 */
    WorkflowMutationResponseBody createDefinition(OperatorContext context, CreateWorkflowDefinitionRequestBody request);

    /** 保存草稿。 */
    WorkflowDraftResponseBody saveDraft(OperatorContext context, String definitionId,
        SaveWorkflowDraftRequestBody request);

    /** 发布草稿。 */
    WorkflowMutationResponseBody publishDefinition(OperatorContext context, String definitionId,
        PublishWorkflowDefinitionRequestBody request);

    /** 启停流程定义。 */
    WorkflowMutationResponseBody changeDefinitionStatus(OperatorContext context, String definitionId,
        ChangeWorkflowStatusRequestBody request);

    /** 查看某个版本。 */
    WorkflowVersionBody getVersion(OperatorContext context, String definitionId, Integer versionNo);

    /** 可发起流程分页。 */
    PagingResponseBody<WorkflowDefinitionSummaryBody> listLaunchableDefinitions(OperatorContext context,
        String keyword, Integer page, Integer size);

    /** 可发起流程的当前发布版本。 */
    WorkflowLaunchResponseBody getLaunchableDefinition(OperatorContext context, String definitionId);

    /** 发起实例。 */
    WorkflowMutationResponseBody startInstance(OperatorContext context, StartWorkflowInstanceRequestBody request);

    /** 实例分页。 */
    PagingResponseBody<WorkflowInstanceSummaryBody> listInstances(OperatorContext context, String scope,
        String definitionId, String status, String startedBy, String businessKey, String keyword, Integer page,
        Integer size);

    /** 实例详情。 */
    WorkflowInstanceDetailResponseBody getInstance(OperatorContext context, String instanceId);

    /** 实例活动记录。 */
    WorkflowActivityListResponseBody listActivities(OperatorContext context, String instanceId);

    /** 终止实例。 */
    WorkflowMutationResponseBody terminateInstance(OperatorContext context, String instanceId,
        TerminateWorkflowInstanceRequestBody request);

    /** 提交动作。 */
    WorkflowSubmissionResponseBody submitAction(OperatorContext context, String actionRunId,
        SubmitWorkflowActionRequestBody request);

    /** 开始上传附件。 */
    FileUploadResponseBody startAttachmentUpload(OperatorContext context,
        StartWorkflowAttachmentUploadRequestBody request);

    /** 完成上传附件。 */
    FileUploadCompletionResponseBody completeAttachmentUpload(OperatorContext context, String uploadId);

    /** 签发实例附件的下载授权；disposition 可空，由 Platform 按 ATTACHMENT。 */
    FileDownloadResponseBody authorizeAttachmentDownload(OperatorContext context, String instanceId, String fileId,
        String disposition);

    /** 转交动作。 */
    WorkflowMutationResponseBody transferAction(OperatorContext context, String actionRunId,
        TransferWorkflowTaskRequestBody request);

    /** 审批决定。 */
    WorkflowMutationResponseBody decideApproval(OperatorContext context, String approvalId,
        DecideWorkflowApprovalRequestBody request);

    /** 转交审批。 */
    WorkflowMutationResponseBody transferApproval(OperatorContext context, String approvalId,
        TransferWorkflowTaskRequestBody request);

    /** 我的待办。 */
    PagingResponseBody<WorkflowTaskBody> listMyTasks(OperatorContext context, String taskType, String keyword,
        Integer page, Integer size);

    /** 我已处理。 */
    PagingResponseBody<WorkflowHandledTaskBody> listHandledTasks(OperatorContext context, String taskType,
        String keyword, Integer page, Integer size);

    /** 手动重试通知回调。 */
    WorkflowMutationResponseBody retryCallback(OperatorContext context, String callbackId);

    /** 选人。 */
    WorkflowAssigneeCandidateListResponseBody listAssigneeCandidates(OperatorContext context, String keyword);
}
