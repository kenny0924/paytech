package com.paytech.platform.support.api.authentication.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 当前账号作为成员的一个 CLIENT 工作区。 */
public final class ClientWorkspaceResponseBody {
    /** 成员编号；进入工作区时提交。 */
    private final String memberId;

    /** 租户编号。 */
    private final String tenantId;

    /** 来源 Client 编号（租户 owner_ref_id）。 */
    private final String clientId;

    /** 租户来源状态：ACTIVE、SUSPENDED 或 TERMINATED。 */
    private final String ownerStatus;

    /** 租户安全状态：ACTIVE 或 SUSPENDED。 */
    private final String securityStatus;

    /** 成员状态：ACTIVE、SUSPENDED 或 REVOKED。 */
    private final String memberStatus;

    /** 来源、安全与成员状态是否都允许进入。 */
    private final boolean accessible;

    /** 不可进入时的第一个公开原因码；可进入时为空。 */
    private final String reasonCode;

    /** 绑定协议字段。 */
    @JsonCreator
    public ClientWorkspaceResponseBody(@JsonProperty("memberId") String memberId,
        @JsonProperty("tenantId") String tenantId, @JsonProperty("clientId") String clientId,
        @JsonProperty("ownerStatus") String ownerStatus, @JsonProperty("securityStatus") String securityStatus,
        @JsonProperty("memberStatus") String memberStatus, @JsonProperty("accessible") boolean accessible,
        @JsonProperty("reasonCode") String reasonCode) {
        this.memberId = memberId;
        this.tenantId = tenantId;
        this.clientId = clientId;
        this.ownerStatus = ownerStatus;
        this.securityStatus = securityStatus;
        this.memberStatus = memberStatus;
        this.accessible = accessible;
        this.reasonCode = reasonCode;
    }

    /** 返回成员编号。 */
    public String getMemberId() { return memberId; }

    /** 返回租户编号。 */
    public String getTenantId() { return tenantId; }

    /** 返回来源 Client 编号。 */
    public String getClientId() { return clientId; }

    /** 返回租户来源状态。 */
    public String getOwnerStatus() { return ownerStatus; }

    /** 返回租户安全状态。 */
    public String getSecurityStatus() { return securityStatus; }

    /** 返回成员状态。 */
    public String getMemberStatus() { return memberStatus; }

    /** 返回是否可以进入。 */
    public boolean isAccessible() { return accessible; }

    /** 返回不可进入时的公开原因码。 */
    public String getReasonCode() { return reasonCode; }
}
