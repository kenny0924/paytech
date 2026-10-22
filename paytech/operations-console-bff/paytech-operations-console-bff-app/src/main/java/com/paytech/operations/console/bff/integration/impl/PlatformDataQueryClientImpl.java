package com.paytech.operations.console.bff.integration.impl;

import static com.paytech.operations.console.bff.integration.impl.DataQueryContractConverter.DATA_SOURCE;
import static com.paytech.operations.console.bff.integration.impl.DataQueryContractConverter.QUERY_TEMPLATE;
import static com.paytech.operations.console.bff.integration.impl.DataQueryContractConverter.convert;
import static com.paytech.operations.console.bff.integration.impl.DataQueryContractConverter.mutation;
import static com.paytech.operations.console.bff.integration.impl.DataQueryContractConverter.page;
import static com.paytech.operations.console.bff.integration.impl.DataQueryContractConverter.require;
import static com.paytech.operations.console.bff.integration.impl.DataQueryContractConverter.sameIdentity;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import com.paytech.foundation.httpclient.api.HttpRequestValidationException;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformDataQueryClient;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.dataquery.request.ChangeDataSourceStatusRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ChangeQueryTemplateStatusRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.CreateDataSourceRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.CreateQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ExecuteDataQueryRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.RestoreQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.TestDataSourceConnectionRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.TrialQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.UpdateDataSourceRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.UpdateQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ValidateQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.DataQueryResultResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.DataSourceConnectionTestResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.DataSourceResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateDetailResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateValidationResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateVersionResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateVersionSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.SaveQueryTemplateResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.TrialQueryTemplateResponseBody;
import com.paytech.platform.support.api.dataquery.DataQueryExecutionApi;
import com.paytech.platform.support.api.dataquery.DataSourceManagementApi;
import com.paytech.platform.support.api.dataquery.QueryTemplateManagementApi;

import lombok.AllArgsConstructor;

/**
 * Platform 数据查询的 HTTP 转发：每个入口只调用一次 Platform，不重试，没有聚合
 * <li>查询与写操作都把会话 Account ID 与 Session ID 填入 X-Gateway-Actor-Subject / X-Gateway-Session-Id；
 * 执行另带 X-Caller-Service=operations-console-bff。请求幂等由 Gateway 负责。</li>
 * <li>带 Body 的请求遇 Platform 422 时，字段错误按本次页面 Body 投影并保留 Platform 文案（见 {@link DataQueryValidationConverter}）。</li>
 *
 * @author Claude
 */
@Component
@AllArgsConstructor
public class PlatformDataQueryClientImpl implements PlatformDataQueryClient {
    /** Platform data-query.http.callers 中登记的本服务调用方名，与 spring.application.name 一致。 */
    static final String CALLER_SERVICE = "operations-console-bff";

    private final DataSourceManagementApi dataSourceApi;
    private final QueryTemplateManagementApi templateApi;
    private final DataQueryExecutionApi executionApi;

    /** 数据源分页；元素身份为数据源 ID。 */
    @Override
    public PagingResponseBody<DataSourceResponseBody> listDataSources(OperatorContext context, String status,
        String engineType, String keyword, Integer page, Integer size) {
        var result = dataSourceApi.listDataSources(context.accountId(), context.sessionId(), status, engineType,
            keyword, page, size);
        return page(result, page, size, DataSourceResponseBody.class, DataSourceResponseBody::getDataSourceId);
    }

    /** 数据源详情；返回的 ID 必须与请求一致。 */
    @Override
    public DataSourceResponseBody getDataSource(OperatorContext context, String dataSourceId) {
        var mapped = convert(dataSourceApi.getDataSource(context.accountId(), context.sessionId(), dataSourceId),
            DataSourceResponseBody.class);
        sameIdentity(dataSourceId, mapped.getDataSourceId());
        return mapped;
    }

    /** 新建；回执须为本次编码的数据源且状态为 INACTIVE。 */
    @Override
    public ConfigurationMutationResponseBody createDataSource(OperatorContext context,
        CreateDataSourceRequestBody request) {
        return mutate(request, () -> dataSourceApi.createDataSource(context.accountId(), context.sessionId(),
            convert(request, com.paytech.platform.support.api.dataquery.request.CreateDataSourceRequestBody.class)),
            DATA_SOURCE, null, request.getDataSourceCode(), "INACTIVE");
    }

