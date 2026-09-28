package com.paytech.operations.console.bff.integration;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.TenantAccessManagementModels.*;

/** 沿用实时 PLT-I02 授权与可信 Operations 身份的租户访问转发。 */
public interface PlatformTenantAccessManagementClient {

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    PagingResponseBody<TenantResponseBody> tenants(
        OperatorContext context,
        Integer page,
        Integer size,
        String tenantType,
        String tenantId,
        String ownerRefId,
        String tenantName,
        String ownerStatus,
        String securityStatus);

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    TenantResponseBody tenant(
        OperatorContext context,
        String tenantId);

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    SecurityStatusResponseBody changeSecurityStatus(
        OperatorContext context,
        String tenantId,
        ChangeTenantSecurityStatusRequestBody request);

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    PagingResponseBody<MemberResponseBody> members(
        OperatorContext context,
        String tenantId,
        Integer page,
        Integer size,
        String accountId,
        String emailMasked,
        String memberType,
        String memberStatus);

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    MemberResponseBody member(
        OperatorContext context,
        String tenantId,
        String memberId);

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    EvaluatedPageResponseBody<RoleGrant> roles(
        OperatorContext context,
        String tenantId,
        String memberId,
        Integer page,
        Integer size);

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    EvaluatedPageResponseBody<MemberPermission> permissions(
        OperatorContext context,
        String tenantId,
        String memberId,
        Integer page,
        Integer size);

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    EvaluatedPageResponseBody<PermissionSource> sources(
        OperatorContext context,
        String tenantId,
        String memberId,
        String permissionId,
        Integer page,
        Integer size);
}
