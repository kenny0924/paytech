package com.paytech.platform.support.api.tenantaccessmanagement.response;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 租户只读详情及列表项。 */
public class TenantResponseBody {
    /** 租户标识。 */
    private final String tenantId;

    /** CLIENT 或 PARTNER。 */
    private final String tenantType;

    /** 来源主档标识。 */
    private final String ownerRefId;

    /** 租户名称。 */
    private final String tenantName;

    /** 来源业务状态。 */
    private final String ownerStatus;

    /** 平台安全状态。 */
    private final String securityStatus;

    /** 当前并发版本。 */
    private final int version;

    /** UTC 创建时间。 */
    private final Instant createdAt;

    /** UTC 更新时间。 */
    private final Instant updatedAt;

    /** 双状态是否均允许访问。 */
    private final boolean tenantAccessAllowed;

    /** 租户限制原因。 */
    private final List<Reason> restrictionReasons;
    /** 按明确字段合同构造对象。 */
    @JsonCreator
    public TenantResponseBody(@JsonProperty(value = "tenantId") String tenantId,
        @JsonProperty(value = "tenantType") String tenantType,
        @JsonProperty(value = "ownerRefId") String ownerRefId,
        @JsonProperty(value = "tenantName") String tenantName,
        @JsonProperty(value = "ownerStatus") String ownerStatus,
        @JsonProperty(value = "securityStatus") String securityStatus,
        @JsonProperty(value = "version", required = true) int version,
        @JsonProperty(value = "createdAt") Instant createdAt,
        @JsonProperty(value = "updatedAt") Instant updatedAt,
        @JsonProperty(value = "tenantAccessAllowed", required = true) boolean tenantAccessAllowed,
        @JsonProperty(value = "restrictionReasons") List<Reason> restrictionReasons) {
        this.tenantId = tenantId;
        this.tenantType = tenantType;
        this.ownerRefId = ownerRefId;
        this.tenantName = tenantName;
        this.ownerStatus = ownerStatus;
        this.securityStatus = securityStatus;
        this.version = version;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
        this.tenantAccessAllowed = tenantAccessAllowed;
        this.restrictionReasons = restrictionReasons;
    }

    /** 租户标识。 */
    public String getTenantId() { return tenantId; }

    /** CLIENT 或 PARTNER。 */
    public String getTenantType() { return tenantType; }

    /** 来源主档标识。 */
    public String getOwnerRefId() { return ownerRefId; }

    /** 租户名称。 */
    public String getTenantName() { return tenantName; }

    /** 来源业务状态。 */
    public String getOwnerStatus() { return ownerStatus; }

    /** 平台安全状态。 */
    public String getSecurityStatus() { return securityStatus; }

    /** 当前并发版本。 */
    public int getVersion() { return version; }

    /** UTC 创建时间。 */
    public Instant getCreatedAt() { return createdAt; }

    /** UTC 更新时间。 */
    public Instant getUpdatedAt() { return updatedAt; }

    /** 双状态是否均允许访问。 */
    public boolean isTenantAccessAllowed() { return tenantAccessAllowed; }

    /** 租户限制原因。 */
    public List<Reason> getRestrictionReasons() { return restrictionReasons; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof TenantResponseBody value)) return false;
        return Objects.equals(tenantId, value.tenantId)
            && Objects.equals(tenantType, value.tenantType)
            && Objects.equals(ownerRefId, value.ownerRefId)
            && Objects.equals(tenantName, value.tenantName)
            && Objects.equals(ownerStatus, value.ownerStatus)
            && Objects.equals(securityStatus, value.securityStatus)
            && Objects.equals(version, value.version)
            && Objects.equals(createdAt, value.createdAt)
            && Objects.equals(updatedAt, value.updatedAt)
            && Objects.equals(tenantAccessAllowed, value.tenantAccessAllowed)
            && Objects.equals(restrictionReasons, value.restrictionReasons);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, tenantType, ownerRefId, tenantName, ownerStatus, securityStatus, version, createdAt, updatedAt, tenantAccessAllowed, restrictionReasons); }
}
