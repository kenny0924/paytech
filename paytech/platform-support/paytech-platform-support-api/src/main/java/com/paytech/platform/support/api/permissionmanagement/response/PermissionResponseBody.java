package com.paytech.platform.support.api.permissionmanagement.response;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.paytech.platform.support.api.configurationmanagement.response.ManagementProtection;

/** 权限详情及当前维护边界、引用计数。 */
public final class PermissionResponseBody {
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

    /** UTC 创建时间。 */
    private final Instant createTime;

    /** UTC 更新时间。 */
    private final Instant updateTime;

    /** 当前允许的维护操作。 */
    private final ManagementProtection managementProtection;

    /** 当前配置引用数量。 */
    private final ReferenceCounts referenceCounts;

    /** 按明确的字段合同创建对象。 */
    @JsonCreator
    public PermissionResponseBody(@JsonProperty(value = "permissionId") String permissionId,
        @JsonProperty(value = "permissionCode") String permissionCode,
        @JsonProperty(value = "resourceType") String resourceType,
        @JsonProperty(value = "actionCode") String actionCode,
        @JsonProperty(value = "riskLevel") String riskLevel,
        @JsonProperty(value = "status") String status,
        @JsonProperty(value = "version", required = true) int version,
        @JsonProperty(value = "createTime") Instant createTime,
        @JsonProperty(value = "updateTime") Instant updateTime,
        @JsonProperty(value = "managementProtection") ManagementProtection managementProtection,
        @JsonProperty(value = "referenceCounts") ReferenceCounts referenceCounts) {
        this.permissionId = permissionId;
        this.permissionCode = permissionCode;
        this.resourceType = resourceType;
        this.actionCode = actionCode;
        this.riskLevel = riskLevel;
        this.status = status;
        this.version = version;
        this.createTime = createTime;
        this.updateTime = updateTime;
        this.managementProtection = managementProtection;
        this.referenceCounts = referenceCounts;
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

    /** UTC 创建时间。 */
    public Instant getCreateTime() { return createTime; }

    /** UTC 更新时间。 */
    public Instant getUpdateTime() { return updateTime; }

    /** 当前允许的维护操作。 */
    public ManagementProtection getManagementProtection() { return managementProtection; }

    /** 当前配置引用数量。 */
    public ReferenceCounts getReferenceCounts() { return referenceCounts; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof PermissionResponseBody value)) return false;
        return Objects.equals(permissionId, value.permissionId)
            && Objects.equals(permissionCode, value.permissionCode)
            && Objects.equals(resourceType, value.resourceType)
            && Objects.equals(actionCode, value.actionCode)
            && Objects.equals(riskLevel, value.riskLevel)
            && Objects.equals(status, value.status)
            && Objects.equals(version, value.version)
            && Objects.equals(createTime, value.createTime)
            && Objects.equals(updateTime, value.updateTime)
            && Objects.equals(managementProtection, value.managementProtection)
            && Objects.equals(referenceCounts, value.referenceCounts);
    }

    @Override
    public int hashCode() { return Objects.hash(permissionId, permissionCode, resourceType, actionCode, riskLevel, status, version, createTime, updateTime, managementProtection, referenceCounts); }
}
