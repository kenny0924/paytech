package com.paytech.platform.support.api.workflow;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.workflow.request.ChangeWorkflowStatusRequestBody;
import com.paytech.platform.support.api.workflow.request.CreateWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.PublishWorkflowDefinitionRequestBody;
import com.paytech.platform.support.api.workflow.request.SaveWorkflowDraftRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionDetailResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDefinitionSummaryBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDraftResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowVersionBody;

import jakarta.validation.Valid;

/**
 * 工作流流程定义维护（技术方案第 7 节 6–12）；查询需 WORKFLOW_DEFINITION_READ，写入需 WORKFLOW_DEFINITION_MANAGE。
 * <li>草稿保存返回校验问题但不阻止保存；发布时有任何问题即拒绝，问题列表通过详情接口读取。</li>
 */
@HttpExchange(url = "/workflow-admin/v1/definitions", accept = MediaType.APPLICATION_JSON_VALUE)
public interface WorkflowDefinitionManagementApi {

    /**
     * 分页查询流程定义
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param status 状态；可空
     * @param keyword 编码或名称关键字；可空
     * @param page 页码
     * @param size 每页条数
     * @return 定义摘要
     */
    @GetExchange
    PagingResponseBody<WorkflowDefinitionSummaryBody> listDefinitions(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询流程定义详情：定义、草稿（含校验问题）与版本列表
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param definitionId 定义 ID
     * @return 详情；不存在返回 404
     */
    @GetExchange("/{definitionId}")
    WorkflowDefinitionDetailResponseBody getDefinition(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("definitionId") String definitionId);

    /**
     * 新建流程定义及草稿 v1
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param request 编码、名称与说明
     * @return 保存结果；编码已存在返回 400
     */
    @PostExchange
    WorkflowMutationResponseBody createDefinition(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateWorkflowDefinitionRequestBody request);

    /**
     * 保存草稿
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param definitionId 定义 ID
     * @param request 名称、说明、流程图、启动表单与原版本
     * @return 保存结果与当前校验问题；版本不符返回 409，结构不合法返回 400 INVALID_REQUEST
     */
    @PutExchange("/{definitionId}/draft")
    WorkflowDraftResponseBody saveDraft(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("definitionId") String definitionId,
        @Valid @RequestBody SaveWorkflowDraftRequestBody request);

    /**
     * 发布草稿并冻结动作模板快照
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param definitionId 定义 ID
     * @param request 原版本
     * @return 保存结果；校验未通过返回 400 BUSINESS_CHECK_FAILED（问题数量见 detail，明细见详情接口）
     */
    @PostExchange("/{definitionId}/publication")
    WorkflowMutationResponseBody publishDefinition(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("definitionId") String definitionId,
        @Valid @RequestBody PublishWorkflowDefinitionRequestBody request);

    /**
     * 按原版本启用或停用流程定义；停用后不能发起，在途实例照常运行
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param definitionId 定义 ID
     * @param request 目标状态与原版本
     * @return 保存结果
     */
    @PostExchange("/{definitionId}/status")
    WorkflowMutationResponseBody changeDefinitionStatus(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("definitionId") String definitionId,
        @Valid @RequestBody ChangeWorkflowStatusRequestBody request);

    /**
     * 查看某个版本
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param definitionId 定义 ID
     * @param versionNo 版本号
     * @return 版本内容；不存在返回 404
     */
    @GetExchange("/{definitionId}/versions/{versionNo}")
    WorkflowVersionBody getVersion(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("definitionId") String definitionId,
        @PathVariable("versionNo") Integer versionNo);
}
