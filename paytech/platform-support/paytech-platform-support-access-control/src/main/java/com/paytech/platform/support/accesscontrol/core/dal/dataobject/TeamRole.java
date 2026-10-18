package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleStatus;

/** 工作区的租户系统角色（t_role，只读）。 */
@lombok.Getter
@lombok.Setter
@lombok.NoArgsConstructor
@lombok.AllArgsConstructor
@lombok.experimental.Accessors(fluent = true)
public class TeamRole {
    /** 角色主键。 */
    private long roleId;

    /** 所属租户。 */
    private long tenantId;

    /** 角色代码。 */
    private String roleCode;

    /** 角色状态。 */
    private TenantRoleStatus status;
}
