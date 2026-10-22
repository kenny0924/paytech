package com.paytech.platform.support.ext.rest.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.workflow.WorkflowInstanceApi;
import com.paytech.platform.support.api.workflow.request.DecideWorkflowApprovalRequestBody;
import com.paytech.platform.support.api.workflow.request.StartWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.request.SubmitWorkflowActionRequestBody;
import com.paytech.platform.support.api.workflow.request.TerminateWorkflowInstanceRequestBody;
import com.paytech.platform.support.api.workflow.request.TransferWorkflowTaskRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActivityListResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowAssigneeCandidateListResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowHandledTaskBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowInstanceSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowLaunchResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowSubmissionResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTaskBody;
import com.paytech.platform.support.ext.rest.audit.Audit;
import com.paytech.platform.support.workflow.service.processor.DecideWorkflowApprovalProcessor;
import com.paytech.platform.support.workflow.service.processor.RetryWorkflowCallbackProcessor;
import com.paytech.platform.support.workflow.service.processor.ReverifyWorkflowActionProcessor;
import com.paytech.platform.support.workflow.service.processor.SearchWorkflowAssigneesProcessor;
import com.paytech.platform.support.workflow.service.processor.StartWorkflowInstanceProcessor;
import com.paytech.platform.support.workflow.service.processor.SubmitWorkflowActionProcessor;
import com.paytech.platform.support.workflow.service.processor.TerminateWorkflowInstanceProcessor;
import com.paytech.platform.support.workflow.service.processor.TransferWorkflowActionProcessor;
import com.paytech.platform.support.workflow.service.processor.TransferWorkflowApprovalProcessor;
import com.paytech.platform.support.workflow.service.processor.WorkflowQueryProcessor;
import com.paytech.platform.support.workflow.service.processor.request.RetryCallbackRequest;
import com.paytech.platform.support.workflow.service.processor.request.ReverifyActionRequest;
import com.paytech.platform.support.workflow.service.processor.request.SubmitActionRequest;
import com.paytech.platform.support.workflow.service.processor.request.TerminateInstanceRequest;

import lombok.AllArgsConstructor;

