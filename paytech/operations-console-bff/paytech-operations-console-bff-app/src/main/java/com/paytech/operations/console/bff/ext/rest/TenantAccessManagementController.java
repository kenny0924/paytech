package com.paytech.operations.console.bff.ext.rest;

import org.springframework.web.bind.annotation.*;

import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.integration.PlatformTenantAccessManagementClient;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.TenantAccessManagementModels.*;

import jakarta.validation.Valid;

/** 沿用实时 PLT-I02 授权与可信 Operations 身份的租户访问转发。 */
@RestController
@org.springframework.validation.annotation.Validated
@lombok.AllArgsConstructor
@RequestMapping("/tenant-access-admin/v1/tenants")
public class TenantAccessManagementController {
    private final PlatformTenantAccessManagementClient client;

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @GetMapping("")
    public PagingResponseBody<TenantResponseBody> tenants(
        OperatorContext context,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "tenantType", required = false) String tenantType,
        @RequestParam(value = "tenantId", required = false) String tenantId,
        @RequestParam(value = "ownerRefId", required = false) String ownerRefId,
        @RequestParam(value = "tenantName", required = false) String tenantName,
        @RequestParam(value = "ownerStatus", required = false) String ownerStatus,
        @RequestParam(value = "securityStatus", required = false) String securityStatus) {
        return client.tenants(context, page, size, tenantType, tenantId, ownerRefId, tenantName, ownerStatus, securityStatus);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @GetMapping("/{tenantId}")
    public TenantResponseBody tenant(
        OperatorContext context,
        @PathVariable("tenantId") String tenantId) {
        return client.tenant(context, tenantId);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @PostMapping("/{tenantId}/security-status")
    public SecurityStatusResponseBody changeSecurityStatus(
        OperatorContext context,
        @PathVariable("tenantId") String tenantId,
        @Valid @RequestBody ChangeTenantSecurityStatusRequestBody request) {
        return client.changeSecurityStatus(context, tenantId, request);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @GetMapping("/{tenantId}/members")
    public PagingResponseBody<MemberResponseBody> members(
        OperatorContext context,
        @PathVariable("tenantId") String tenantId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size,
        @RequestParam(value = "accountId", required = false) String accountId,
        @RequestParam(value = "emailMasked", required = false) String emailMasked,
        @RequestParam(value = "memberType", required = false) String memberType,
        @RequestParam(value = "memberStatus", required = false) String memberStatus) {
        return client.members(context, tenantId, page, size, accountId, emailMasked, memberType, memberStatus);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @GetMapping("/{tenantId}/members/{memberId}")
    public MemberResponseBody member(
        OperatorContext context,
        @PathVariable("tenantId") String tenantId,
        @PathVariable("memberId") String memberId) {
        return client.member(context, tenantId, memberId);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @GetMapping("/{tenantId}/members/{memberId}/roles")
    public EvaluatedPageResponseBody<RoleGrant> roles(
        OperatorContext context,
        @PathVariable("tenantId") String tenantId,
        @PathVariable("memberId") String memberId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.roles(context, tenantId, memberId, page, size);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @GetMapping("/{tenantId}/members/{memberId}/permissions")
    public EvaluatedPageResponseBody<MemberPermission> permissions(
        OperatorContext context,
        @PathVariable("tenantId") String tenantId,
        @PathVariable("memberId") String memberId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.permissions(context, tenantId, memberId, page, size);
    }

    /** 转发精确路径与业务参数，结果仅包含批准的显示字段。 */
    @GetMapping("/{tenantId}/members/{memberId}/permissions/{permissionId}/sources")
    public EvaluatedPageResponseBody<PermissionSource> sources(
        OperatorContext context,
        @PathVariable("tenantId") String tenantId,
        @PathVariable("memberId") String memberId,
        @PathVariable("permissionId") String permissionId,
        @RequestParam(value = "page", required = false) Integer page,
        @RequestParam(value = "size", required = false) Integer size) {
        return client.sources(context, tenantId, memberId, permissionId, page, size);
    }
}
