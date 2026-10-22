package com.paytech.platform.support.ext.rest.dataquery;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
import com.paytech.platform.support.api.dataquery.QueryTemplateManagementApi;
import com.paytech.platform.support.api.dataquery.request.ChangeQueryTemplateStatusRequestBody;
import com.paytech.platform.support.api.dataquery.request.CreateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.RestoreQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.TrialQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.UpdateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.request.ValidateQueryTemplateRequestBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateDetailResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateSummaryResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateValidationResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateVersionResponseBody;
import com.paytech.platform.support.api.dataquery.response.QueryTemplateVersionSummaryResponseBody;
import com.paytech.platform.support.api.dataquery.response.SaveQueryTemplateResponseBody;
import com.paytech.platform.support.api.dataquery.response.TrialQueryTemplateResponseBody;
import com.paytech.platform.support.dataquery.core.enums.QueryTemplateStatus;
import com.paytech.platform.support.dataquery.service.processor.ChangeQueryTemplateStatusProcessor;
import com.paytech.platform.support.dataquery.service.processor.CreateQueryTemplateProcessor;
import com.paytech.platform.support.dataquery.service.processor.GetQueryTemplateProcessor;
import com.paytech.platform.support.dataquery.service.processor.GetQueryTemplateVersionProcessor;
import com.paytech.platform.support.dataquery.service.processor.ListQueryTemplateVersionsProcessor;
import com.paytech.platform.support.dataquery.service.processor.ListQueryTemplatesProcessor;
import com.paytech.platform.support.dataquery.service.processor.RestoreQueryTemplateProcessor;
import com.paytech.platform.support.dataquery.service.processor.TrialQueryTemplateProcessor;
import com.paytech.platform.support.dataquery.service.processor.UpdateQueryTemplateProcessor;
import com.paytech.platform.support.dataquery.service.processor.ValidateQueryTemplateProcessor;
import com.paytech.platform.support.dataquery.service.processor.request.ChangeQueryTemplateStatusRequest;
import com.paytech.platform.support.dataquery.service.processor.request.CreateQueryTemplateRequest;
import com.paytech.platform.support.dataquery.service.processor.request.GetQueryTemplateRequest;
import com.paytech.platform.support.dataquery.service.processor.request.GetQueryTemplateVersionRequest;
import com.paytech.platform.support.dataquery.service.processor.request.ListQueryTemplateVersionsRequest;
import com.paytech.platform.support.dataquery.service.processor.request.ListQueryTemplatesRequest;
import com.paytech.platform.support.dataquery.service.processor.request.RestoreQueryTemplateRequest;
import com.paytech.platform.support.dataquery.service.processor.request.TrialQueryTemplateRequest;
import com.paytech.platform.support.dataquery.service.processor.request.UpdateQueryTemplateRequest;
import com.paytech.platform.support.dataquery.service.processor.request.ValidateQueryTemplateRequest;
import com.paytech.platform.support.ext.rest.audit.Audit;

import lombok.AllArgsConstructor;

/** 查询模板维护、校验与试运行的协议适配；规则、权限与事务委托 dataquery 的 Processor。 */
@RestController
@AllArgsConstructor
@ConditionalOnProperty(name = "operations.authentication.enabled", havingValue = "true")
public class QueryTemplateManagementController implements QueryTemplateManagementApi {

    private final DataQueryConverter converter;
    private final GetQueryTemplateProcessor getProcessor;
    private final ListQueryTemplatesProcessor listProcessor;
    private final TrialQueryTemplateProcessor trialProcessor;
    private final CreateQueryTemplateProcessor createProcessor;
    private final UpdateQueryTemplateProcessor updateProcessor;
    private final RestoreQueryTemplateProcessor restoreProcessor;
    private final ValidateQueryTemplateProcessor validateProcessor;
    private final GetQueryTemplateVersionProcessor versionProcessor;
    private final ListQueryTemplateVersionsProcessor versionsProcessor;
    private final ChangeQueryTemplateStatusProcessor statusProcessor;

    /** 解析筛选与分页后查询。 */
    @Override
    public PagingResponseBody<QueryTemplateSummaryResponseBody> listQueryTemplates(String actor, String session,
        String dataSourceId, String status, String keyword, Integer page, Integer size) {
        var request = new ListQueryTemplatesRequest(actor, session, converter.optionalId(dataSourceId, "dataSourceId"),
            converter.optionalCode(QueryTemplateStatus.class, status, "status"), DataQueryConverter.text(keyword),
            converter.paging(page, size));
        return listProcessor.process(request).map(converter::templateSummary);
    }

    /** 解析 ID 后查询。 */
    @Override
    public QueryTemplateDetailResponseBody getQueryTemplate(String actor, String session, String templateId) {
        return converter.templateDetail(getProcessor.process(new GetQueryTemplateRequest(actor, session,
            converter.id(templateId, "templateId"))));
    }

