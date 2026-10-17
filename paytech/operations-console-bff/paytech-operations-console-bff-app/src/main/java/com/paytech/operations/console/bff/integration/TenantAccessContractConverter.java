package com.paytech.operations.console.bff.integration;

import java.util.List;
import java.util.Set;

import com.paytech.foundation.common.error.*;
import com.paytech.foundation.web.paging.PagingResponseBody;
import com.paytech.operations.console.bff.service.model.TenantAccessManagementModels.*;

/** Owner 响应的严格白名单投影，避免泄漏额外字段或误显示其他租户对象。 */
public final class TenantAccessContractConverter {
    /** 转换器不创建实例。 */
    private TenantAccessContractConverter() {}

    /** 复用现有分页窗口验证，只增加计算时刻。 */
    public static <S,T> EvaluatedPageResponseBody<T> evaluated(com.paytech.platform.support.api.tenantaccessmanagement.response.EvaluatedPageResponseBody<S> value,
        Integer page, Integer size, java.util.function.Function<S,T> convert, java.util.function.Function<T,String> identity) {
        require(value != null && value.getEvaluatedAt() != null && value.getItems() != null && value.getTotal() >= 0 && value.getTotal() <= 9007199254740991L);
        var mapped = ClientApiContractConverter.page(new PagingResponseBody<>(value.getItems(), value.getPage(), value.getSize(), value.getTotal()), page, size, convert, identity);
        return new EvaluatedPageResponseBody<>(mapped.items(), mapped.page(), mapped.size(), mapped.total(), value.getEvaluatedAt());
    }

    /** 平台角色不绑定租户，租户角色必须属于当前路径目标。 */
    public static void roleTenant(String tenantId, String scope, String roleTenantId) {
        require("PLATFORM".equals(scope) ? roleTenantId == null : "TENANT".equals(scope) && tenantId.equals(roleTenantId));
    }

    /** 仅复制该响应合同中的安全列，并校验必填值和闭合代码。 */
    public static TenantResponseBody toBff(com.paytech.platform.support.api.tenantaccessmanagement.response.TenantResponseBody value) {
        require(value != null);
        require(id(value.getTenantId())
            && oneOf(value.getTenantType(), "CLIENT", "PARTNER")
            && id(value.getOwnerRefId())
            && text(value.getTenantName(), 255)
            && oneOf(value.getOwnerStatus(), "PENDING", "ACTIVE", "SUSPENDED", "TERMINATED")
            && oneOf(value.getSecurityStatus(), "ACTIVE", "SUSPENDED")
            && value.getVersion() > 0
            && value.getCreatedAt() != null
            && value.getUpdatedAt() != null
            && value.isTenantAccessAllowed() == ("ACTIVE".equals(value.getOwnerStatus()) && "ACTIVE".equals(value.getSecurityStatus())));
        var restrictionReasons = reasons(value.getRestrictionReasons());
        require(value.isTenantAccessAllowed() == restrictionReasons.isEmpty());
        return new TenantResponseBody(value.getTenantId(), value.getTenantType(), value.getOwnerRefId(), value.getTenantName(), value.getOwnerStatus(), value.getSecurityStatus(), value.getVersion(), value.getCreatedAt(), value.getUpdatedAt(), value.isTenantAccessAllowed(), restrictionReasons);
    }

    /** 仅复制该响应合同中的安全列，并校验必填值和闭合代码。 */
    public static MemberResponseBody toBff(com.paytech.platform.support.api.tenantaccessmanagement.response.MemberResponseBody value) {
        require(value != null);
        require(id(value.getTenantId())
            && id(value.getMemberId())
            && id(value.getAccountId())
            && (value.getEmailMasked() == null || text(value.getEmailMasked(), 255))
            && oneOf(value.getMemberType(), "OWNER", "ADMIN", "MEMBER")
            && oneOf(value.getMemberStatus(), "ACTIVE", "SUSPENDED", "REVOKED")
            && oneOf(value.getAccountStatus(), "PENDING_VERIFICATION", "ACTIVE", "DISABLED", "CLOSED")
            && value.getCreatedAt() != null
            && value.getUpdatedAt() != null);
        return new MemberResponseBody(value.getTenantId(), value.getMemberId(), value.getAccountId(), value.getEmailMasked(), value.getMemberType(), value.getMemberStatus(), value.getAccountStatus(), value.getCreatedAt(), value.getUpdatedAt());
    }

    /** 仅复制该响应合同中的安全列，并校验必填值和闭合代码。 */
    public static RoleGrant toBff(com.paytech.platform.support.api.tenantaccessmanagement.response.RoleGrant value) {
        require(value != null);
        require(id(value.getGrantId())
            && id(value.getRoleId())
            && text(value.getRoleCode(), 64)
            && text(value.getRoleName(), 255)
            && oneOf(value.getRoleScope(), "PLATFORM", "TENANT")
            && (value.getRoleTenantId() == null || id(value.getRoleTenantId()))
            && oneOf(value.getRoleStatus(), "ACTIVE", "INACTIVE")
            && oneOf(value.getGrantStatus(), "ACTIVE", "REVOKED", "EXPIRED")
            && value.getEffectiveFrom() != null
            && (value.getEffectiveTo() == null || value.getEffectiveTo().isAfter(value.getEffectiveFrom()))
            && ("PLATFORM".equals(value.getRoleScope()) ? value.getRoleTenantId() == null : id(value.getRoleTenantId())));
        var reasons = reasons(value.getReasons());
        require(value.isEffective() == reasons.isEmpty());
        return new RoleGrant(value.getGrantId(), value.getRoleId(), value.getRoleCode(), value.getRoleName(), value.getRoleScope(), value.getRoleTenantId(), value.getRoleStatus(), value.getGrantStatus(), value.getEffectiveFrom(), value.getEffectiveTo(), value.isEffective(), reasons);
    }

