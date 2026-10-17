package com.paytech.platform.support.api.tenantprojection.response;

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前已提交位置及稳定工作区身份，不签发 Tenant Token。 */
public final class ProjectionResponseBody {
    /** 租户类型，本期为 CLIENT。 */
    private final String tenantType;

    /** CCT Client 标识，正十进制字符串。 */
    private final String ownerRefId;

    /** 原申请账号标识，正十进制字符串。 */
    private final String requestedByAccountId;

    /** 稳定工作区租户标识，正十进制字符串。 */
    private final String tenantId;

    /** 初始 OWNER 成员标识，正十进制字符串。 */
    private final String memberId;

    /** 当前 CCT 来源业务状态。 */
    private final String ownerStatus;

    /** 当前已提交的正整数来源序号，不是资料版本。 */
    private final int sourceVersion;

    /** 当前位置对应的不可变来源摘要。 */
    private final String payloadHash;

    /** 按明确属性绑定，缺失来源序号作为 JSON 协议错误处理。 */
    @JsonCreator
    public ProjectionResponseBody(@JsonProperty(value = "tenantType") String tenantType,
        @JsonProperty(value = "ownerRefId") String ownerRefId,
        @JsonProperty(value = "requestedByAccountId") String requestedByAccountId,
        @JsonProperty(value = "tenantId") String tenantId,
        @JsonProperty(value = "memberId") String memberId,
        @JsonProperty(value = "ownerStatus") String ownerStatus,
        @JsonProperty(value = "sourceVersion", required = true) int sourceVersion,
        @JsonProperty(value = "payloadHash") String payloadHash) {
        this.tenantType = tenantType;
        this.ownerRefId = ownerRefId;
        this.requestedByAccountId = requestedByAccountId;
        this.tenantId = tenantId;
        this.memberId = memberId;
        this.ownerStatus = ownerStatus;
        this.sourceVersion = sourceVersion;
        this.payloadHash = payloadHash;
    }

    /** 租户类型，本期为 CLIENT。 */
    public String getTenantType() { return tenantType; }

    /** CCT Client 标识，正十进制字符串。 */
    public String getOwnerRefId() { return ownerRefId; }

    /** 原申请账号标识，正十进制字符串。 */
    public String getRequestedByAccountId() { return requestedByAccountId; }

    /** 稳定工作区租户标识，正十进制字符串。 */
    public String getTenantId() { return tenantId; }

    /** 初始 OWNER 成员标识，正十进制字符串。 */
    public String getMemberId() { return memberId; }

    /** 当前 CCT 来源业务状态。 */
    public String getOwnerStatus() { return ownerStatus; }

    /** 当前已提交的正整数来源序号，不是资料版本。 */
    public int getSourceVersion() { return sourceVersion; }

    /** 当前位置对应的不可变来源摘要。 */
    public String getPayloadHash() { return payloadHash; }

    /** 保持原传输合同按字段比较的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof ProjectionResponseBody value)) return false;
        return Objects.equals(tenantType, value.tenantType)
            && Objects.equals(ownerRefId, value.ownerRefId)
            && Objects.equals(requestedByAccountId, value.requestedByAccountId)
            && Objects.equals(tenantId, value.tenantId)
            && Objects.equals(memberId, value.memberId)
            && Objects.equals(ownerStatus, value.ownerStatus)
            && Objects.equals(sourceVersion, value.sourceVersion)
            && Objects.equals(payloadHash, value.payloadHash);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantType, ownerRefId, requestedByAccountId, tenantId, memberId, ownerStatus, sourceVersion, payloadHash); }
}
