package com.paytech.platform.support.api.tenantaccessmanagement.response;

import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 成员权限汇总。 */
public class MemberPermission {
    /** 权限标识。 */
    private final String permissionId;

    /** 权限代码。 */
    private final String permissionCode;

    /** 资源类型。 */
    private final String resourceType;

    /** 动作代码。 */
    private final String actionCode;

    /** 权限状态。 */
    private final String permissionStatus;

    /** 来源总数。 */
    private final long sourceCount;

    /** 有效来源数。 */
    private final long effectiveSourceCount;

    /** 当前授权是否有效。 */
    private final boolean effective;

    /** 有序失效原因。 */
    private final List<Reason> reasons;
    /** 按明确字段合同构造对象。 */
    @JsonCreator
    public MemberPermission(@JsonProperty(value = "permissionId") String permissionId,
        @JsonProperty(value = "permissionCode") String permissionCode,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "actionCode") String actionCode,
        @JsonProperty(value = "permissionStatus") String permissionStatus,
        @JsonProperty(value = "sourceCount", required = true) long sourceCount,
        @JsonProperty(value = "effectiveSourceCount", required = true) long effectiveSourceCount,
        @JsonProperty(value = "effective", required = true) boolean effective,
        @JsonProperty(value = "reasons") List<Reason> reasons) {
        this.permissionId = permissionId;
        this.permissionCode = permissionCode;
        this.resourceType = resourceType;
        this.actionCode = actionCode;
        this.permissionStatus = permissionStatus;
        this.sourceCount = sourceCount;
        this.effectiveSourceCount = effectiveSourceCount;
        this.effective = effective;
        this.reasons = reasons;
    }

    /** 权限标识。 */
    public String getPermissionId() { return permissionId; }

    /** 权限代码。 */
    public String getPermissionCode() { return permissionCode; }

    /** 资源类型。 */
    public String getResourceType() { return resourceType; }

    /** 动作代码。 */
    public String getActionCode() { return actionCode; }

    /** 权限状态。 */
    public String getPermissionStatus() { return permissionStatus; }

    /** 来源总数。 */
    public long getSourceCount() { return sourceCount; }

    /** 有效来源数。 */
    public long getEffectiveSourceCount() { return effectiveSourceCount; }

    /** 当前授权是否有效。 */
    public boolean isEffective() { return effective; }

    /** 有序失效原因。 */
    public List<Reason> getReasons() { return reasons; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MemberPermission value)) return false;
        return Objects.equals(permissionId, value.permissionId)
            && Objects.equals(permissionCode, value.permissionCode)
            && Objects.equals(resourceType, value.resourceType)
            && Objects.equals(actionCode, value.actionCode)
            && Objects.equals(permissionStatus, value.permissionStatus)
            && Objects.equals(sourceCount, value.sourceCount)
            && Objects.equals(effectiveSourceCount, value.effectiveSourceCount)
            && Objects.equals(effective, value.effective)
            && Objects.equals(reasons, value.reasons);
    }

    @Override
    public int hashCode() { return Objects.hash(permissionId, permissionCode, resourceType, actionCode, permissionStatus, sourceCount, effectiveSourceCount, effective, reasons); }
}
