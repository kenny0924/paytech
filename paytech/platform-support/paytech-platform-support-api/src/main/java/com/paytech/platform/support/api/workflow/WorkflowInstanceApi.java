package com.paytech.platform.support.api.workflow;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
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

import jakarta.validation.Valid;

/**
 * 工作流运行（技术方案第 7 节 13–24 与 4.2 的活动记录、手动重试）。
 * <li>发起需 WORKFLOW_INSTANCE_START；其余需 WORKFLOW_TASK_HANDLE，且须是被指派的人或参与人，
 * WORKFLOW_INSTANCE_READ 可查看全部实例，WORKFLOW_INSTANCE_MANAGE 可终止或转交任意实例并手动重试回调。</li>
 * <li>附件上传与下载授权见 {@link WorkflowAttachmentApi}。</li>
 */
@HttpExchange(url = "/workflow/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface WorkflowInstanceApi {

    /**
     * 可发起的流程：ACTIVE 且已发布
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param keyword 编码或名称关键字；可空
     * @param page 页码
     * @param size 每页条数
     * @return 定义摘要
     */
    @GetExchange("/launchable-definitions")
    PagingResponseBody<WorkflowDefinitionSummaryBody> listLaunchableDefinitions(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 可发起流程的当前发布版本
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param definitionId 定义 ID
     * @return 启动表单、流程图与默认人员；不可发起返回 404
     */
    @GetExchange("/launchable-definitions/{definitionId}")
    WorkflowLaunchResponseBody getLaunchableDefinition(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("definitionId") String definitionId);

    /**
     * 发起实例
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param request 定义、版本号、标题、业务关联键、启动值与人员修改
     * @return 新实例；启动值不合法、人员不可用或版本已变返回 400
     */
    @PostExchange("/instances")
    WorkflowMutationResponseBody startInstance(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @Valid @RequestBody StartWorkflowInstanceRequestBody request);

    /**
     * 实例列表
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param scope MINE（默认，我参与的）或 ALL（需 WORKFLOW_INSTANCE_READ）
     * @param definitionId 定义；可空
     * @param status 状态；可空
     * @param startedBy 发起人；可空
     * @param businessKey 业务关联键；可空
     * @param keyword 标题关键字（包含匹配）；可空
     * @param page 页码
     * @param size 每页条数
     * @return 实例摘要（含流程名称、版本、进度与当前节点处理人），按发起时间倒序
     */
    @GetExchange("/instances")
    PagingResponseBody<WorkflowInstanceSummaryBody> listInstances(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "scope", required = false) String scope,
        @RequestParam(value = "definitionId", required = false) String definitionId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "startedBy", required = false) String startedBy,
        @RequestParam(value = "businessKey", required = false) String businessKey,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 实例详情；参与人或有 WORKFLOW_INSTANCE_READ 的人可看
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param instanceId 实例 ID
     * @return 详情；不存在或无权查看返回 404
     */
    @GetExchange("/instances/{instanceId}")
    WorkflowInstanceDetailResponseBody getInstance(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("instanceId") String instanceId);

    /**
     * 实例活动记录：节点状态、提交与验证、审批、转交与通知回调投递状态
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param instanceId 实例 ID
     * @return 活动记录；不存在或无权查看返回 404
     */
    @GetExchange("/instances/{instanceId}/activities")
    WorkflowActivityListResponseBody listActivities(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("instanceId") String instanceId);

    /**
     * 终止实例；发起人或有 WORKFLOW_INSTANCE_MANAGE 的人
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param instanceId 实例 ID
     * @param request 原因
     * @return 保存结果
     */
    @PostExchange("/instances/{instanceId}/termination")
    WorkflowMutationResponseBody terminateInstance(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("instanceId") String instanceId,
        @Valid @RequestBody TerminateWorkflowInstanceRequestBody request);

    /**
     * 提交动作输出并同步验证；只有被指派的执行人
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param actionRunId 动作运行 ID
     * @param request 提交内容
     * @return 本次验证结果；验证未通过也返回 200（status=UNVERIFIED）
     */
    @PostExchange("/action-runs/{actionRunId}/submission")
    WorkflowSubmissionResponseBody submitAction(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("actionRunId") String actionRunId,
        @Valid @RequestBody SubmitWorkflowActionRequestBody request);

    /**
     * 重新验证动作：文件扫描结束后，当前执行人按原提交内容再验证一次（技术方案第 7 节 #20）
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param actionRunId 动作运行 ID
     * @return 本次验证结果；仍未通过也返回 200（status=UNVERIFIED）；动作不是已提交未通过的文件类动作返回 400
     */
    @PostExchange("/action-runs/{actionRunId}/verification")
    WorkflowSubmissionResponseBody reverifyAction(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("actionRunId") String actionRunId);

    /**
     * 转交动作；当前执行人或有 WORKFLOW_INSTANCE_MANAGE 的人
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param actionRunId 动作运行 ID
     * @param request 新执行人与说明
     * @return 保存结果
     */
    @PostExchange("/action-runs/{actionRunId}/transfer")
    WorkflowMutationResponseBody transferAction(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("actionRunId") String actionRunId,
        @Valid @RequestBody TransferWorkflowTaskRequestBody request);

    /**
     * 审批决定：同意、退回修改或驳回；只有被指派的审批人，且不能审批自己执行过动作的节点
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param approvalId 审批 ID
     * @param request 决定、意见与驳回目标
     * @return 保存结果（status 为实例状态）
     */
    @PostExchange("/approvals/{approvalId}/decision")
    WorkflowMutationResponseBody decideApproval(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("approvalId") String approvalId,
        @Valid @RequestBody DecideWorkflowApprovalRequestBody request);

    /**
     * 转交审批；当前审批人或有 WORKFLOW_INSTANCE_MANAGE 的人，不能转给同一节点的其他审批人
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param approvalId 审批 ID
     * @param request 新审批人与说明
     * @return 保存结果
     */
    @PostExchange("/approvals/{approvalId}/transfer")
    WorkflowMutationResponseBody transferApproval(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("approvalId") String approvalId,
        @Valid @RequestBody TransferWorkflowTaskRequestBody request);

    /**
     * 我的待办：待执行动作与待审批节点合并，按待办开始时间倒序
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param taskType ACTION 或 APPROVAL；可空
     * @param keyword 实例标题关键字（包含匹配）；可空
     * @param page 页码
     * @param size 每页条数
     * @return 待办
     */
    @GetExchange("/my-tasks")
    PagingResponseBody<WorkflowTaskBody> listMyTasks(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "taskType", required = false) String taskType,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 我已处理：我提交过的动作与我已决定的审批，按处理时间倒序；需 WORKFLOW_TASK_HANDLE
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param taskType ACTION 或 APPROVAL；可空
     * @param keyword 实例标题关键字（包含匹配）；可空
     * @param page 页码
     * @param size 每页条数
     * @return 已处理项
     */
    @GetExchange("/my-tasks/handled")
    PagingResponseBody<WorkflowHandledTaskBody> listHandledTasks(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "taskType", required = false) String taskType,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 手动重试 FAILED 的通知或回调；需 WORKFLOW_INSTANCE_MANAGE
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param callbackId 通知回调 ID
     * @return 保存结果（PENDING）；不是 FAILED 返回 400
     */
    @PostExchange("/callbacks/{callbackId}/retry")
    WorkflowMutationResponseBody retryCallback(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("callbackId") String callbackId);

    /**
     * 选人：ACTIVE 的 Operations 人员，最多 20 条；需 WORKFLOW_TASK_HANDLE 或 WORKFLOW_DEFINITION_MANAGE。
     * <li>关键字含 @ 时按完整登录邮箱精确匹配；否则按登录名前缀或脱敏邮箱片段匹配；为空时返回前 20 人。</li>
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param keyword 关键字，可空，最长 128
     * @return 候选人
     */
    @GetExchange("/assignee-candidates")
    WorkflowAssigneeCandidateListResponseBody listAssigneeCandidates(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "keyword", required = false) String keyword);
}
