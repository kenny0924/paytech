package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.dataquery.domain.QueryTemplateDetail;
import com.paytech.platform.support.dataquery.domainservice.service.QueryTemplateService;
import com.paytech.platform.support.dataquery.service.processor.request.GetQueryTemplateRequest;

import lombok.AllArgsConstructor;

/** 模板详情及当前版本：复核 DATA_QUERY_READ 后读取，不存在返回 404。 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class GetQueryTemplateProcessor extends AbstractProcessor<GetQueryTemplateRequest, QueryTemplateDetail> {

    private final QueryTemplateService templates;
    private final DataQueryManagementSupport support;

    @Override
    protected QueryTemplateDetail processRequest(GetQueryTemplateRequest request) {
        support.requirePermission(request.getActor(), request.getSession(), DataQueryManagementSupport.READ_PERMISSION);
        return DataQueryManagementSupport.requireFound(templates.findQueryTemplate(request.getTemplateId()));
    }
}
