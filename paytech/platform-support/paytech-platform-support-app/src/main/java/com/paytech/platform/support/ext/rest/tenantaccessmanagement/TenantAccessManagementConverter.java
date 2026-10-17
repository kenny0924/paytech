package com.paytech.platform.support.ext.rest.tenantaccessmanagement;

import java.util.List;

import org.springframework.stereotype.Component;

import com.paytech.foundation.web.paging.PagingRequestBody;
import com.paytech.platform.support.accesscontrol.domain.apimanagement.ClientApiManagementInputs;
import com.paytech.platform.support.accesscontrol.domain.configurationmanagement.ConfigurationInputs;
import com.paytech.platform.support.accesscontrol.domain.enums.*;
import com.paytech.platform.support.accesscontrol.domain.tenantaccess.*;
import com.paytech.platform.support.accesscontrol.service.processor.request.*;
import com.paytech.platform.support.api.tenantaccessmanagement.request.ChangeTenantSecurityStatusRequestBody;
import com.paytech.platform.support.api.tenantaccessmanagement.response.*;

/** HTTP 协议与 Owner 用例转换，不持有事务或业务授权规则。 */
@Component
public class TenantAccessManagementConverter {
    /** 转换可选筛选，保留空白为未填写的语义。 */
    public ListTenantAccessRequest tenants(String actor, String session, Integer page, Integer size, String tenantType,
        String tenantId, String ownerRefId, String tenantName, String ownerStatus, String securityStatus) {
        return new ListTenantAccessRequest(actor, session, PagingRequestBody.of(page, size),
            optionalEnum(AccessTenantType.class, tenantType), optionalId(tenantId), optionalId(ownerRefId),
            text(tenantName), optionalEnum(OperationsTenantOwnerStatus.class, ownerStatus),
            optionalEnum(OperationsTenantSecurityStatus.class, securityStatus));
    }

    /** 路径身份只做协议解析，当前归属由 Processor 检查。 */
    public GetTenantAccessRequest tenant(String actor, String session, String tenantId) {
        return new GetTenantAccessRequest(actor, session, ClientApiManagementInputs.id(tenantId));
    }

    /** 请求幂等 Header 不进入业务 Request。 */
    public ChangeTenantSecurityStatusRequest changeSecurityStatus(String actor, String session, String tenantId,
        ChangeTenantSecurityStatusRequestBody body) {
        return new ChangeTenantSecurityStatusRequest(actor, session, ClientApiManagementInputs.id(tenantId),
            ConfigurationInputs.enumeration(OperationsTenantSecurityStatus.class, body.getSecurityStatus()),
            body.getExpectedVersion(), text(body.getReason()));
    }

    /** 仅对已保存的脱敏邮箱做包含筛选。 */
    public ListTenantMembersRequest members(String actor, String session, String tenantId, Integer page, Integer size,
        String accountId, String emailMasked, String memberType, String memberStatus) {
        return new ListTenantMembersRequest(actor, session, ClientApiManagementInputs.id(tenantId),
            PagingRequestBody.of(page, size), optionalId(accountId), text(emailMasked),
            optionalEnum(TenantMemberType.class, memberType), optionalEnum(OperationsMemberStatus.class, memberStatus));
    }

    /** 成员和租户各自使用正十进制标识。 */
    public GetTenantMemberRequest member(String actor, String session, String tenantId, String memberId) {
        return new GetTenantMemberRequest(actor, session, ClientApiManagementInputs.id(tenantId),
            ClientApiManagementInputs.id(memberId));
    }

    /** 角色页复用公共分页，保留专项每页上限。 */
    public ListTenantMemberRolesRequest roles(String actor, String session, String tenantId, String memberId,
        Integer page, Integer size) {
        return new ListTenantMemberRolesRequest(actor, session, ClientApiManagementInputs.id(tenantId),
            ClientApiManagementInputs.id(memberId), PagingRequestBody.of(page, size));
    }

    /** 权限页只传递路径范围和分页参数。 */
    public ListTenantMemberPermissionsRequest permissions(String actor, String session, String tenantId,
        String memberId, Integer page, Integer size) {
        return new ListTenantMemberPermissionsRequest(actor, session, ClientApiManagementInputs.id(tenantId),
            ClientApiManagementInputs.id(memberId), PagingRequestBody.of(page, size));
    }

