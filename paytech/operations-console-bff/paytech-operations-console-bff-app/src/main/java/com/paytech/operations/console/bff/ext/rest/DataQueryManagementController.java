package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformDataQueryClient;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.dataquery.request.ChangeDataSourceStatusRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ChangeQueryTemplateStatusRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.CreateDataSourceRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.CreateQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.RestoreQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.TestDataSourceConnectionRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.TrialQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.UpdateDataSourceRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.UpdateQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ValidateQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.DataSourceConnectionTestResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.DataSourceResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateDetailResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateValidationResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateVersionResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateVersionSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.SaveQueryTemplateResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.TrialQueryTemplateResponseBody;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;

/**
 * 数据查询页（数据源、查询模板、模板编辑器）的浏览器入口，全部同路径简单转发 Platform
 * <li>每个路由由 Gateway 绑定 DATA_QUERY_READ（查询）、DATA_SOURCE_MANAGE（数据源维护与测试连接）或
 * DATA_QUERY_TEMPLATE_MANAGE（模板维护、校验与试运行），并经 PLT-I02 实时授权；Platform 再次校验权限。</li>
 * <li>Query 与 Body 按配置管理的严格边界解析：未知字段、重复或多余 Query、查询带 Body 均在访问 Platform 前拒绝。</li>
 * <li>新建、修改与测试连接的 Body 含数据源密码，路径只记元数据日志。</li>
 *
 * @author Claude
 */
@RestController
@AllArgsConstructor
@RequestMapping("/data-query-admin/v1")
public class DataQueryManagementController {
    private static final String ID = "[1-9][0-9]{0,18}";

    private final PlatformDataQueryClient client;

