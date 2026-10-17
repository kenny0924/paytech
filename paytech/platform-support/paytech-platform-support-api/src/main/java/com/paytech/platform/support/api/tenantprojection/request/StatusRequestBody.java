package com.paytech.platform.support.api.tenantprojection.request;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

/** 投影同一来源的生命周期事实，申请账号身份保持不变。 */
public final class StatusRequestBody {
    /** 租户类型。<li>本期只接受 CLIENT。</li> */
    @NotNull @Pattern(regexp = "CLIENT")
    private final String tenantType;

    /** CCT Client 标识。<li>正十进制字符串，不超过 signed BIGINT 上限。</li> */
    @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}")
    private final String ownerRefId;

    /** 创建申请时冻结的 CUSTOMER Account 标识。 */
    @NotNull @Pattern(regexp = "[1-9][0-9]{0,18}")
    private final String requestedByAccountId;

    /** 本次 Client 生命周期来源序号。<li>只接受连续下一版；旧版本按已提交事实处理。</li> */
    @Min(1)
    private final int sourceVersion;

    /** 原始不可变来源事实的 SHA-256 小写十六进制摘要。 */
    @NotNull @Pattern(regexp = "[a-f0-9]{64}")
    private final String payloadHash;

    /** 本次来源业务状态。<li>不覆盖 Platform 安全状态。</li> */
    @NotNull @Pattern(regexp = "PENDING|ACTIVE|SUSPENDED|TERMINATED")
    private final String ownerStatus;

    /** 按明确属性绑定，缺失来源序号作为 JSON 协议错误处理。 */
    @JsonCreator
    public StatusRequestBody(@JsonProperty(value = "tenantType") String tenantType,
        @JsonProperty(value = "ownerRefId") String ownerRefId,
        @JsonProperty(value = "requestedByAccountId") String requestedByAccountId,
        @JsonProperty(value = "sourceVersion", required = true) int sourceVersion,
        @JsonProperty(value = "payloadHash") String payloadHash,
        @JsonProperty(value = "ownerStatus") String ownerStatus) {
        this.tenantType = tenantType;
        this.ownerRefId = ownerRefId;
        this.requestedByAccountId = requestedByAccountId;
        this.sourceVersion = sourceVersion;
        this.payloadHash = payloadHash;
        this.ownerStatus = ownerStatus;
    }

    /** 租户类型。<li>本期只接受 CLIENT。</li> */
    public String getTenantType() { return tenantType; }

    /** CCT Client 标识。<li>正十进制字符串，不超过 signed BIGINT 上限。</li> */
    public String getOwnerRefId() { return ownerRefId; }

    /** 创建申请时冻结的 CUSTOMER Account 标识。 */
    public String getRequestedByAccountId() { return requestedByAccountId; }

    /** 本次 Client 生命周期来源序号。<li>只接受连续下一版；旧版本按已提交事实处理。</li> */
    public int getSourceVersion() { return sourceVersion; }

    /** 原始不可变来源事实的 SHA-256 小写十六进制摘要。 */
    public String getPayloadHash() { return payloadHash; }

    /** 本次来源业务状态。<li>不覆盖 Platform 安全状态。</li> */
    public String getOwnerStatus() { return ownerStatus; }

    /** 保持原传输合同按字段比较的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof StatusRequestBody value)) return false;
        return Objects.equals(tenantType, value.tenantType)
            && Objects.equals(ownerRefId, value.ownerRefId)
            && Objects.equals(requestedByAccountId, value.requestedByAccountId)
            && Objects.equals(sourceVersion, value.sourceVersion)
            && Objects.equals(payloadHash, value.payloadHash)
            && Objects.equals(ownerStatus, value.ownerStatus);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantType, ownerRefId, requestedByAccountId, sourceVersion, payloadHash, ownerStatus); }
}
