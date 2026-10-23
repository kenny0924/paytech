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
import com.paytech.platform.support.api.workflow.request.CreateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.request.UpdateWorkflowActionTemplateRequestBody;
import com.paytech.platform.support.api.workflow.response.WorkflowActionTemplateResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowMutationResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowTemplateReferenceListResponseBody;

import jakarta.validation.Valid;

/**
 * 工作流动作目录维护（技术方案第 7 节 1–5）；Platform 在事务内重检权限，请求幂等由 Gateway 负责。
 * <li>查询需 WORKFLOW_DEFINITION_READ，写入需 WORKFLOW_ACTION_TEMPLATE_MANAGE。</li>
 */
@HttpExchange(url = "/workflow-admin/v1/action-templates", accept = MediaType.APPLICATION_JSON_VALUE)
public interface WorkflowActionTemplateManagementApi {

    /**
     * 分页查询动作模板
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param actionType 动作类型；可空
     * @param status 状态；可空
     * @param keyword 编码或名称关键字；可空
     * @param page 页码
     * @param size 每页条数
     * @return 模板摘要（不含配置），每行带被当前发布版本引用的流程定义数 referencedDefinitionCount
     */
    @GetExchange
    PagingResponseBody<WorkflowActionTemplateResponseBody> listActionTemplates(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "actionType", required = false) String actionType,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 查询动作模板详情
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param templateId 模板 ID
     * @return 模板（含配置）；不存在返回 404
     */
    @GetExchange("/{templateId}")
    WorkflowActionTemplateResponseBody getActionTemplate(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("templateId") String templateId);

    /**
     * 查询引用该模板的已发布流程版本：冻结的模板版本、是否等于当前版本、各版本运行中实例数；需 WORKFLOW_DEFINITION_READ
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param templateId 模板 ID
     * @return 引用；模板不存在返回 404
     */
    @GetExchange("/{templateId}/references")
    WorkflowTemplateReferenceListResponseBody listTemplateReferences(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("templateId") String templateId);

    /**
     * 新建动作模板（ACTIVE）
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param request 编码、名称、类型与配置
     * @return 保存结果
     *         <li>编码已存在或配置不符合类型要求返回 400 BUSINESS_CHECK_FAILED。</li>
     */
    @PostExchange
    WorkflowMutationResponseBody createActionTemplate(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateWorkflowActionTemplateRequestBody request);

    /**
     * 按原版本修改动作模板
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param templateId 模板 ID
     * @param request 名称、说明、配置与原版本
     * @return 保存结果；版本不符返回 409，已发布流程版本不受影响
     */
    @PutExchange("/{templateId}")
    WorkflowMutationResponseBody updateActionTemplate(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("templateId") String templateId,
        @Valid @RequestBody UpdateWorkflowActionTemplateRequestBody request);

    /**
     * 按原版本启用或停用动作模板
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param templateId 模板 ID
     * @param request 目标状态与原版本
     * @return 保存结果；停用后新发布的流程不能引用
     */
    @PostExchange("/{templateId}/status")
    WorkflowMutationResponseBody changeActionTemplateStatus(@RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session, @PathVariable("templateId") String templateId,
        @Valid @RequestBody ChangeWorkflowStatusRequestBody request);
}