    /** 转换请求后委托新建。 */
    @Audit(action = "OPERATIONS_QUERY_TEMPLATE_CREATE", resourceType = "QUERY_TEMPLATE",
        fallbackResourceId = "QUERY_TEMPLATE_CATALOG")
    @Override
    public SaveQueryTemplateResponseBody createQueryTemplate(String actor, String session,
        CreateQueryTemplateRequestBody request) {
        return converter.save(createProcessor.process(new CreateQueryTemplateRequest(actor, session,
            request.getTemplateCode(), DataQueryConverter.text(request.getTemplateName()),
            DataQueryConverter.text(request.getDescription()), converter.draft(request.getDataSourceId(),
                request.getBody(), request.getParameters(), request.getColumns(), request.getConstraints()),
            DataQueryConverter.text(request.getChangeNote()))));
    }

    /** 转换请求后委托保存新版本。 */
    @Audit(action = "OPERATIONS_QUERY_TEMPLATE_UPDATE", resourceType = "QUERY_TEMPLATE",
        resourceIdParameter = "templateId", fallbackResourceId = "QUERY_TEMPLATE_CATALOG")
    @Override
    public SaveQueryTemplateResponseBody updateQueryTemplate(String actor, String session, String templateId,
        UpdateQueryTemplateRequestBody request) {
        return converter.save(updateProcessor.process(new UpdateQueryTemplateRequest(actor, session,
            converter.id(templateId, "templateId"), request.getExpectedVersion(),
            DataQueryConverter.text(request.getTemplateName()), DataQueryConverter.text(request.getDescription()),
            converter.draft(request.getDataSourceId(), request.getBody(), request.getParameters(), request.getColumns(),
                request.getConstraints()),
            DataQueryConverter.text(request.getChangeNote()))));
    }

    /** 转换目标状态后委托启停。 */
    @Audit(action = "OPERATIONS_QUERY_TEMPLATE_STATUS", resourceType = "QUERY_TEMPLATE",
        resourceIdParameter = "templateId", fallbackResourceId = "QUERY_TEMPLATE_CATALOG")
    @Override
    public ConfigurationMutationResponseBody changeQueryTemplateStatus(String actor, String session, String templateId,
        ChangeQueryTemplateStatusRequestBody request) {
        return converter.mutation(statusProcessor.process(new ChangeQueryTemplateStatusRequest(actor, session,
            converter.id(templateId, "templateId"), request.getExpectedVersion(),
            converter.code(QueryTemplateStatus.class, request.getStatus(), "status"))));
    }

    /** 解析 ID 与分页后查询版本列表。 */
    @Override
    public PagingResponseBody<QueryTemplateVersionSummaryResponseBody> listQueryTemplateVersions(String actor,
        String session, String templateId, Integer page, Integer size) {
        return versionsProcessor.process(new ListQueryTemplateVersionsRequest(actor, session,
            converter.id(templateId, "templateId"), converter.paging(page, size))).map(converter::versionSummary);
    }

    /** 解析 ID 后查询版本内容。 */
    @Override
    public QueryTemplateVersionResponseBody getQueryTemplateVersion(String actor, String session, String templateId,
        Integer versionNo) {
        return converter.version(versionProcessor.process(new GetQueryTemplateVersionRequest(actor, session,
            converter.id(templateId, "templateId"), versionNo == null ? 0 : versionNo)));
    }

    /** 委托恢复。 */
    @Audit(action = "OPERATIONS_QUERY_TEMPLATE_RESTORE", resourceType = "QUERY_TEMPLATE",
        resourceIdParameter = "templateId", fallbackResourceId = "QUERY_TEMPLATE_CATALOG")
    @Override
    public SaveQueryTemplateResponseBody restoreQueryTemplate(String actor, String session, String templateId,
        RestoreQueryTemplateRequestBody request) {
        return converter.save(restoreProcessor.process(new RestoreQueryTemplateRequest(actor, session,
            converter.id(templateId, "templateId"), request.getExpectedVersion(), request.getVersionNo(),
            DataQueryConverter.text(request.getChangeNote()))));
    }

    /** 转换草稿后委托校验。 */
    @Override
    public QueryTemplateValidationResponseBody validateQueryTemplate(String actor, String session,
        ValidateQueryTemplateRequestBody request) {
        return converter.validation(validateProcessor.process(new ValidateQueryTemplateRequest(actor, session,
            converter.draft(request.getDataSourceId(), request.getBody(), request.getParameters(), request.getColumns(),
                request.getConstraints()))));
    }

    /** 转换草稿与分页后委托试运行。 */
    @Override
    public TrialQueryTemplateResponseBody trialQueryTemplate(String actor, String session,
        TrialQueryTemplateRequestBody request) {
        return converter.trial(trialProcessor.process(new TrialQueryTemplateRequest(actor, session,
            converter.draft(request.getDataSourceId(), request.getBody(), request.getParameters(), request.getColumns(),
                request.getConstraints()),
            request.getParams(), request.getPage(), request.getSize(), request.isWithTotal(), request.getCursor())));
    }
}