    /** 修改；回执须为本次数据源，状态不变由 Platform 保证。 */
    @Override
    public ConfigurationMutationResponseBody updateDataSource(OperatorContext context, String dataSourceId,
        UpdateDataSourceRequestBody request) {
        return mutate(request, () -> dataSourceApi.updateDataSource(context.accountId(), context.sessionId(),
            dataSourceId, convert(request, com.paytech.platform.support.api.dataquery.request.UpdateDataSourceRequestBody.class)),
            DATA_SOURCE, dataSourceId, null, null);
    }

    /** 测试连接；未通过时 Platform 仍返回 200。 */
    @Override
    public DataSourceConnectionTestResponseBody testDataSourceConnection(OperatorContext context,
        TestDataSourceConnectionRequestBody request) {
        return send(request, () -> dataSourceApi.testDataSourceConnection(context.accountId(), context.sessionId(),
            convert(request, com.paytech.platform.support.api.dataquery.request.TestDataSourceConnectionRequestBody.class)),
            DataSourceConnectionTestResponseBody.class);
    }

    /** 启停；回执须为本次数据源且状态等于目标状态。 */
    @Override
    public ConfigurationMutationResponseBody changeDataSourceStatus(OperatorContext context, String dataSourceId,
        ChangeDataSourceStatusRequestBody request) {
        return mutate(request, () -> dataSourceApi.changeDataSourceStatus(context.accountId(), context.sessionId(),
            dataSourceId, convert(request, com.paytech.platform.support.api.dataquery.request.ChangeDataSourceStatusRequestBody.class)),
            DATA_SOURCE, dataSourceId, null, request.getStatus());
    }

    /** 软删除；回执须为本次数据源且状态为 DELETED。 */
    @Override
    public ConfigurationMutationResponseBody deleteDataSource(OperatorContext context, String dataSourceId,
        Integer expectedVersion) {
        return mutation(dataSourceApi.deleteDataSource(context.accountId(), context.sessionId(), dataSourceId,
            expectedVersion), DATA_SOURCE, dataSourceId, null, "DELETED");
    }

    /** 模板分页；元素身份为模板 ID。 */
    @Override
    public PagingResponseBody<QueryTemplateSummaryResponseBody> listQueryTemplates(OperatorContext context,
        String dataSourceId, String status, String keyword, Integer page, Integer size) {
        var result = templateApi.listQueryTemplates(context.accountId(), context.sessionId(), dataSourceId, status,
            keyword, page, size);
        return page(result, page, size, QueryTemplateSummaryResponseBody.class,
            QueryTemplateSummaryResponseBody::getTemplateId);
    }

    /** 模板详情；模板与当前版本都必须属于本次模板，当前版本号一致。 */
    @Override
    public QueryTemplateDetailResponseBody getQueryTemplate(OperatorContext context, String templateId) {
        var mapped = convert(templateApi.getQueryTemplate(context.accountId(), context.sessionId(), templateId),
            QueryTemplateDetailResponseBody.class);
        sameIdentity(templateId, mapped.getTemplateId());
        require(mapped.getCurrentVersion() != null && templateId.equals(mapped.getCurrentVersion().getTemplateId())
            && mapped.getCurrentVersion().getVersionNo() == mapped.getCurrentVersionNo());
        return mapped;
    }

    /** 新建模板；回执须为本次编码。 */
    @Override
    public SaveQueryTemplateResponseBody createQueryTemplate(OperatorContext context,
        CreateQueryTemplateRequestBody request) {
        var mapped = send(request, () -> templateApi.createQueryTemplate(context.accountId(), context.sessionId(),
            convert(request, com.paytech.platform.support.api.dataquery.request.CreateQueryTemplateRequestBody.class)),
            SaveQueryTemplateResponseBody.class);
        sameIdentity(request.getTemplateCode(), mapped.getTemplateCode());
        return mapped;
    }

    /** 修改模板；回执须为本次模板。 */
    @Override
    public SaveQueryTemplateResponseBody updateQueryTemplate(OperatorContext context, String templateId,
        UpdateQueryTemplateRequestBody request) {
        var mapped = send(request, () -> templateApi.updateQueryTemplate(context.accountId(), context.sessionId(),
            templateId, convert(request, com.paytech.platform.support.api.dataquery.request.UpdateQueryTemplateRequestBody.class)),
            SaveQueryTemplateResponseBody.class);
        sameIdentity(templateId, mapped.getTemplateId());
        return mapped;
    }

