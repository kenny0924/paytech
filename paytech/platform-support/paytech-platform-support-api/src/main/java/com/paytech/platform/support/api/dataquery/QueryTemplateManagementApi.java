package com.paytech.platform.support.api.dataquery;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.configurationmanagement.response.ConfigurationMutationResponseBody;
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

import jakarta.validation.Valid;

/**
 * 数据查询的模板维护、校验与试运行；只供 operations-console-bff 私网调用，操作人由 Gateway 可信 Header 传递。
 * <li>查询需要 DATA_QUERY_READ；写操作、校验与试运行需要 DATA_QUERY_TEMPLATE_MANAGE；无权限返回 403 OPERATIONS_PERMISSION_DENIED。</li>
 * <li>保存即生效、不复核：每次保存生成新版本号，历史版本不可变。</li>
 * <li>保存校验不通过返回 422 VALIDATION_ERROR，errors[].field 为 body（message 带“第 N 行第 M 列：”前缀）、
 * parameters[i]… 、constraints.xxx、columns[i].xxx 或 dataSourceId。</li>
 * <li>写接口带 expectedVersion，版本不符返回 409；模板不存在返回 404。</li>
 */
@HttpExchange(url = "/data-query-admin/v1", accept = MediaType.APPLICATION_JSON_VALUE)
public interface QueryTemplateManagementApi {

    /**
     * 分页查询模板
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param dataSourceId 可选：当前版本的数据源 ID
     * @param status 可选：ACTIVE、INACTIVE
     * @param keyword 可选：编码或名称包含该文本，最多 64 字符
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按最后修改时间倒序
     */
    @GetExchange("/templates")
    PagingResponseBody<QueryTemplateSummaryResponseBody> listQueryTemplates(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "dataSourceId", required = false) String dataSourceId,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "keyword", required = false) String keyword,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 模板详情及当前版本内容
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param templateId 模板 ID
     * @return 模板头与当前版本
     */
    @GetExchange("/templates/{templateId}")
    QueryTemplateDetailResponseBody getQueryTemplate(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("templateId") String templateId);

    /**
     * 新建模板，保存为版本 1，状态 ACTIVE
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 编码、名称、数据源、正文、参数、列与约束
     * @return 保存结果与警告
     *         <li>编码已存在返回 400；校验不通过返回 422（见类说明）。</li>
     */
    @PostExchange("/templates")
    SaveQueryTemplateResponseBody createQueryTemplate(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateQueryTemplateRequestBody request);

    /**
     * 保存为新版本并立即生效
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param templateId 模板 ID
     * @param request 新内容、变更说明与原版本
     * @return 保存结果与警告；即使内容未变也生成新版本
     */
    @PutExchange("/templates/{templateId}")
    SaveQueryTemplateResponseBody updateQueryTemplate(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("templateId") String templateId,
        @Valid @RequestBody UpdateQueryTemplateRequestBody request);

    /**
     * 启用或停用模板
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param templateId 模板 ID
     * @param request 目标状态与原版本
     * @return 保存结果，objectType=QUERY_TEMPLATE
     *         <li>启用要求当前版本数据源为 ACTIVE，否则返回 400。目标状态与当前相同时不递增版本。</li>
     */
    @PostExchange("/templates/{templateId}/status")
    ConfigurationMutationResponseBody changeQueryTemplateStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("templateId") String templateId,
        @Valid @RequestBody ChangeQueryTemplateStatusRequestBody request);

    /**
     * 版本列表
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param templateId 模板 ID
     * @param page 页码，默认 1
     * @param size 每页条数，默认 10，只允许 10、20、50、100、200
     * @return 按版本号倒序
     */
    @GetExchange("/templates/{templateId}/versions")
    PagingResponseBody<QueryTemplateVersionSummaryResponseBody> listQueryTemplateVersions(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("templateId") String templateId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 某个版本的完整内容，用于对比
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param templateId 模板 ID
     * @param versionNo 版本号
     * @return 版本内容；版本不存在返回 404
     */
    @GetExchange("/templates/{templateId}/versions/{versionNo}")
    QueryTemplateVersionResponseBody getQueryTemplateVersion(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("templateId") String templateId,
        @PathVariable("versionNo") Integer versionNo);

    /**
     * 以历史版本内容生成新版本
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param templateId 模板 ID
     * @param request 被恢复的版本号、原版本与变更说明
     * @return 保存结果与警告；按当前规则与当前数据源状态重新校验，不通过返回 422
     */
    @PostExchange("/templates/{templateId}/restoration")
    SaveQueryTemplateResponseBody restoreQueryTemplate(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("templateId") String templateId,
        @Valid @RequestBody RestoreQueryTemplateRequestBody request);

    /**
     * 编辑器实时校验，不保存
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 草稿
     * @return 问题列表；有错误也返回 200，valid=false
     */
    @PostExchange("/template-validations")
    QueryTemplateValidationResponseBody validateQueryTemplate(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody ValidateQueryTemplateRequestBody request);

    /**
     * 试运行草稿，不保存
     *
     * @param actor Gateway 传递的可信操作人账号
     * @param session Gateway 传递的可信会话标识
     * @param request 草稿、参数与分页
     * @return 结果、实际执行的 SQL 与绑定值
     *         <li>草稿有 ERROR 级问题返回 422；参数错误返回 422（field 为 params…）；只读校验未通过返回 400。</li>
     *         <li>超时、结果过大、数据源不可用分别返回 DATA_QUERY_TIMEOUT、DATA_QUERY_RESULT_TOO_LARGE、DATA_SOURCE_UNAVAILABLE。</li>
     */
    @PostExchange("/template-trials")
    TrialQueryTemplateResponseBody trialQueryTemplate(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody TrialQueryTemplateRequestBody request);
}
