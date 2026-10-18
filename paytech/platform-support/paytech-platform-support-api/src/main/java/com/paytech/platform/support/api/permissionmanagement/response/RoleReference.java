package com.paytech.platform.support.api.permissionmanagement.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 未撤销的角色授权引用，不展开人员。 */
public final class RoleReference implements PermissionReference {
    /** 角色主键，使用十进制字符串。 */
    private final String roleId;

    /** 稳定角色编码。 */
    private final String roleCode;

    /** 角色名称。 */
    private final String roleName;

    /** 当前角色状态。 */
    private final String roleStatus;

    /** 角色与权限的关联状态。 */
    private final String grantStatus;

    /** 角色所属授权范围。 */
    private final String scopeKey;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public RoleReference(@JsonProperty(value = "roleId") String roleId,
        @JsonProperty(value = "roleCode") String roleCode,
        @JsonProperty(value = "roleName") String roleName,
        @JsonProperty(value = "roleStatus") String roleStatus,
        @JsonProperty(value = "grantStatus") String grantStatus,
        @JsonProperty(value = "scopeKey") String scopeKey) {
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.roleStatus = roleStatus;
        this.grantStatus = grantStatus;
        this.scopeKey = scopeKey;
    }

    /** 角色主键，使用十进制字符串。 */
    public String getRoleId() { return roleId; }

    /** 稳定角色编码。 */
    public String getRoleCode() { return roleCode; }

    /** 角色名称。 */
    public String getRoleName() { return roleName; }

    /** 当前角色状态。 */
    public String getRoleStatus() { return roleStatus; }

    /** 角色与权限的关联状态。 */
    public String getGrantStatus() { return grantStatus; }

    /** 角色所属授权范围。 */
    public String getScopeKey() { return scopeKey; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RoleReference value)) return false;
        return Objects.equals(roleId, value.roleId)
            && Objects.equals(roleCode, value.roleCode)
            && Objects.equals(roleName, value.roleName)
            && Objects.equals(roleStatus, value.roleStatus)
            && Objects.equals(grantStatus, value.grantStatus)
            && Objects.equals(scopeKey, value.scopeKey);
    }

    @Override
    public int hashCode() { return Objects.hash(roleId, roleCode, roleName, roleStatus, grantStatus, scopeKey); }
}