    /** 来源页不改变成员范围，也不增加请求状态筛选。 */
    public ListTenantPermissionSourcesRequest sources(String actor, String session, String tenantId, String memberId,
        String permissionId, Integer page, Integer size) {
        return new ListTenantPermissionSourcesRequest(actor, session, ClientApiManagementInputs.id(tenantId),
            ClientApiManagementInputs.id(memberId), ClientApiManagementInputs.id(permissionId),
            PagingRequestBody.of(page, size));
    }

    /** 可选文本去除首尾空白，长度由请求字段注解检查。 */
    private String text(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Long optionalId(String value) {
        return text(value) == null ? null : ConfigurationInputs.id(text(value));
    }

    private <E extends Enum<E>> E optionalEnum(Class<E> type, String value) {
        return text(value) == null ? null : ConfigurationInputs.enumeration(type, text(value));
    }

    /** 只返回已保存状态，不把来源状态或后续读入响应。 */
    public SecurityStatusResponseBody status(TenantSecurityStatusResult value) {
        return new SecurityStatusResponseBody(Long.toString(value.getTenantId()), value.getSecurityStatus().getCode(),
            value.getVersion());
    }

    /** 公共分页字段与统一计算时刻保持原合同。 */
    public <T, R> EvaluatedPageResponseBody<R> evaluated(EvaluatedPage<T> value,
        java.util.function.Function<T, R> convert) {
        var page = value.getPage().map(convert);
        return new EvaluatedPageResponseBody<>(page.items(), page.page(), page.size(), page.total(),
            value.getEvaluatedAt());
    }

    /** 将有序业务原因投影为安全说明。 */
    private List<Reason> reasons(List<TenantAccessReason> values) {
        return values.stream().map(v -> new Reason(v.getCode(), v.getMessage())).toList();
    }

    /** ID 始终以字符串跨越 HTTP，平台范围角色的 Tenant 保持 null。 */
    private String id(Long value) {
        return value == null ? null : value.toString();
    }

    /** 只投影合同明确允许的字段。 */
    public TenantResponseBody tenant(TenantAccessSummary row) {
        return new TenantResponseBody(id(row.getTenantId()), row.getTenantType().getCode(), id(row.getOwnerRefId()),
            row.getTenantName(), row.getOwnerStatus().getCode(), row.getSecurityStatus().getCode(), row.getVersion(),
            row.getCreatedAt(), row.getUpdatedAt(), row.getRestrictionReasons().isEmpty(),
            reasons(row.getRestrictionReasons()));
    }

    /** 只投影合同明确允许的字段。 */
    public MemberResponseBody member(TenantMemberView row) {
        return new MemberResponseBody(id(row.getTenantId()), id(row.getMemberId()), id(row.getAccountId()),
            row.getEmailMasked(), row.getMemberType().getCode(), row.getMemberStatus().getCode(),
            row.getAccountStatus().getCode(), row.getCreatedAt(), row.getUpdatedAt());
    }

    /** 只投影合同明确允许的字段。 */
    public RoleGrant role(TenantRoleGrantView row) {
        return new RoleGrant(id(row.getGrantId()), id(row.getRoleId()), row.getRoleCode(), row.getRoleName(),
            row.getRoleScope().getCode(), id(row.getRoleTenantId()), row.getRoleStatus().getCode(),
            row.getGrantStatus().getCode(), row.getEffectiveFrom(), row.getEffectiveTo(), row.isEffective(),
            reasons(row.getReasons()));
    }

    /** 只投影合同明确允许的字段。 */
    public MemberPermission permission(TenantMemberPermissionView row) {
        return new MemberPermission(id(row.getPermissionId()), row.getPermissionCode(), row.getResourceType(),
            row.getActionCode(), row.getPermissionStatus().getCode(), row.getSourceCount(),
            row.getEffectiveSourceCount(), row.isEffective(), reasons(row.getReasons()));
    }

    /** 只投影合同明确允许的字段。 */
    public PermissionSource source(TenantPermissionSourceView row) {
        return new PermissionSource(id(row.getGrantId()), id(row.getRoleId()), row.getRoleCode(), row.getRoleName(),
            row.getRoleScope().getCode(), id(row.getRoleTenantId()), row.getRoleStatus().getCode(),
            row.getGrantStatus().getCode(), row.getEffectiveFrom(), row.getEffectiveTo(), id(row.getRolePermissionId()),
            row.getRolePermissionStatus().getCode(), row.isEffective(), reasons(row.getReasons()));
    }
}