    /** 启停模板；回执须为本次模板且状态等于目标状态。 */
    @Override
    public ConfigurationMutationResponseBody changeQueryTemplateStatus(OperatorContext context, String templateId,
        ChangeQueryTemplateStatusRequestBody request) {
        return mutate(request, () -> templateApi.changeQueryTemplateStatus(context.accountId(), context.sessionId(),
            templateId, convert(request, com.paytech.platform.support.api.dataquery.request.ChangeQueryTemplateStatusRequestBody.class)),
            QUERY_TEMPLATE, templateId, null, request.getStatus());
    }

    /** 版本分页；元素身份为版本号。 */
    @Override
    public PagingResponseBody<QueryTemplateVersionSummaryResponseBody> listQueryTemplateVersions(
        OperatorContext context, String templateId, Integer page, Integer size) {
        var result = templateApi.listQueryTemplateVersions(context.accountId(), context.sessionId(), templateId, page,
            size);
        return page(result, page, size, QueryTemplateVersionSummaryResponseBody.class,
            item -> Integer.toString(item.getVersionNo()));
    }

    /** 版本内容；模板与版本号必须与请求一致。 */
    @Override
    public QueryTemplateVersionResponseBody getQueryTemplateVersion(OperatorContext context, String templateId,
        Integer versionNo) {
        var mapped = convert(templateApi.getQueryTemplateVersion(context.accountId(), context.sessionId(), templateId,
            versionNo), QueryTemplateVersionResponseBody.class);
        sameIdentity(templateId, mapped.getTemplateId());
        require(versionNo == mapped.getVersionNo());
        return mapped;
    }

    /** 恢复为新版本；回执须为本次模板。 */
    @Override
    public SaveQueryTemplateResponseBody restoreQueryTemplate(OperatorContext context, String templateId,
        RestoreQueryTemplateRequestBody request) {
        var mapped = send(request, () -> templateApi.restoreQueryTemplate(context.accountId(), context.sessionId(),
            templateId, convert(request, com.paytech.platform.support.api.dataquery.request.RestoreQueryTemplateRequestBody.class)),
            SaveQueryTemplateResponseBody.class);
        sameIdentity(templateId, mapped.getTemplateId());
        return mapped;
    }

    /** 草稿校验；有错误时 Platform 仍返回 200。 */
    @Override
    public QueryTemplateValidationResponseBody validateQueryTemplate(OperatorContext context,
        ValidateQueryTemplateRequestBody request) {
        return send(request, () -> templateApi.validateQueryTemplate(context.accountId(), context.sessionId(),
            convert(request, com.paytech.platform.support.api.dataquery.request.ValidateQueryTemplateRequestBody.class)),
            QueryTemplateValidationResponseBody.class);
    }

    /** 试运行草稿。 */
    @Override
    public TrialQueryTemplateResponseBody trialQueryTemplate(OperatorContext context,
        TrialQueryTemplateRequestBody request) {
        return send(request, () -> templateApi.trialQueryTemplate(context.accountId(), context.sessionId(),
            convert(request, com.paytech.platform.support.api.dataquery.request.TrialQueryTemplateRequestBody.class)),
            TrialQueryTemplateResponseBody.class);
    }

    /** 执行模板当前版本，以本服务名作为调用方。 */
    @Override
    public DataQueryResultResponseBody executeDataQuery(OperatorContext context, String templateCode,
        ExecuteDataQueryRequestBody request) {
        return send(request, () -> executionApi.executeDataQuery(CALLER_SERVICE, context.accountId(),
            context.sessionId(), templateCode,
            convert(request, com.paytech.platform.support.api.dataquery.request.ExecuteDataQueryRequestBody.class)),
            DataQueryResultResponseBody.class);
    }

    /** 带 Body 的转发：结果严格投影，422 字段错误按本次 Body 投影。 */
    private static <T> T send(Object request, Supplier<?> call, Class<T> type) {
        try {
            return convert(call.get(), type);
        } catch (HttpRequestValidationException failure) {
            throw DataQueryValidationConverter.local(request, failure);
        }
    }

    /** 带 Body 的写操作：核对回执，422 字段错误按本次 Body 投影。 */
    private static ConfigurationMutationResponseBody mutate(Object request,
        Supplier<com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody> call,
        String objectType, String objectId, String objectCode, String expectedStatus) {
        try {
            return mutation(call.get(), objectType, objectId, objectCode, expectedStatus);
        } catch (HttpRequestValidationException failure) {
            throw DataQueryValidationConverter.local(request, failure);
        }
    }
}
