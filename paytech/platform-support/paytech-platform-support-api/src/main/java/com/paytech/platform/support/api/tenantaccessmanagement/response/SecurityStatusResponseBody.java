package com.paytech.platform.support.api.tenantaccessmanagement.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 稳定写入结果。 */
public class SecurityStatusResponseBody {
    /** 租户标识。 */
    private final String tenantId;

    /** 当次安全状态。 */
    private final String securityStatus;

    /** 当次结果版本。 */
    private final int version;
    /** 按明确字段合同构造对象。 */
    @JsonCreator
    public SecurityStatusResponseBody(@JsonProperty(value = "tenantId") String tenantId,
        @JsonProperty(value = "securityStatus") String securityStatus,
        @JsonProperty(value = "version", required = true) int version) {
        this.tenantId = tenantId;
        this.securityStatus = securityStatus;
        this.version = version;
    }

    /** 租户标识。 */
    public String getTenantId() { return tenantId; }

    /** 当次安全状态。 */
    public String getSecurityStatus() { return securityStatus; }

    /** 当次结果版本。 */
    public int getVersion() { return version; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof SecurityStatusResponseBody value)) return false;
        return Objects.equals(tenantId, value.tenantId)
            && Objects.equals(securityStatus, value.securityStatus)
            && Objects.equals(version, value.version);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, securityStatus, version); }
}
