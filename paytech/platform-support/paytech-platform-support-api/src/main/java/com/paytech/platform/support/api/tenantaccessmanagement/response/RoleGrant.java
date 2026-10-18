package com.paytech.platform.support.api.tenantaccessmanagement.response;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 角色授予记录。 */
public class RoleGrant {
    /** 角色授予标识。 */
    private final String grantId;

    /** 角色标识。 */
    private final String roleId;

    /** 角色代码。 */
    private final String roleCode;

    /** 角色名称。 */
    private final String roleName;

    /** 角色范围。 */
    private final String roleScope;

    /** 租户角色所属 ID，平台角色为空。 */
    private final String roleTenantId;

    /** 角色状态。 */
    private final String roleStatus;

    /** 授予状态。 */
    private final String grantStatus;

    /** 包含的生效时刻。 */
    private final Instant effectiveFrom;

    /** 不包含的结束时刻，可空。 */
    private final Instant effectiveTo;

    /** 当前授权是否有效。 */
    private final boolean effective;

    /** 有序失效原因。 */
    private final List<Reason> reasons;
    /** 按明确字段合同构造对象。 */
    @JsonCreator
    public RoleGrant(@JsonProperty(value = "grantId") String grantId,
        @JsonProperty(value = "roleId") String roleId,
        @JsonProperty(value = "roleCode") String roleCode,
        @JsonProperty(value = "roleName") String roleName,
        @JsonProperty(value = "roleScope") String roleScope,
        @JsonProperty(value = "roleTenantId") String roleTenantId,
        @JsonProperty(value = "roleStatus") String roleStatus,
        @JsonProperty(value = "grantStatus") String grantStatus,
        @JsonProperty(value = "effectiveFrom") Instant effectiveFrom,
        @JsonProperty(value = "effectiveTo") Instant effectiveTo,
        @JsonProperty(value = "effective", required = true) boolean effective,
        @JsonProperty(value = "reasons") List<Reason> reasons) {
        this.grantId = grantId;
        this.roleId = roleId;
        this.roleCode = roleCode;
        this.roleName = roleName;
        this.roleScope = roleScope;
        this.roleTenantId = roleTenantId;
        this.roleStatus = roleStatus;
        this.grantStatus = grantStatus;
        this.effectiveFrom = effectiveFrom;
        this.effectiveTo = effectiveTo;
        this.effective = effective;
        this.reasons = reasons;
    }

    /** 角色授予标识。 */
    public String getGrantId() { return grantId; }

    /** 角色标识。 */
    public String getRoleId() { return roleId; }

    /** 角色代码。 */
    public String getRoleCode() { return roleCode; }

    /** 角色名称。 */
    public String getRoleName() { return roleName; }

    /** 角色范围。 */
    public String getRoleScope() { return roleScope; }

    /** 租户角色所属 ID，平台角色为空。 */
    public String getRoleTenantId() { return roleTenantId; }

    /** 角色状态。 */
    public String getRoleStatus() { return roleStatus; }

    /** 授予状态。 */
    public String getGrantStatus() { return grantStatus; }

    /** 包含的生效时刻。 */
    public Instant getEffectiveFrom() { return effectiveFrom; }

    /** 不包含的结束时刻，可空。 */
    public Instant getEffectiveTo() { return effectiveTo; }

    /** 当前授权是否有效。 */
    public boolean isEffective() { return effective; }

    /** 有序失效原因。 */
    public List<Reason> getReasons() { return reasons; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof RoleGrant value)) return false;
        return Objects.equals(grantId, value.grantId)
            && Objects.equals(roleId, value.roleId)
            && Objects.equals(roleCode, value.roleCode)
            && Objects.equals(roleName, value.roleName)
            && Objects.equals(roleScope, value.roleScope)
            && Objects.equals(roleTenantId, value.roleTenantId)
            && Objects.equals(roleStatus, value.roleStatus)
            && Objects.equals(grantStatus, value.grantStatus)
            && Objects.equals(effectiveFrom, value.effectiveFrom)
            && Objects.equals(effectiveTo, value.effectiveTo)
            && Objects.equals(effective, value.effective)
            && Objects.equals(reasons, value.reasons);
    }

    @Override
    public int hashCode() { return Objects.hash(grantId, roleId, roleCode, roleName, roleScope, roleTenantId, roleStatus, grantStatus, effectiveFrom, effectiveTo, effective, reasons); }
}
