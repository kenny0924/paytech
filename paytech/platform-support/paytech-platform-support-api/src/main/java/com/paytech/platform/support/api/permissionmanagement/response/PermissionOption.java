package com.paytech.platform.support.api.permissionmanagement.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 全局 ACTIVE 权限选项，不包含持有人信息。 */
public final class PermissionOption {
    /** 权限主键，使用十进制字符串。 */
    private final String permissionId;

    /**
     * 不可变权限编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 128 字符。</li>
     */
    private final String permissionCode;

    /**
     * 不可变资源类型编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 64 字符。</li>
     */
    private final String resourceType;

    /**
     * 不可变动作编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 64 字符。</li>
     */
    private final String actionCode;

    /** 风险等级：LOW、MEDIUM、HIGH 或 CRITICAL。 */
    private final String riskLevel;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public PermissionOption(@JsonProperty(value = "permissionId") String permissionId,
        @JsonProperty(value = "permissionCode") String permissionCode,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "actionCode") String actionCode,
        @JsonProperty(value = "riskLevel") String riskLevel) {
        this.permissionId = permissionId;
        this.permissionCode = permissionCode;
        this.resourceType = resourceType;
        this.actionCode = actionCode;
        this.riskLevel = riskLevel;
    }

    /** 权限主键，使用十进制字符串。 */
    public String getPermissionId() { return permissionId; }

    /**
     * 不可变权限编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 128 字符。</li>
     */
    public String getPermissionCode() { return permissionCode; }

    /**
     * 不可变资源类型编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 64 字符。</li>
     */
    public String getResourceType() { return resourceType; }

    /**
     * 不可变动作编码。
     * <li>以大写字母开头，仅含大写字母、数字和下划线，最长 64 字符。</li>
     */
    public String getActionCode() { return actionCode; }

    /** 风险等级：LOW、MEDIUM、HIGH 或 CRITICAL。 */
    public String getRiskLevel() { return riskLevel; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PermissionOption value)) return false;
        return Objects.equals(permissionId, value.permissionId)
            && Objects.equals(permissionCode, value.permissionCode)
            && Objects.equals(resourceType, value.resourceType)
            && Objects.equals(actionCode, value.actionCode)
            && Objects.equals(riskLevel, value.riskLevel);
    }

    @Override
    public int hashCode() { return Objects.hash(permissionId, permissionCode, resourceType, actionCode, riskLevel); }
}
