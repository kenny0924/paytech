package com.paytech.platform.support.api.permissionmanagement.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 权限目录摘要，不代表当前操作者持有或可转授。 */
public final class PermissionSummary {
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

    /** 权限状态：ACTIVE 或 INACTIVE。 */
    private final String status;

    /** 当前乐观锁版本。 */
    private final int version;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public PermissionSummary(@JsonProperty(value = "permissionId") String permissionId,
        @JsonProperty(value = "permissionCode") String permissionCode,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "actionCode") String actionCode,
        @JsonProperty(value = "riskLevel") String riskLevel,
        @JsonProperty(value = "status") String status,
        @JsonProperty(value = "version", required = true) int version) {
        this.permissionId = permissionId;
        this.permissionCode = permissionCode;
        this.resourceType = resourceType;
        this.actionCode = actionCode;
        this.riskLevel = riskLevel;
        this.status = status;
        this.version = version;
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

    /** 权限状态：ACTIVE 或 INACTIVE。 */
    public String getStatus() { return status; }

    /** 当前乐观锁版本。 */
    public int getVersion() { return version; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PermissionSummary value)) return false;
        return Objects.equals(permissionId, value.permissionId)
            && Objects.equals(permissionCode, value.permissionCode)
            && Objects.equals(resourceType, value.resourceType)
            && Objects.equals(actionCode, value.actionCode)
            && Objects.equals(riskLevel, value.riskLevel)
            && Objects.equals(status, value.status)
            && Objects.equals(version, value.version);
    }

    @Override
    public int hashCode() { return Objects.hash(permissionId, permissionCode, resourceType, actionCode, riskLevel, status, version); }
}
