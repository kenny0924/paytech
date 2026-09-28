package com.paytech.platform.support.api.apiscopemanagement;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;
import org.springframework.web.service.annotation.PutExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.request.CreateApiScopeRequestBody;
import com.paytech.platform.support.api.apiscopemanagement.request.UpdateApiScopeRequestBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeDetailResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeMutationResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeOptionResponseBody;
import com.paytech.platform.support.api.apiscopemanagement.response.ApiScopeSummaryResponseBody;

import jakarta.validation.Valid;

/** 全局 Scope 定义管理，逐次校验普通 Operations 会话和业务权限。 */
@HttpExchange("/api-scope-admin/v1")
public interface ApiScopeManagementApi {
    /**
     * 按编码稳定排序并搜索编码或名称。
     * 
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 API_SCOPE_READ
     * @param page
     *            页码，默认 1，范围 1..100000
     * @param size
     *            每页条数，默认 10，仅允许 10、20、50、100
     * @param keyword
     *            可选编码或名称包含查询，去除首尾空白后最多 255 字符，通配符按文字处理
     * @return 同一快照中的目录条目和总数
     */
    @GetExchange("/scopes")
    PagingResponseBody<ApiScopeSummaryResponseBody> scopes(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword);

    /**
     * 读取目录定义，引用查询由 BFF 按权限组合。
     * 
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 API_SCOPE_READ
     * @param scopeCode
     *            精确目录编码，不存在时返回 NOT_FOUND
     * @return 目录详情，无生命周期状态
     */
    @GetExchange("/scopes/{scopeCode}")
    ApiScopeDetailResponseBody scope(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("scopeCode") String scopeCode);

    /**
     * 登记独立能力，不授予凭证或创建 Route；请求幂等归 Gateway。
     * 
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 API_SCOPE_MANAGE
     * @param request
     *            目录定义及原因，编码重复返回 BUSINESS_CHECK_FAILED
     * @return 新目录身份和初始版本 1
     */
    @PostExchange("/scopes")
    ApiScopeMutationResponseBody create(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @Valid @RequestBody CreateApiScopeRequestBody request);

    /**
     * CAS 更新名称和说明，保留编码；请求幂等归 Gateway。
     * 
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 API_SCOPE_MANAGE
     * @param scopeCode
     *            精确目录编码，不存在时返回 NOT_FOUND
     * @param request
     *            当前版本、编辑内容和原因；description 必须显式提供，null 表示清空
     * @return 本次保存后的身份和版本；旧版本返回 CONCURRENT_REQUEST
     */
    @PutExchange("/scopes/{scopeCode}")
    ApiScopeMutationResponseBody update(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("scopeCode") String scopeCode,
        @Valid @RequestBody UpdateApiScopeRequestBody request);

    /**
     * 按路由读取权限分页返回编码和名称，不附带 Scope 管理权限。
     * 
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 GATEWAY_CONFIG_READ
     * @param page
     *            页码，默认 1，范围 1..100000
     * @param size
     *            每页条数，默认 10，仅允许 10、20、50、100
     * @param keyword
     *            可选编码或名称包含查询，最多 255 字符
     * @return 同一快照中的目录选项及总数，按编码升序排列
     */
    @GetExchange("/scope-options")
    PagingResponseBody<ApiScopeOptionResponseBody> options(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "keyword", required = false) String keyword);

}
