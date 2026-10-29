package com.paytech.platform.support.api.workflow;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.workflow.response.WorkflowDataQueryTemplateBody;

/**
 * 工作流动作目录选择 data-query 模板（REQ-05，technique 8.5）。
 * <li>需 WORKFLOW_ACTION_TEMPLATE_MANAGE，不要求 data-query 权限；只返回 ACTIVE 模板的编码、名称、说明、数据源名称与参数声明。</li>
 */
@HttpExchange(url = "/workflow-admin/v1/data-query-templates", accept = MediaType.APPLICATION_JSON_VALUE)
public interface WorkflowDataQueryTemplateApi {

    /**
     * 启用中的查询模板
     *
     * @param actor 可信操作人账号
     * @param session 可信会话
     * @param keyword 编码或名称关键字；可空
     * @param page 页码，默认 1
     * @param size 每页条数，默认 20，最大 50
     * @return 模板分页
     */
    @GetExchange
    PagingResponseBody<WorkflowDataQueryTemplateBody> listDataQueryTemplates(
        @RequestHeader("X-Gateway-Actor-Subject") String actor, @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
