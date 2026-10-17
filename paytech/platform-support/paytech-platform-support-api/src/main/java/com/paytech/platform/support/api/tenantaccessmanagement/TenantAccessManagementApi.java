package com.paytech.platform.support.api.tenantaccessmanagement;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.platform.support.api.tenantaccessmanagement.request.ChangeTenantSecurityStatusRequestBody;
import com.paytech.platform.support.api.tenantaccessmanagement.response.*;

import jakarta.validation.Valid;

/** 由 Platform 拥有租户安全状态及成员授权事实；调用者使用可信 Operations 身份。 */
@HttpExchange("/tenant-access-admin/v1/tenants")
public interface TenantAccessManagementApi {

    /**
     * 分页查询客户与合作伙伴租户，已失效状态仍可见。
     * 
     * @param actor
     *            可信 Operations 操作人账号，由入口传入。
     * @param session
     *            可信 Operations 会话标识，由入口传入。
     * @param page
     *            页码，可空，默认 1。
     * @param size
     *            每页条数，可空，默认 10；支持 10、20、50、100。
     * @param tenantType
     *            租户类型筛选，可空，仅支持 CLIENT、PARTNER。
     * @param tenantId
     *            租户标识精确筛选，可空。
     * @param ownerRefId
     *            所属客户或合作伙伴标识筛选，可空。
     * @param tenantName
     *            租户名称包含筛选，可空；去除首尾空白后最多 255 字符。
     * @param ownerStatus
     *            来源业务状态筛选，可空。
     * @param securityStatus
     *            平台安全状态筛选，可空。
     * @return 同一快照中的租户分页与真实总数。
     */
    @GetExchange("")
    PagingResponseBody<TenantResponseBody> tenants(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "tenantType", required = false) String tenantType,
        @RequestParam(value = "tenantId", required = false) String tenantId,
        @RequestParam(value = "ownerRefId", required = false) String ownerRefId,
        @RequestParam(value = "tenantName", required = false) String tenantName,
        @RequestParam(value = "ownerStatus", required = false) String ownerStatus,
        @RequestParam(value = "securityStatus", required = false) String securityStatus);

    /**
     * 查询租户当前访问条件，INTERNAL 与不存在的租户返回 404。
     * 
     * @param actor
     *            可信 Operations 操作人账号，由入口传入。
     * @param session
     *            可信 Operations 会话标识，由入口传入。
     * @param tenantId
     *            目标租户的正十进制标识。
     * @return 租户状态及当前访问限制。
     */
    @GetExchange("/{tenantId}")
    TenantResponseBody tenant(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("tenantId") String tenantId);

    /**
     * 按原版本暂停或恢复租户安全状态；请求幂等由 Gateway 处理。
     * 
     * @param actor
     *            可信 Operations 操作人账号，由入口传入。
     * @param session
     *            可信 Operations 会话标识，由入口传入。
     * @param tenantId
     *            目标租户的正十进制标识。
     * @param request
     *            目标安全状态、正整数原版本及非敏感操作原因。
     * @return 当次已确认的租户标识、安全状态与版本；同状态不推进版本。
     */
    @PostExchange("/{tenantId}/security-status")
    SecurityStatusResponseBody changeSecurityStatus(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("tenantId") String tenantId,
        @Valid @RequestBody ChangeTenantSecurityStatusRequestBody request);

    /**
     * 查询指定租户的成员，保留失效成员及脱敏账号信息。
     * 
     * @param actor
     *            可信 Operations 操作人账号，由入口传入。
     * @param session
     *            可信 Operations 会话标识，由入口传入。
     * @param tenantId
     *            目标租户的正十进制标识。
     * @param page
     *            页码，可空，默认 1。
     * @param size
     *            每页条数，可空，默认 10；支持 10、20、50、100。
     * @param accountId
     *            账号标识筛选，可空。
     * @param emailMasked
     *            脱敏邮箱显示值包含筛选，可空。
     * @param memberType
     *            成员类型筛选，可空。
     * @param memberStatus
     *            成员状态筛选，可空。
     * @return 同一租户范围内的成员分页与真实总数。
     */
    @GetExchange("/{tenantId}/members")
    PagingResponseBody<MemberResponseBody> members(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("tenantId") String tenantId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "accountId", required = false) String accountId,
        @RequestParam(value = "emailMasked", required = false) String emailMasked,
        @RequestParam(value = "memberType", required = false) String memberType,
        @RequestParam(value = "memberStatus", required = false) String memberStatus);

    /**
     * 查询租户内的成员，跨租户成员返回 404。
     * 
     * @param actor
     *            可信 Operations 操作人账号，由入口传入。
     * @param session
     *            可信 Operations 会话标识，由入口传入。
     * @param tenantId
     *            目标租户的正十进制标识。
     * @param memberId
     *            路径租户内的成员标识。
     * @return 成员及其全局账号的安全显示字段。
     */
    @GetExchange("/{tenantId}/members/{memberId}")
    MemberResponseBody member(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("tenantId") String tenantId,
        @PathVariable("memberId") String memberId);

    /**
     * 分页查询成员角色授予，按同一数据库时刻解释有效性。
     * 
     * @param actor
     *            可信 Operations 操作人账号，由入口传入。
     * @param session
     *            可信 Operations 会话标识，由入口传入。
     * @param tenantId
     *            目标租户的正十进制标识。
     * @param memberId
     *            路径租户内的成员标识。
     * @param page
     *            页码，可空，默认 1。
     * @param size
     *            每页条数，可空，默认 10；支持 10、20、50、100。
     * @return 角色授予分页、失效原因及统一计算时刻。
     */
    @GetExchange("/{tenantId}/members/{memberId}/roles")
    EvaluatedPageResponseBody<RoleGrant> roles(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("tenantId") String tenantId,
        @PathVariable("memberId") String memberId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 合并成员权限来源，任一完整来源有效即为有效授权。
     * 
     * @param actor
     *            可信 Operations 操作人账号，由入口传入。
     * @param session
     *            可信 Operations 会话标识，由入口传入。
     * @param tenantId
     *            目标租户的正十进制标识。
     * @param memberId
     *            路径租户内的成员标识。
     * @param page
     *            页码，可空，默认 1。
     * @param size
     *            每页条数，可空，默认 10；支持 10、20、50、100。
     * @return 权限汇总分页、有效来源数及统一计算时刻。
     */
    @GetExchange("/{tenantId}/members/{memberId}/permissions")
    EvaluatedPageResponseBody<MemberPermission> permissions(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("tenantId") String tenantId,
        @PathVariable("memberId") String memberId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);

    /**
     * 分页查询成员某项权限的完整来源，没有来源返回 404。
     * 
     * @param actor
     *            可信 Operations 操作人账号，由入口传入。
     * @param session
     *            可信 Operations 会话标识，由入口传入。
     * @param tenantId
     *            目标租户的正十进制标识。
     * @param memberId
     *            路径租户内的成员标识。
     * @param permissionId
     *            该成员授权链涉及的权限标识。
     * @param page
     *            页码，可空，默认 1。
     * @param size
     *            每页条数，可空，默认 10；支持 10、20、50、100。
     * @return 来源分页、完整失效原因及统一计算时刻。
     */
    @GetExchange("/{tenantId}/members/{memberId}/permissions/{permissionId}/sources")
    EvaluatedPageResponseBody<PermissionSource> sources(
        @RequestHeader("X-Gateway-Actor-Subject") String actor,
        @RequestHeader("X-Gateway-Session-Id") String session,
        @PathVariable("tenantId") String tenantId,
        @PathVariable("memberId") String memberId,
        @PathVariable("permissionId") String permissionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size);
}
