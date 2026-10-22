package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.domain.QueryTemplateSummary;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.service.processor.request.ListQueryTemplatesRequest;

import lombok.AllArgsConstructor;

/** 分页查询模板：复核 DATA_QUERY_READ 后读取，不开事务。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListQueryTemplatesProcessor
    extends AbstractProcessor<ListQueryTemplatesRequest, PagingResponseBody<QueryTemplateSummary>> {

    private final QueryTemplateService templates;
    private final DataQueryManagementSupport support;

    @Override
    protected PagingResponseBody<QueryTemplateSummary> processRequest(ListQueryTemplatesRequest request) {
        support.requirePermission(request.getActor(), request.getSession(), DataQueryManagementSupport.READ_PERMISSION);
        return templates.listQueryTemplates(request.getDataSourceId(), request.getStatus(), request.getKeyword(),
            request.getPaging());
    }
}
