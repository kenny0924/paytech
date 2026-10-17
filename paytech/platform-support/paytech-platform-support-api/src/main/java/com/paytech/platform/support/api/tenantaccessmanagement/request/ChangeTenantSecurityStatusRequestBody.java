package com.paytech.platform.support.api.tenantaccessmanagement.request;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** 仅修改目标租户安全状态。 */
public class ChangeTenantSecurityStatusRequestBody {
    /** ACTIVE 或 SUSPENDED。 */
    @NotBlank
    private final String securityStatus;

    /** 期望版本。 */
    @Positive
    private final int expectedVersion;

    /** 操作原因。 */
    @NotBlank @Size(max = 500)
    private final String reason;
    /** 按明确字段合同构造对象。 */
    @JsonCreator
    public ChangeTenantSecurityStatusRequestBody(@JsonProperty("securityStatus") String securityStatus,
        @JsonProperty(value = "expectedVersion", required = true) int expectedVersion,
        @JsonProperty("reason") String reason) {
        this.securityStatus = securityStatus;
        this.expectedVersion = expectedVersion;
        this.reason = reason;
    }

    /** ACTIVE 或 SUSPENDED。 */
    public String getSecurityStatus() { return securityStatus; }

    /** 期望版本。 */
    public int getExpectedVersion() { return expectedVersion; }

    /** 操作原因。 */
    public String getReason() { return reason; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ChangeTenantSecurityStatusRequestBody value)) return false;
        return Objects.equals(securityStatus, value.securityStatus)
            && Objects.equals(expectedVersion, value.expectedVersion)
            && Objects.equals(reason, value.reason);
    }

    @Override
    public int hashCode() { return Objects.hash(securityStatus, expectedVersion, reason); }
}
