package com.paytech.platform.support.accesscontrol.domain.tenantaccess;

import java.util.List;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.accesscontrol.domain.enums.PermissionStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 成员已有权限及其全部角色来源的汇总查询结果。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class TenantMemberPermissionView {
    /** 权限标识。 */
    private Long permissionId;

    /** 权限代码。 */
    private String permissionCode;

    /** 资源类型。 */
    private String resourceType;

    /** 动作代码。 */
    private String actionCode;

    /** 权限状态。 */
    private PermissionStatus permissionStatus;

    /** 来源总数。 */
    private long sourceCount;

    /** 有效来源数。 */
    private long effectiveSourceCount;

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

    /** 任一完整来源有效即可；租户或成员受限时全部来源失效。 */
    public void evaluate(List<TenantAccessReason> base) {
        long currentEffective = base.isEmpty() ? effectiveSourceCount : 0;
        if (sourceCount < 1 || sourceCount > 9007199254740991L || currentEffective < 0 || currentEffective > sourceCount)
            throw new ErrorException("租户成员授权记录不一致，请联系管理员检查");
        effectiveSourceCount = currentEffective;
        effective = currentEffective > 0;
        reasons = effective ? List.of() : TenantAccessReason.combine(base, roleInactive, grantRevoked,
            grantNotStarted, grantExpired, rolePermissionRevoked, permissionInactive);
    }
}
