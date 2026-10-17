package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import java.time.Instant;
import java.util.List;

import com.paytech.platform.support.accesscontrol.domain.enums.RolePermissionStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantGrantStatus;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleScope;
import com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 成员角色授予、角色权限和权限目录的完整来源。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantPermissionSourceView {
    /** 角色授予标识。 */
    private Long grantId;

    /** 角色标识。 */
    private Long roleId;

    /** 角色代码。 */
    private String roleCode;

    /** 角色名称。 */
    private String roleName;

    /** 角色范围。 */
    private TenantRoleScope roleScope;

    /** 租户角色所属 ID，平台角色为空。 */
    private Long roleTenantId;

    /** 角色状态。 */
    private TenantRoleStatus roleStatus;

    /** 授予状态。 */
    private TenantGrantStatus grantStatus;

    /** 包含的生效时刻。 */
    private Instant effectiveFrom;

    /** 不包含的结束时刻，可空。 */
    private Instant effectiveTo;

    /** 角色权限关联标识。 */
    private Long rolePermissionId;

    /** 角色权限关联状态。 */
    private RolePermissionStatus rolePermissionStatus;

    /** 角色停用。 */
    private boolean roleInactive;

    /** 授予撤销。 */
    private boolean grantRevoked;

    /** 授予未开始。 */
    private boolean grantNotStarted;

    /** 授予过期。 */
    private boolean grantExpired;

    /** 角色权限关联撤销。 */
    private boolean rolePermissionRevoked;

    /** 权限停用。 */
    private boolean permissionInactive;

    /** 当前授权是否有效。 */
    private boolean effective;

    /** 按固定顺序排列的失效原因。 */
    private List<TenantAccessReason> reasons;

    /** 解释本次快照已计算的状态标记，保留全部合法失效来源。 */
    public void evaluate(List<TenantAccessReason> base) {
        reasons = TenantAccessReason.combine(base, roleInactive, grantRevoked, grantNotStarted, grantExpired,
            rolePermissionRevoked, permissionInactive);
        effective = reasons.isEmpty();
    }
}
