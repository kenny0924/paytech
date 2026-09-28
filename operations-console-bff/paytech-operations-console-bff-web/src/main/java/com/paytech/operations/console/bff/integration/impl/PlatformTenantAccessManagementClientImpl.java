package com.paytech.operations.console.bff.integration.impl;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.ClientApiContractConverter;
import com.paytech.operations.console.bff.integration.PlatformTenantAccessManagementClient;
import com.paytech.operations.console.bff.integration.TenantAccessContractConverter;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.TenantAccessManagementModels.*;
import com.paytech.platform.support.api.tenantaccessmanagement.TenantAccessManagementApi;

/** 沿用实时 PLT-I02 授权与可信 Operations 身份的租户访问转发。 */
@org.springframework.stereotype.Component
@lombok.AllArgsConstructor
public class PlatformTenantAccessManagementClientImpl implements PlatformTenantAccessManagementClient {
    private final TenantAccessManagementApi management;

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @Override
    public PagingResponseBody<TenantResponseBody> tenants(
        OperatorContext context,
        Integer page,
        Integer size,
        String tenantType,
        String tenantId,
        String ownerRefId,
        String tenantName,
        String ownerStatus,
        String securityStatus) {
        var result = management.tenants(context.accountId(), context.sessionId(), page, size, tenantType, tenantId, ownerRefId, tenantName, ownerStatus, securityStatus);
        return ClientApiContractConverter.page(result, page, size, TenantAccessContractConverter::toBff, TenantResponseBody::tenantId);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @Override
    public TenantResponseBody tenant(
        OperatorContext context,
        String tenantId) {
        var result = management.tenant(context.accountId(), context.sessionId(), tenantId);
        var mapped = TenantAccessContractConverter.toBff(result);
        ClientApiContractConverter.sameIdentity(tenantId, mapped.tenantId());
        return mapped;
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @Override
    public SecurityStatusResponseBody changeSecurityStatus(
        OperatorContext context,
        String tenantId,
        ChangeTenantSecurityStatusRequestBody request) {
        try {
            var result = management.changeSecurityStatus(context.accountId(), context.sessionId(), tenantId, new com.paytech.platform.support.api.tenantaccessmanagement.request.ChangeTenantSecurityStatusRequestBody(request.securityStatus(), request.expectedVersion(), request.reason()));
            var mapped = TenantAccessContractConverter.toBff(result);
            ClientApiContractConverter.sameIdentity(tenantId, mapped.tenantId());
            ClientApiContractConverter.sameIdentity(request.securityStatus(), mapped.securityStatus());
            return mapped;
        } catch (com.paytech.foundation.httpclient.api.HttpRequestValidationException failure) {
            throw ConfigurationValidationConverter.local(request, failure);
        }
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @Override
    public PagingResponseBody<MemberResponseBody> members(
        OperatorContext context,
        String tenantId,
        Integer page,
        Integer size,
        String accountId,
        String emailMasked,
        String memberType,
        String memberStatus) {
        var result = management.members(context.accountId(), context.sessionId(), tenantId, page, size, accountId, emailMasked, memberType, memberStatus);
        return ClientApiContractConverter.page(result, page, size, value -> { var mapped = TenantAccessContractConverter.toBff(value); ClientApiContractConverter.sameIdentity(tenantId, mapped.tenantId()); return mapped; }, MemberResponseBody::memberId);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @Override
    public MemberResponseBody member(
        OperatorContext context,
        String tenantId,
        String memberId) {
        var result = management.member(context.accountId(), context.sessionId(), tenantId, memberId);
        var mapped = TenantAccessContractConverter.toBff(result);
        ClientApiContractConverter.sameIdentity(tenantId, mapped.tenantId());
        ClientApiContractConverter.sameIdentity(memberId, mapped.memberId());
        return mapped;
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @Override
    public EvaluatedPageResponseBody<RoleGrant> roles(
        OperatorContext context,
        String tenantId,
        String memberId,
        Integer page,
        Integer size) {
        var result = management.roles(context.accountId(), context.sessionId(), tenantId, memberId, page, size);
        return TenantAccessContractConverter.evaluated(result, page, size, value -> { var mapped = TenantAccessContractConverter.toBff(value); TenantAccessContractConverter.roleTenant(tenantId, mapped.roleScope(), mapped.roleTenantId()); return mapped; }, RoleGrant::grantId);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @Override
    public EvaluatedPageResponseBody<MemberPermission> permissions(
        OperatorContext context,
        String tenantId,
        String memberId,
        Integer page,
        Integer size) {
        var result = management.permissions(context.accountId(), context.sessionId(), tenantId, memberId, page, size);
        return TenantAccessContractConverter.evaluated(result, page, size, TenantAccessContractConverter::toBff, MemberPermission::permissionId);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @Override
    public EvaluatedPageResponseBody<PermissionSource> sources(
        OperatorContext context,
        String tenantId,
        String memberId,
        String permissionId,
        Integer page,
        Integer size) {
        var result = management.sources(context.accountId(), context.sessionId(), tenantId, memberId, permissionId, page, size);
        return TenantAccessContractConverter.evaluated(result, page, size, value -> { var mapped = TenantAccessContractConverter.toBff(value); TenantAccessContractConverter.roleTenant(tenantId, mapped.roleScope(), mapped.roleTenantId()); return mapped; }, PermissionSource::grantId);
    }
}
