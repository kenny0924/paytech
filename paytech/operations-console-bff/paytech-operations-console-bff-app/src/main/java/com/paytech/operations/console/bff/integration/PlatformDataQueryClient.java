package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.ConfigurationManagementModels.ConfigurationMutationResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.dataquery.request.ChangeDataSourceStatusRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ChangeQueryKeyTypeStatusRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ChangeQueryTemplateStatusRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.CreateDataSourceRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.CreateQueryKeyTypeRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.CreateQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ExecuteDataQueryRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.JointQueryStepRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ReorderQueryKeyTypesRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.RestoreQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.TestDataSourceConnectionRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.TrialQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.UpdateDataSourceRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.UpdateQueryKeyTypeRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.UpdateQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.request.ValidateQueryTemplateRequestBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.DataQueryResultResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.DataSourceConnectionTestResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.DataSourceResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.JointQueryGraphResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.JointQuerySettingsResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.JointQueryStepResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryKeyTypeResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateDetailResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateValidationResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateVersionResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.QueryTemplateVersionSummaryResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.ReorderQueryKeyTypesResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.SaveQueryTemplateResponseBody;
import com.paytech.operations.console.bff.service.model.dataquery.response.TrialQueryTemplateResponseBody;

/**
 * Platform 数据查询的本地端口：数据源、查询模板、联合查询键类型的维护与试运行，以及模板执行与联合查询
 * <li>数据源、模板事实、键类型、只读校验、执行、联合查询展开与 DATA_QUERY_* 权限的再次校验归 Platform；BFF 只做同路径转发与合同核对。</li>
 * <li>查询与写操作都以会话 Account ID 与 Session ID 作为操作人 Header 转发；执行与联合查询另以本服务名作为调用方。</li>
 *
 * @author Claude
 */
public interface PlatformDataQueryClient {

    /**
     * 分页查询数据源（不含 DELETED），按最后修改时间倒序
     *
     * @param context 操作人
     * @param status 可选状态
     * @param engineType 可选引擎类型
     * @param keyword 可选编码或名称关键字
     * @param page 页码，缺省 1
     * @param size 每页条数，缺省 10
     * @return 数据源分页
     */
    PagingResponseBody<DataSourceResponseBody> listDataSources(OperatorContext context, String status,
        String engineType, String keyword, Integer page, Integer size);

    /**
     * 数据源详情
     *
     * @param context 操作人
     * @param dataSourceId 数据源 ID
     * @return 配置、最后一次检查与引用模板数，不含密码
     */
    DataSourceResponseBody getDataSource(OperatorContext context, String dataSourceId);

    /**
     * 新建数据源，状态为 INACTIVE
     *
     * @param context 操作人
     * @param request 页面请求，含密码
     * @return 保存回执，objectCode 为数据源编码
     */
    ConfigurationMutationResponseBody createDataSource(OperatorContext context, CreateDataSourceRequestBody request);

    /**
     * 按原版本修改数据源；密码为空表示保留
     *
     * @param context 操作人
     * @param dataSourceId 数据源 ID
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody updateDataSource(OperatorContext context, String dataSourceId,
        UpdateDataSourceRequestBody request);

    /**
     * 测试草稿连接配置，不保存
     *
     * @param context 操作人
     * @param request 草稿配置
     * @return 检查结果；未通过也正常返回
     */
    DataSourceConnectionTestResponseBody testDataSourceConnection(OperatorContext context,
        TestDataSourceConnectionRequestBody request);

    /**
     * 启用或停用数据源
     *
     * @param context 操作人
     * @param dataSourceId 数据源 ID
     * @param request 目标状态与原版本
     * @return 保存回执，状态等于目标状态
     */
    ConfigurationMutationResponseBody changeDataSourceStatus(OperatorContext context, String dataSourceId,
        ChangeDataSourceStatusRequestBody request);

    /**
     * 软删除数据源
     *
     * @param context 操作人
     * @param dataSourceId 数据源 ID
     * @param expectedVersion 页面读取的原版本
     * @return 保存回执，状态为 DELETED
     */
    ConfigurationMutationResponseBody deleteDataSource(OperatorContext context, String dataSourceId,
        Integer expectedVersion);

    /**
     * 分页查询模板，按最后修改时间倒序
     *
     * @param context 操作人
     * @param dataSourceId 可选当前版本数据源 ID
     * @param status 可选状态
     * @param keyword 可选编码或名称关键字
     * @param page 页码，缺省 1
     * @param size 每页条数，缺省 10
     * @return 模板分页
     */
    PagingResponseBody<QueryTemplateSummaryResponseBody> listQueryTemplates(OperatorContext context,
        String dataSourceId, String status, String keyword, Integer page, Integer size);

    /**
     * 模板详情及当前版本内容
     *
     * @param context 操作人
     * @param templateId 模板 ID
     * @return 模板头与当前版本
     */
    QueryTemplateDetailResponseBody getQueryTemplate(OperatorContext context, String templateId);

    /**
     * 新建模板，保存为版本 1
     *
     * @param context 操作人
     * @param request 页面请求
     * @return 保存结果与警告
     */
    SaveQueryTemplateResponseBody createQueryTemplate(OperatorContext context, CreateQueryTemplateRequestBody request);

