package com.paytech.platform.support.ext.rest.workflow;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.workflow.WorkflowDataQueryTemplateApi;
import com.paytech.platform.support.api.workflow.response.WorkflowDataQueryTemplateBody;
import com.paytech.platform.support.workflow.service.processor.WorkflowQueryProcessor;

import lombok.AllArgsConstructor;

/** 动作目录选择 data-query 模板的协议适配器；授权与查询委托 workflow 模块。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class WorkflowDataQueryTemplateController implements WorkflowDataQueryTemplateApi {

    private final WorkflowQueryProcessor queries;

    /** 启用中的查询模板。 */
    @Override
    public PagingResponseBody<WorkflowDataQueryTemplateBody> listDataQueryTemplates(String actor, String session,
        String keyword, Integer page, Integer size) {
        return queries.listDataQueryTemplates(actor, session, keyword, page, size);
    }
}
