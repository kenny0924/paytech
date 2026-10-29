package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformWorkflowClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.platform.support.api.workflow.response.WorkflowDataQueryTemplateBody;

import lombok.AllArgsConstructor;

/**
 * 动作目录选择 DATA_QUERY 查询模板的浏览器入口（REQ-05），简单转发 Platform 同路径
 * <li>路由由 Gateway 绑定 WORKFLOW_ACTION_TEMPLATE_MANAGE 并经 PLT-I02 实时授权；Platform 再次校验，不要求 data-query 权限。</li>
 */
@RestController
@AllArgsConstructor
@RequestMapping("/workflow-admin/v1/data-query-templates")
public class WorkflowDataQueryTemplateController {

    private final PlatformWorkflowClient client;

    /** 启用中的查询模板：编码、名称、说明、数据源名称与参数声明。 */
    @GetMapping
    public PagingResponseBody<WorkflowDataQueryTemplateBody> listDataQueryTemplates(OperatorContext context,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listDataQueryTemplates(context, keyword, page, size);
    }
}
