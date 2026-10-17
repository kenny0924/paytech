package com.paytech.operations.console.bff.service.model;

import java.time.Instant;
import java.util.List;
import jakarta.validation.constraints.*;

/** Operations 租户访问与只读授权诊断合同。 */
public final class TenantAccessManagementModels {
    /** 类型容器不创建实例。 */
    private TenantAccessManagementModels() {}

    /** 租户只读详情及列表项。
     * @param tenantId 租户标识。
     * @param tenantType CLIENT 或 PARTNER。
     * @param ownerRefId 来源主档标识。
     * @param tenantName 租户名称。
     * @param ownerStatus 来源业务状态。
     * @param securityStatus 平台安全状态。
     * @param version 当前并发版本。
     * @param createdAt UTC 创建时间。
     * @param updatedAt UTC 更新时间。
     * @param tenantAccessAllowed 双状态是否均允许访问。
     * @param restrictionReasons 租户限制原因。
     */
    public record TenantResponseBody(
        String tenantId,
        String tenantType,
        String ownerRefId,
        String tenantName,
        String ownerStatus,
        String securityStatus,
        int version,
        Instant createdAt,
        Instant updatedAt,
        boolean tenantAccessAllowed,
        List<Reason> restrictionReasons) {}
    /** 成员只读详情及列表项。
     * @param tenantId 所属租户标识。
     * @param memberId 成员标识。
     * @param accountId 全局账号标识。
     * @param emailMasked 脱敏邮箱，可空。
     * @param memberType 成员类型。
     * @param memberStatus 成员状态。
     * @param accountStatus 账号状态。
     * @param createdAt 成员 UTC 创建时间。
     * @param updatedAt 成员 UTC 更新时间。
     */
    public record MemberResponseBody(
        String tenantId,
        String memberId,
        String accountId,
        String emailMasked,
        String memberType,
        String memberStatus,
        String accountStatus,
        Instant createdAt,
        Instant updatedAt) {}
    /** 角色授予记录。
     * @param grantId 角色授予标识。
     * @param roleId 角色标识。
     * @param roleCode 角色代码。
     * @param roleName 角色名称。
     * @param roleScope 角色范围。
     * @param roleTenantId 租户角色所属 ID，平台角色为空。
     * @param roleStatus 角色状态。
     * @param grantStatus 授予状态。
     * @param effectiveFrom 包含的生效时刻。
     * @param effectiveTo 不包含的结束时刻，可空。
     * @param effective 当前授权是否有效。
     * @param reasons 有序失效原因。
     */
    public record RoleGrant(
        String grantId,
        String roleId,
        String roleCode,
        String roleName,
        String roleScope,
        String roleTenantId,
        String roleStatus,
        String grantStatus,
        Instant effectiveFrom,
        Instant effectiveTo,
        boolean effective,
        List<Reason> reasons) {}
    /** 成员权限汇总。
     * @param permissionId 权限标识。
     * @param permissionCode 权限代码。
     * @param resourceType 资源类型。
     * @param actionCode 动作代码。
     * @param permissionStatus 权限状态。
     * @param sourceCount 来源总数。
     * @param effectiveSourceCount 有效来源数。
     * @param effective 当前授权是否有效。
     * @param reasons 有序失效原因。
     */
    public record MemberPermission(
        String permissionId,
        String permissionCode,
        String resourceType,
        String actionCode,
        String permissionStatus,
        long sourceCount,
        long effectiveSourceCount,
        boolean effective,
        List<Reason> reasons) {}
    /** 权限来源解释。
     * @param grantId 角色授予标识。
     * @param roleId 角色标识。
     * @param roleCode 角色代码。
     * @param roleName 角色名称。
     * @param roleScope 角色范围。
     * @param roleTenantId 租户角色所属 ID，平台角色为空。
     * @param roleStatus 角色状态。
     * @param grantStatus 授予状态。
     * @param effectiveFrom 包含的生效时刻。
     * @param effectiveTo 不包含的结束时刻，可空。
     * @param rolePermissionId 角色权限关联标识。
     * @param rolePermissionStatus 角色权限关联状态。
     * @param effective 当前授权是否有效。
     * @param reasons 有序失效原因。
     */
    public record PermissionSource(
        String grantId,
        String roleId,
        String roleCode,
        String roleName,
        String roleScope,
        String roleTenantId,
        String roleStatus,
        String grantStatus,
        Instant effectiveFrom,
        Instant effectiveTo,
        String rolePermissionId,
        String rolePermissionStatus,
        boolean effective,
        List<Reason> reasons) {}
    /** 安全的授权解释。
     * @param code 原因代码。
     * @param message 可读说明。
     */
    public record Reason(
        String code,
        String message) {}
    /** 仅修改目标租户安全状态。
     * @param securityStatus ACTIVE 或 SUSPENDED。
     * @param expectedVersion 期望版本。
     * @param reason 操作原因。
     */
    public record ChangeTenantSecurityStatusRequestBody(
        @NotBlank String securityStatus,
        @Positive int expectedVersion,
        @NotBlank @Size(max = 500) String reason) {}
    /** 稳定写入结果。
     * @param tenantId 租户标识。
     * @param securityStatus 当次安全状态。
     * @param version 当次结果版本。
     */
    public record SecurityStatusResponseBody(
        String tenantId,
        String securityStatus,
        int version) {}
    /** 公共分页协议加计算时刻的传输投影。
     * @param items 当前页内容。
     * @param page 页码。
     * @param size 分页大小。
     * @param total 真实总数。
     * @param evaluatedAt 同一查询的 UTC 计算时刻。
     */
    public record EvaluatedPageResponseBody<T>(
        List<T> items,
        int page,
        int size,
        long total,
        Instant evaluatedAt) {}
}
