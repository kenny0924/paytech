package com.paytech.platform.support.dataquery.service.processor;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.platform.support.dataquery.domain.DataSourceDetail;
import com.paytech.platform.support.dataquery.domainservice.service.DataSourceService;
import com.paytech.platform.support.dataquery.domainservice.validator.QueryTemplateDraftValidator;
import com.paytech.platform.support.dataquery.service.processor.request.ValidateQueryTemplateRequest;
import com.paytech.platform.support.dataquery.template.TemplateValidationResult;

import lombok.AllArgsConstructor;

/**
 * 编辑器实时校验：复核 DATA_QUERY_TEMPLATE_MANAGE 后按保存规则给出全部问题，不写库、不访问被查库。
 * <li>有联合查询配置且没有错误时附加参与提示（按模板 ACTIVE 推算）。</li>
 */
@Component
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class ValidateQueryTemplateProcessor
    extends AbstractProcessor<ValidateQueryTemplateRequest, TemplateValidationResult> {

    private final DataSourceService dataSources;
    private final DataQueryManagementSupport support;
    private final QueryTemplateDraftValidator validator;

    @Override
    protected TemplateValidationResult processRequest(ValidateQueryTemplateRequest request) {
        support.requirePermission(request.getActor(), request.getSession(),
            DataQueryManagementSupport.TEMPLATE_MANAGE_PERMISSION);
        DataSourceDetail dataSource = dataSources.findDataSource(request.getDraft().getDataSourceId());
        TemplateValidationResult result = validator.validate(request.getDraft(), dataSource,
            support.referencedKeyTypes(request.getDraft()));
        return new TemplateValidationResult(DataQueryManagementSupport.withParticipation(result.getProblems(), result,
            request.getDraft(), dataSource, true));
    }
}
