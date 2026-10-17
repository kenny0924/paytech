package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** 首次创建正式 OWNER 角色时授予的单个权限。 */
@Getter
@AllArgsConstructor
@Setter
@NoArgsConstructor
@Accessors(fluent = true)
public class TenantProjectionRolePermission {
    /** 角色权限映射主键。 */
    private long rolePermissionId;

    /** 正式 OWNER 角色。 */
    private long roleId;

    /** 授予的权限。 */
    private long permissionId;
}