    /** 仅复制该响应合同中的安全列，并校验必填值和闭合代码。 */
    public static MemberPermission toBff(com.paytech.platform.support.api.tenantaccessmanagement.response.MemberPermission value) {
        require(value != null);
        require(id(value.getPermissionId())
            && text(value.getPermissionCode(), 128)
            && text(value.getResourceType(), 64)
            && text(value.getActionCode(), 64)
            && oneOf(value.getPermissionStatus(), "ACTIVE", "INACTIVE")
            && value.getSourceCount() > 0 && value.getSourceCount() <= 9007199254740991L
            && value.getEffectiveSourceCount() >= 0 && value.getEffectiveSourceCount() <= value.getSourceCount()
            && value.isEffective() == (value.getEffectiveSourceCount() > 0));
        var reasons = reasons(value.getReasons());
        require(value.isEffective() == reasons.isEmpty());
        return new MemberPermission(value.getPermissionId(), value.getPermissionCode(), value.getResourceType(), value.getActionCode(), value.getPermissionStatus(), value.getSourceCount(), value.getEffectiveSourceCount(), value.isEffective(), reasons);
    }

    /** 仅复制该响应合同中的安全列，并校验必填值和闭合代码。 */
    public static PermissionSource toBff(com.paytech.platform.support.api.tenantaccessmanagement.response.PermissionSource value) {
        require(value != null);
        require(id(value.getGrantId())
            && id(value.getRoleId())
            && text(value.getRoleCode(), 64)
            && text(value.getRoleName(), 255)
            && oneOf(value.getRoleScope(), "PLATFORM", "TENANT")
            && (value.getRoleTenantId() == null || id(value.getRoleTenantId()))
            && oneOf(value.getRoleStatus(), "ACTIVE", "INACTIVE")
            && oneOf(value.getGrantStatus(), "ACTIVE", "REVOKED", "EXPIRED")
            && value.getEffectiveFrom() != null
            && id(value.getRolePermissionId())
            && oneOf(value.getRolePermissionStatus(), "ACTIVE", "REVOKED")
            && (value.getEffectiveTo() == null || value.getEffectiveTo().isAfter(value.getEffectiveFrom()))
            && ("PLATFORM".equals(value.getRoleScope()) ? value.getRoleTenantId() == null : id(value.getRoleTenantId())));
        var reasons = reasons(value.getReasons());
        require(value.isEffective() == reasons.isEmpty());
        return new PermissionSource(value.getGrantId(), value.getRoleId(), value.getRoleCode(), value.getRoleName(), value.getRoleScope(), value.getRoleTenantId(), value.getRoleStatus(), value.getGrantStatus(), value.getEffectiveFrom(), value.getEffectiveTo(), value.getRolePermissionId(), value.getRolePermissionStatus(), value.isEffective(), reasons);
    }

    /** 仅复制该响应合同中的安全列，并校验必填值和闭合代码。 */
    public static SecurityStatusResponseBody toBff(com.paytech.platform.support.api.tenantaccessmanagement.response.SecurityStatusResponseBody value) {
        require(value != null);
        require(id(value.getTenantId())
            && oneOf(value.getSecurityStatus(), "ACTIVE", "SUSPENDED")
            && value.getVersion() > 0);
        return new SecurityStatusResponseBody(value.getTenantId(), value.getSecurityStatus(), value.getVersion());
    }

    /** 拒绝未知、重复或乱序的原因代码，说明值不含内部对象。 */
    private static List<Reason> reasons(List<com.paytech.platform.support.api.tenantaccessmanagement.response.Reason> values) {
        List<String> order = List.of("TENANT_OWNER_INACTIVE", "TENANT_SECURITY_SUSPENDED", "ACCOUNT_INACTIVE", "MEMBER_INACTIVE",
            "ROLE_INACTIVE", "GRANT_REVOKED", "GRANT_NOT_STARTED", "GRANT_EXPIRED", "ROLE_PERMISSION_REVOKED", "PERMISSION_INACTIVE");
        require(values != null && values.size() <= order.size());
        int previous = -1;
        for (var value : values) {
            require(value != null && value.getCode() != null && text(value.getMessage(), 255));
            int index = order.indexOf(value.getCode());
            require(index > previous);
            previous = index;
        }
        return values.stream().map(v -> new Reason(v.getCode(), v.getMessage())).toList();
    }
    /** 闭合协议代码不接收空值或新造状态。 */
    private static boolean oneOf(String value, String... allowed) { return value != null && Set.of(allowed).contains(value); }
    /** 对响应文本保留字段长度边界。 */
    private static boolean text(String value, int max) { return value != null && value.length() <= max; }
    /** 所有外部身份保持正十进制字符串及 signed BIGINT 范围。 */
    private static boolean id(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) { return false; }
        try { return Long.parseLong(value) > 0; } catch (NumberFormatException invalid) { return false; }
    }
    /** 无法信任的 Owner 响应统一返回现有 502 Problem。 */
    private static void require(boolean valid) { if (!valid) { throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE); } }
}
