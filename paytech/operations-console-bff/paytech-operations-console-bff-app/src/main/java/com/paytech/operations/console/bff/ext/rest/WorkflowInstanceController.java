package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformWorkflowClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.WorkflowModels;
import com.paytech.operations.console.bff.service.model.WorkflowModels.DecideWorkflowApprovalRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.StartWorkflowInstanceRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.SubmitWorkflowActionRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.TerminateWorkflowInstanceRequestBody;
import com.paytech.operations.console.bff.service.model.WorkflowModels.TransferWorkflowTaskRequestBody;
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

import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;

/**
 * 工作流运行（发起、实例、待办、审批、转交、选人）的浏览器入口，全部简单转发 Platform 同路径
 * <li>每个路由由 Gateway 绑定 WORKFLOW_* 权限并经 PLT-I02 实时授权；参与人、指派人与实例管理权限由 Platform 判断。</li>
 * <li>无 Body 的命令（回调重试）沿用 BFF 写请求的 JSON Content-Type 与 Origin 要求。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/workflow/v1")
public class WorkflowInstanceController {

    private final PlatformWorkflowClient client;

    /** 可发起的流程。 */
    @GetMapping("/launchable-definitions")
    public PagingResponseBody<WorkflowDefinitionSummaryBody> listLaunchableDefinitions(OperatorContext context,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listLaunchableDefinitions(context, keyword, page, size);
    }

    /** 可发起流程的当前发布版本、启动表单与默认人员。 */
    @GetMapping("/launchable-definitions/{definitionId}")
    public WorkflowLaunchResponseBody getLaunchableDefinition(OperatorContext context,
        @PathVariable("definitionId") @Pattern(regexp = WorkflowModels.ID) String definitionId) {
        return client.getLaunchableDefinition(context, definitionId);
    }

    /** 发起实例。 */
    @PostMapping("/instances")
    public WorkflowMutationResponseBody startInstance(OperatorContext context,
        @Valid @RequestBody StartWorkflowInstanceRequestBody request) {
        return client.startInstance(context, request);
    }

    /** 实例分页：默认我参与的；scope=ALL 需实例查看权限。 */
    @GetMapping("/instances")
    public PagingResponseBody<WorkflowInstanceSummaryBody> listInstances(OperatorContext context,
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "definitionId", required = false) String definitionId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "startedBy", required = false) String startedBy,
        @RequestParam(value = "businessKey", required = false) String businessKey,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listInstances(context, scope, definitionId, status, startedBy, businessKey, keyword, page, size);
    }

    /** 实例详情。 */
    @GetMapping("/instances/{instanceId}")
    public WorkflowInstanceDetailResponseBody getInstance(OperatorContext context,
        @PathVariable("instanceId") @Pattern(regexp = WorkflowModels.ID) String instanceId) {
        return client.getInstance(context, instanceId);
    }

    /** 实例活动记录。 */
    @GetMapping("/instances/{instanceId}/activities")
    public WorkflowActivityListResponseBody listActivities(OperatorContext context,
        @PathVariable("instanceId") @Pattern(regexp = WorkflowModels.ID) String instanceId) {
        return client.listActivities(context, instanceId);
    }

    /** 终止实例。 */
    @PostMapping("/instances/{instanceId}/termination")
    public WorkflowMutationResponseBody terminateInstance(OperatorContext context,
        @PathVariable("instanceId") @Pattern(regexp = WorkflowModels.ID) String instanceId,
        @Valid @RequestBody TerminateWorkflowInstanceRequestBody request) {
        return client.terminateInstance(context, instanceId, request);
    }

    /** 完成动作：只检查必填与值类型，不合格返回 422 字段错误。 */
    @PostMapping("/action-runs/{actionRunId}/submission")
    public WorkflowSubmissionResponseBody submitAction(OperatorContext context,
        @PathVariable("actionRunId") @Pattern(regexp = WorkflowModels.ID) String actionRunId,
        @Valid @RequestBody SubmitWorkflowActionRequestBody request) {
        return client.submitAction(context, actionRunId, request);
    }

    /** 转交动作。 */
    @PostMapping("/action-runs/{actionRunId}/transfer")
    public WorkflowMutationResponseBody transferAction(OperatorContext context,
        @PathVariable("actionRunId") @Pattern(regexp = WorkflowModels.ID) String actionRunId,
        @Valid @RequestBody TransferWorkflowTaskRequestBody request) {
        return client.transferAction(context, actionRunId, request);
    }

    /** 审批决定。 */
    @PostMapping("/approvals/{approvalId}/decision")
    public WorkflowMutationResponseBody decideApproval(OperatorContext context,
        @PathVariable("approvalId") @Pattern(regexp = WorkflowModels.ID) String approvalId,
        @Valid @RequestBody DecideWorkflowApprovalRequestBody request) {
        return client.decideApproval(context, approvalId, request);
    }

    /** 转交审批。 */
    @PostMapping("/approvals/{approvalId}/transfer")
    public WorkflowMutationResponseBody transferApproval(OperatorContext context,
        @PathVariable("approvalId") @Pattern(regexp = WorkflowModels.ID) String approvalId,
        @Valid @RequestBody TransferWorkflowTaskRequestBody request) {
        return client.transferApproval(context, approvalId, request);
    }

    /** 我的待办。 */
    @GetMapping("/my-tasks")
    public PagingResponseBody<WorkflowTaskBody> listMyTasks(OperatorContext context,
        @RequestParam(value = "taskType", required = false) String taskType,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listMyTasks(context, taskType, keyword, page, size);
    }

    /** 我已处理：我提交过的动作与我已决定的审批。 */
    @GetMapping("/my-tasks/handled")
    public PagingResponseBody<WorkflowHandledTaskBody> listHandledTasks(OperatorContext context,
        @RequestParam(value = "taskType", required = false) String taskType,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listHandledTasks(context, taskType, keyword, page, size);
    }

    /** 手动重试投递失败的通知或回调。 */
    @PostMapping("/callbacks/{callbackId}/retry")
    public WorkflowMutationResponseBody retryCallback(OperatorContext context,
        @PathVariable("callbackId") @Pattern(regexp = WorkflowModels.ID) String callbackId) {
        return client.retryCallback(context, callbackId);
    }

    /** 选人。 */
    @GetMapping("/assignee-candidates")
    public WorkflowAssigneeCandidateListResponseBody listAssigneeCandidates(OperatorContext context,
        @RequestParam(value = "keyword", required = false) String keyword) {
        return client.listAssigneeCandidates(context, keyword);
    }
}