    /**
     * 保存为新版本并立即生效
     *
     * @param context 操作人
     * @param templateId 模板 ID
     * @param request 页面请求
     * @return 保存结果与警告
     */
    SaveQueryTemplateResponseBody updateQueryTemplate(OperatorContext context, String templateId,
        UpdateQueryTemplateRequestBody request);

    /**
     * 启用或停用模板
     *
     * @param context 操作人
     * @param templateId 模板 ID
     * @param request 目标状态与原版本
     * @return 保存回执，状态等于目标状态
     */
    ConfigurationMutationResponseBody changeQueryTemplateStatus(OperatorContext context, String templateId,
        ChangeQueryTemplateStatusRequestBody request);

    /**
     * 模板版本列表，按版本号倒序
     *
     * @param context 操作人
     * @param templateId 模板 ID
     * @param page 页码，缺省 1
     * @param size 每页条数，缺省 10
     * @return 版本分页
     */
    PagingResponseBody<QueryTemplateVersionSummaryResponseBody> listQueryTemplateVersions(OperatorContext context,
        String templateId, Integer page, Integer size);

    /**
     * 某个版本的完整内容
     *
     * @param context 操作人
     * @param templateId 模板 ID
     * @param versionNo 版本号
     * @return 版本内容
     */
    QueryTemplateVersionResponseBody getQueryTemplateVersion(OperatorContext context, String templateId,
        Integer versionNo);

    /**
     * 以历史版本内容生成新版本
     *
     * @param context 操作人
     * @param templateId 模板 ID
     * @param request 被恢复的版本号与原版本
     * @return 保存结果与警告
     */
    SaveQueryTemplateResponseBody restoreQueryTemplate(OperatorContext context, String templateId,
        RestoreQueryTemplateRequestBody request);

    /**
     * 编辑器实时校验草稿，不保存
     *
     * @param context 操作人
     * @param request 草稿
     * @return 问题列表；有错误也正常返回
     */
    QueryTemplateValidationResponseBody validateQueryTemplate(OperatorContext context,
        ValidateQueryTemplateRequestBody request);

    /**
     * 试运行草稿，不保存
     *
     * @param context 操作人
     * @param request 草稿、参数与分页
     * @return 结果、实际执行的 SQL 与绑定值
     */
    TrialQueryTemplateResponseBody trialQueryTemplate(OperatorContext context, TrialQueryTemplateRequestBody request);

    /**
     * 按模板编码执行当前版本
     *
     * @param context 操作人
     * @param templateCode 模板编码
     * @param request 参数与分页
     * @return 查询结果，带实际执行的版本号
     */
    DataQueryResultResponseBody executeDataQuery(OperatorContext context, String templateCode,
        ExecuteDataQueryRequestBody request);

    /**
     * 分页查询联合查询键类型，按 sortNo、keyTypeCode 升序
     *
     * @param context 操作人
     * @param status 可选状态
     * @param keyword 可选编码、名称或分组关键字
     * @param page 页码，缺省 1
     * @param size 每页条数，缺省 10
     * @return 键类型分页，每项带引用模板数
     */
    PagingResponseBody<QueryKeyTypeResponseBody> listQueryKeyTypes(OperatorContext context, String status,
        String keyword, Integer page, Integer size);

    /**
     * 新建键类型，状态为 ACTIVE
     *
     * @param context 操作人
     * @param request 页面请求
     * @return 保存回执，objectCode 为键类型编码
     */
    ConfigurationMutationResponseBody createQueryKeyType(OperatorContext context, CreateQueryKeyTypeRequestBody request);

    /**
     * 按原版本修改键类型；编码不可修改
     *
     * @param context 操作人
     * @param keyTypeId 键类型 ID
     * @param request 页面请求
     * @return 保存回执
     */
    ConfigurationMutationResponseBody updateQueryKeyType(OperatorContext context, String keyTypeId,
        UpdateQueryKeyTypeRequestBody request);

    /**
     * 启用或停用键类型
     *
     * @param context 操作人
     * @param keyTypeId 键类型 ID
     * @param request 目标状态与原版本
     * @return 保存回执，状态等于目标状态
     */
    ConfigurationMutationResponseBody changeQueryKeyTypeStatus(OperatorContext context, String keyTypeId,
        ChangeQueryKeyTypeStatusRequestBody request);

    /**
     * 保存全部键类型的新顺序
     *
     * @param context 操作人
     * @param request 全部键类型按新顺序排列及各自原版本
     * @return 按新顺序的 sortNo 与新版本
     */
    ReorderQueryKeyTypesResponseBody reorderQueryKeyTypes(OperatorContext context,
        ReorderQueryKeyTypesRequestBody request);

    /**
     * 联合查询页初始化数据
     *
     * @param context 操作人
     * @return 入口键类型、分组与护栏
     */
    JointQuerySettingsResponseBody getJointQuerySettings(OperatorContext context);

    /**
     * 从入口键类型出发的可达预览，不执行查询
     *
     * @param context 操作人
     * @param entryKeyType 入口键类型编码
     * @return 按泳道分组的可达模板、链路断点与不参与的模板
     */
    JointQueryGraphResponseBody getJointQueryGraph(OperatorContext context, String entryKeyType);

    /**
     * 联合查询执行一层
     *
     * @param context 操作人
     * @param request 层号、本层键值与可选的模板范围
     * @return 本层节点、LINK 值、发现的键与截断
     */
    JointQueryStepResponseBody executeJointQueryStep(OperatorContext context, JointQueryStepRequestBody request);
}
