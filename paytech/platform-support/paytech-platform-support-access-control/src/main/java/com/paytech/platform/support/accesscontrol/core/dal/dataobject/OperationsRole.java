package com.paytech.platform.support.accesscontrol.core.dal.dataobject;

import com.paytech.platform.support.accesscontrol.domain.enums.TenantRoleStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.experimental.Accessors;

/** Operations 本库事实；由 Repository 在写入成功后同步原对象。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Accessors(fluent = true)
public class OperationsRole {
    /** 角色主键。 */
    private long id;

    /** 稳定角色代码。 */
    private String code;

    /** 受控角色名称。 */
    private String name;

    /** 系统角色禁止通过 Operations 修改。 */
    private boolean systemRole;

    /** 角色聚合版本，同时保护授权集合。 */
    private int version;

    /** 由 tenant_scope='PLATFORM' 派生的系统范围标记。 */
    private boolean platformRole;

    /** 当前角色生命周期，与 t_role.status 一致。 */
    private TenantRoleStatus status;

    /** 有效角色查询与新建角色的兼容构造入口。 */
    public OperationsRole(long id, String code, String name, boolean systemRole, int version, boolean platformRole) {
        this(id, code, name, systemRole, version, platformRole, TenantRoleStatus.ACTIVE);
    }

    /** 避免认证材料进入普通日志。 */
    @Override
    public String toString() { return "OperationsRole[REDACTED]"; }
}