    /** 数据源分页，按最后修改时间倒序，不含已删除。 */
    @GetMapping("/data-sources")
    public PagingResponseBody<DataSourceResponseBody> listDataSources(OperatorContext context,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "INACTIVE|ACTIVE") String status,
        @RequestParam(value = "engineType", required = false) @Pattern(regexp = "MYSQL|DORIS") String engineType,
        @RequestParam(value = "keyword", required = false) @Size(max = 64) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listDataSources(context, status, engineType, keyword, page, size);
    }

    /** 数据源详情，已删除的也可读取。 */
    @GetMapping("/data-sources/{dataSourceId}")
    public DataSourceResponseBody getDataSource(OperatorContext context,
        @PathVariable("dataSourceId") @Pattern(regexp = ID) String dataSourceId) {
        return client.getDataSource(context, dataSourceId);
    }

    /** 新建 INACTIVE 数据源。 */
    @PostMapping("/data-sources")
    public ConfigurationMutationResponseBody createDataSource(OperatorContext context,
        @Valid @RequestBody CreateDataSourceRequestBody request) {
        return client.createDataSource(context, request);
    }

    /** 按原版本修改数据源；密码为空表示保留，ACTIVE 时 Platform 先做连接检查。 */
    @PutMapping("/data-sources/{dataSourceId}")
    public ConfigurationMutationResponseBody updateDataSource(OperatorContext context,
        @PathVariable("dataSourceId") @Pattern(regexp = ID) String dataSourceId,
        @Valid @RequestBody UpdateDataSourceRequestBody request) {
        return client.updateDataSource(context, dataSourceId, request);
    }

    /** 测试草稿连接配置，不保存。 */
    @PostMapping("/data-source-connection-tests")
    public DataSourceConnectionTestResponseBody testDataSourceConnection(OperatorContext context,
        @Valid @RequestBody TestDataSourceConnectionRequestBody request) {
        return client.testDataSourceConnection(context, request);
    }

    /** 启用（Platform 先做连接检查）或停用数据源。 */
    @PostMapping("/data-sources/{dataSourceId}/status")
    public ConfigurationMutationResponseBody changeDataSourceStatus(OperatorContext context,
        @PathVariable("dataSourceId") @Pattern(regexp = ID) String dataSourceId,
        @Valid @RequestBody ChangeDataSourceStatusRequestBody request) {
        return client.changeDataSourceStatus(context, dataSourceId, request);
    }

    /** 软删除 INACTIVE 且无 ACTIVE 模板引用的数据源。 */
    @DeleteMapping("/data-sources/{dataSourceId}")
    public ConfigurationMutationResponseBody deleteDataSource(OperatorContext context,
        @PathVariable("dataSourceId") @Pattern(regexp = ID) String dataSourceId,
        @RequestParam("expectedVersion") @NotNull @Positive Integer expectedVersion) {
        return client.deleteDataSource(context, dataSourceId, expectedVersion);
    }

    /** 模板分页，按最后修改时间倒序。 */
    @GetMapping("/templates")
    public PagingResponseBody<QueryTemplateSummaryResponseBody> listQueryTemplates(OperatorContext context,
        @RequestParam(value = "dataSourceId", required = false) @Pattern(regexp = ID) String dataSourceId,
        @RequestParam(value = "status", required = false) @Pattern(regexp = "ACTIVE|INACTIVE") String status,
        @RequestParam(value = "keyword", required = false) @Size(max = 64) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listQueryTemplates(context, dataSourceId, status, keyword, page, size);
    }

    /** 模板详情及当前版本内容。 */
    @GetMapping("/templates/{templateId}")
    public QueryTemplateDetailResponseBody getQueryTemplate(OperatorContext context,
        @PathVariable("templateId") @Pattern(regexp = ID) String templateId) {
        return client.getQueryTemplate(context, templateId);
    }

    /** 新建模板，保存为版本 1 并立即生效。 */
    @PostMapping("/templates")
    public SaveQueryTemplateResponseBody createQueryTemplate(OperatorContext context,
        @Valid @RequestBody CreateQueryTemplateRequestBody request) {
        return client.createQueryTemplate(context, request);
    }

    /** 保存为新版本并立即生效。 */
    @PutMapping("/templates/{templateId}")
    public SaveQueryTemplateResponseBody updateQueryTemplate(OperatorContext context,
        @PathVariable("templateId") @Pattern(regexp = ID) String templateId,
        @Valid @RequestBody UpdateQueryTemplateRequestBody request) {
        return client.updateQueryTemplate(context, templateId, request);
    }

    /** 启用或停用模板。 */
    @PostMapping("/templates/{templateId}/status")
    public ConfigurationMutationResponseBody changeQueryTemplateStatus(OperatorContext context,
        @PathVariable("templateId") @Pattern(regexp = ID) String templateId,
        @Valid @RequestBody ChangeQueryTemplateStatusRequestBody request) {
        return client.changeQueryTemplateStatus(context, templateId, request);
    }

    /** 版本列表，按版本号倒序。 */
    @GetMapping("/templates/{templateId}/versions")
    public PagingResponseBody<QueryTemplateVersionSummaryResponseBody> listQueryTemplateVersions(
        OperatorContext context, @PathVariable("templateId") @Pattern(regexp = ID) String templateId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.listQueryTemplateVersions(context, templateId, page, size);
    }

    /** 某个版本的完整内容，用于对比。 */
    @GetMapping("/templates/{templateId}/versions/{versionNo}")
    public QueryTemplateVersionResponseBody getQueryTemplateVersion(OperatorContext context,
        @PathVariable("templateId") @Pattern(regexp = ID) String templateId,
        @PathVariable("versionNo") @Positive Integer versionNo) {
        return client.getQueryTemplateVersion(context, templateId, versionNo);
    }

    /** 以历史版本内容生成新版本。 */
    @PostMapping("/templates/{templateId}/restoration")
    public SaveQueryTemplateResponseBody restoreQueryTemplate(OperatorContext context,
        @PathVariable("templateId") @Pattern(regexp = ID) String templateId,
        @Valid @RequestBody RestoreQueryTemplateRequestBody request) {
        return client.restoreQueryTemplate(context, templateId, request);
    }

    /** 编辑器实时校验草稿，不保存。 */
    @PostMapping("/template-validations")
    public QueryTemplateValidationResponseBody validateQueryTemplate(OperatorContext context,
        @Valid @RequestBody ValidateQueryTemplateRequestBody request) {
        return client.validateQueryTemplate(context, request);
    }

    /** 试运行草稿，不保存。 */
    @PostMapping("/template-trials")
    public TrialQueryTemplateResponseBody trialQueryTemplate(OperatorContext context,
        @Valid @RequestBody TrialQueryTemplateRequestBody request) {
        return client.trialQueryTemplate(context, request);
    }
}
