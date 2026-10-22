package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.dataquery.domain.QueryTemplateVersionSummary;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.service.processor.request.ListQueryTemplateVersionsRequest;

import lombok.AllArgsConstructor;

/** 模板版本列表：复核 DATA_QUERY_READ，模板不存在返回 404，按版本号倒序。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ListQueryTemplateVersionsProcessor
    extends AbstractProcessor<ListQueryTemplateVersionsRequest, PagingResponseBody<QueryTemplateVersionSummary>> {

    private final QueryTemplateService templates;
    private final DataQueryManagementSupport support;

    @Override
    protected PagingResponseBody<QueryTemplateVersionSummary> processRequest(ListQueryTemplateVersionsRequest request) {
        support.requirePermission(request.getActor(), request.getSession(), DataQueryManagementSupport.READ_PERMISSION);
        DataQueryManagementSupport.requireFound(templates.findQueryTemplate(request.getTemplateId()));
        return templates.listQueryTemplateVersions(request.getTemplateId(), request.getPaging());
    }
}