/** 工作流运行的协议适配器；规则、授权与事务委托 workflow 模块的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowInstanceController implements WorkflowInstanceApi {

    private final WorkflowConverter converter;
    private final WorkflowQueryProcessor queries;
    private final StartWorkflowInstanceProcessor startProcessor;
    private final SubmitWorkflowActionProcessor submitProcessor;
    private final ReverifyWorkflowActionProcessor reverifyProcessor;
    private final RetryWorkflowCallbackProcessor retryProcessor;
    private final DecideWorkflowApprovalProcessor decideProcessor;
    private final TransferWorkflowActionProcessor transferActionProcessor;
    private final TerminateWorkflowInstanceProcessor terminateProcessor;
    private final TransferWorkflowApprovalProcessor transferApprovalProcessor;
    private final SearchWorkflowAssigneesProcessor assigneeSearch;

    /** 可发起流程。 */
    @Override
    public PagingResponseBody<WorkflowDefinitionSummaryBody> listLaunchableDefinitions(String actor, String session,
        String keyword, Integer page, Integer size) {
        return queries.listDefinitions(actor, session, null, keyword, true, PagingRequestBody.of(page, size));
    }

    /** 可发起流程的当前版本。 */
    @Override
    public WorkflowLaunchResponseBody getLaunchableDefinition(String actor, String session, String definitionId) {
        return queries.getLaunchable(actor, session, converter.id(definitionId, "definitionId"));
    }

    /** 发起。 */
    @Audit(action = "OPERATIONS_WORKFLOW_INSTANCE_START", resourceType = "WORKFLOW_INSTANCE",
        fallbackResourceId = "WORKFLOW_INSTANCE")
    @Override
    public WorkflowMutationResponseBody startInstance(String actor, String session,
        StartWorkflowInstanceRequestBody request) {
        return converter.mutation(startProcessor.process(converter.start(actor, session, request)));
    }

    /** 实例列表。 */
    @Override
    public PagingResponseBody<WorkflowInstanceSummaryBody> listInstances(String actor, String session, String scope,
        String definitionId, String status, String startedBy, String businessKey, String keyword, Integer page,
        Integer size) {
        return queries.listInstances(actor, session, scope, definitionId, status, startedBy, businessKey, keyword,
            PagingRequestBody.of(page, size));
    }

    /** 实例详情。 */
    @Override
    public WorkflowInstanceDetailResponseBody getInstance(String actor, String session, String instanceId) {
        return queries.getInstance(actor, session, converter.id(instanceId, "instanceId"));
    }

    /** 活动记录。 */
    @Override
    public WorkflowActivityListResponseBody listActivities(String actor, String session, String instanceId) {
        return queries.listActivities(actor, session, converter.id(instanceId, "instanceId"));
    }

    /** 终止。 */
    @Audit(action = "OPERATIONS_WORKFLOW_INSTANCE_TERMINATE", resourceType = "WORKFLOW_INSTANCE",
        resourceIdParameter = "instanceId", fallbackResourceId = "WORKFLOW_INSTANCE")
    @Override
    public WorkflowMutationResponseBody terminateInstance(String actor, String session, String instanceId,
        TerminateWorkflowInstanceRequestBody request) {
        return converter.mutation(terminateProcessor.process(new TerminateInstanceRequest(actor, session,
            converter.id(instanceId, "instanceId"), request.getReason().strip())));
    }

    /** 提交动作。 */
    @Audit(action = "OPERATIONS_WORKFLOW_ACTION_SUBMIT", resourceType = "WORKFLOW_ACTION_RUN",
        resourceIdParameter = "actionRunId", fallbackResourceId = "WORKFLOW_ACTION_RUN")
    @Override
    public WorkflowSubmissionResponseBody submitAction(String actor, String session, String actionRunId,
        SubmitWorkflowActionRequestBody request) {
        converter.object(request.getOutput(), "output");
        return converter.submission(submitProcessor.process(new SubmitActionRequest(actor, session,
            converter.id(actionRunId, "actionRunId"), request.getOutput())));
    }

    /** 重新验证动作。 */
    @Audit(action = "OPERATIONS_WORKFLOW_ACTION_REVERIFY", resourceType = "WORKFLOW_ACTION_RUN",
        resourceIdParameter = "actionRunId", fallbackResourceId = "WORKFLOW_ACTION_RUN")
    @Override
    public WorkflowSubmissionResponseBody reverifyAction(String actor, String session, String actionRunId) {
        return converter.submission(reverifyProcessor.process(new ReverifyActionRequest(actor, session,
            converter.id(actionRunId, "actionRunId"))));
    }

    /** 转交动作。 */
    @Audit(action = "OPERATIONS_WORKFLOW_ACTION_TRANSFER", resourceType = "WORKFLOW_ACTION_RUN",
        resourceIdParameter = "actionRunId", fallbackResourceId = "WORKFLOW_ACTION_RUN")
    @Override
    public WorkflowMutationResponseBody transferAction(String actor, String session, String actionRunId,
        TransferWorkflowTaskRequestBody request) {
        return converter.mutation(transferActionProcessor.process(converter.transfer(actor, session, actionRunId,
            request)));
    }

    /** 审批决定。 */
    @Audit(action = "OPERATIONS_WORKFLOW_APPROVAL_DECIDE", resourceType = "WORKFLOW_APPROVAL",
        resourceIdParameter = "approvalId", fallbackResourceId = "WORKFLOW_APPROVAL")
    @Override
    public WorkflowMutationResponseBody decideApproval(String actor, String session, String approvalId,
        DecideWorkflowApprovalRequestBody request) {
        return converter.mutation(decideProcessor.process(converter.decide(actor, session, approvalId, request)));
    }

    /** 转交审批。 */
    @Audit(action = "OPERATIONS_WORKFLOW_APPROVAL_TRANSFER", resourceType = "WORKFLOW_APPROVAL",
        resourceIdParameter = "approvalId", fallbackResourceId = "WORKFLOW_APPROVAL")
    @Override
    public WorkflowMutationResponseBody transferApproval(String actor, String session, String approvalId,
        TransferWorkflowTaskRequestBody request) {
        return converter.mutation(transferApprovalProcessor.process(converter.transfer(actor, session, approvalId,
            request)));
    }

    /** 我的待办。 */
    @Override
    public PagingResponseBody<WorkflowTaskBody> listMyTasks(String actor, String session, String taskType,
        String keyword, Integer page, Integer size) {
        return queries.myTasks(actor, session, taskType, keyword, PagingRequestBody.of(page, size));
    }

    /** 我已处理。 */
    @Override
    public PagingResponseBody<WorkflowHandledTaskBody> listHandledTasks(String actor, String session, String taskType,
        String keyword, Integer page, Integer size) {
        return queries.handledTasks(actor, session, taskType, keyword, PagingRequestBody.of(page, size));
    }

    /** 手动重试通知回调。 */
    @Audit(action = "OPERATIONS_WORKFLOW_CALLBACK_RETRY", resourceType = "WORKFLOW_CALLBACK",
        resourceIdParameter = "callbackId", fallbackResourceId = "WORKFLOW_CALLBACK")
    @Override
    public WorkflowMutationResponseBody retryCallback(String actor, String session, String callbackId) {
        return converter.mutation(retryProcessor.process(new RetryCallbackRequest(actor, session,
            converter.id(callbackId, "callbackId"))));
    }

    /** 选人。 */
    @Override
    public WorkflowAssigneeCandidateListResponseBody listAssigneeCandidates(String actor, String session,
        String keyword) {
        return assigneeSearch.search(actor, session, keyword);
    }
}
