package com.paytech.platform.support.api.apicredentialmanagement;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.apicredentialmanagement.request.RevokeApiCredentialRequestBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialDetailResponseBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialRevokeResponseBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialScopeGrantResponseBody;
import com.paytech.platform.support.api.apicredentialmanagement.response.ApiCredentialSummaryResponseBody;

import jakarta.validation.Valid;

/** Operations 凭证查看和独立授权的紧急撤销入口。 */
@HttpExchange("/api-credential-admin/v1")
public interface ApiCredentialManagementApi {
    /**
     * 按 AND 条件分页查询凭证，包含失效 Tenant 及已撤销、已过期凭证。
     *
     * @param actor
     *            可信入口提供的 Operations 账号标识
     * @param session
     *            可信入口提供的 Operations 会话标识，实时复核普通会话及读取权限
     * @param page
     *            页码，省略时为 1，范围 1..100000
     * @param size
     *            每页条数，省略时为 10，仅允许 10、20、50、100
     * @param clientId
     *            可选的 Client 精确标识，正十进制 Long 字符串
     * @param clientName
     *            可选的 Client 名称包含匹配，去除首尾空白后长度为 1..255，通配符按文字处理
     * @param credentialId
     *            可选的凭证精确标识，正十进制 Long 字符串
     * @param keyPrefix
     *            可选的非秘密前缀精确匹配，不接受完整凭证
     * @param status
     *            可选的派生状态，精确匹配 ACTIVE、REVOKED 或 EXPIRED
     * @param scopeCode
     *            可选的 Scope 精确编码，匹配任意状态授权且不重复返回凭证
     * @return 同一快照及查询时点下的列表和总数；空页仍为成功响应
     */
    @GetExchange("/credentials")
    PagingResponseBody<ApiCredentialSummaryResponseBody> credentials(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "clientId", required = false) String clientId,
        @RequestParam(value = "clientName", required = false) String clientName,
        @RequestParam(value = "credentialId", required = false) String credentialId,
        @RequestParam(value = "keyPrefix", required = false) String keyPrefix,
        @RequestParam(value = "status", required = false) String status,
        @RequestParam(value = "scopeCode", required = false) String scopeCode);

    /**
     * 读取非秘密详情，包含失效 Tenant 与已失效凭证；未知 ID 返回 NOT_FOUND。
     * 
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 API_CREDENTIAL_READ
     * @param credentialId
     *            正十进制凭证标识
     * @return 按本次查询时点派生状态的详情，轮换来源必须属于同一 Tenant
     */
    @GetExchange("/credentials/{credentialId}")
    ApiCredentialDetailResponseBody credential(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("credentialId") String credentialId);

    /**
     * 分页读取全部授权记录，包含未来和失效区间；状态独立于父凭证。
     * 
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，要求 API_CREDENTIAL_READ
     * @param credentialId
     *            正十进制凭证标识，目标不存在时返回 NOT_FOUND
     * @param page
     *            页码，默认 1，范围 1..100000
     * @param size
     *            每页条数，默认 10，仅允许 10、20、50、100
     * @return 同一查询快照的授权明细及总数，按授权主键升序排列
     */
    @GetExchange("/credentials/{credentialId}/scopes")
    PagingResponseBody<ApiCredentialScopeGrantResponseBody> scopes(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("credentialId") String credentialId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 按当前版本撤销指定凭证；已撤销且版本匹配时返回当前结果。
     * 
     * @param actor
     *            可信 Operations 账号标识
     * @param session
     *            当前普通 Operations 会话，每次均要求 API_CREDENTIAL_REVOKE
     * @param credentialId
     *            正十进制凭证标识，不级联修改其他凭证或授权行
     * @param request
     *            当前版本与撤销原因，版本不匹配时返回并发冲突
     * @return 本次命令的 REVOKED 状态及结果版本
     */
    @PostExchange("/credentials/{credentialId}/revoke")
    ApiCredentialRevokeResponseBody revoke(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("credentialId") String credentialId,
        @Valid @RequestBody RevokeApiCredentialRequestBody request);

}
