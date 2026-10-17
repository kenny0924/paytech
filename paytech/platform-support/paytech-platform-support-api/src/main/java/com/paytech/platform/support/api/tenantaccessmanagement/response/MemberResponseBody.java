package com.paytech.platform.support.api.tenantaccessmanagement.response;

import java.time.Instant;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 成员只读详情及列表项。 */
public class MemberResponseBody {
    /** 所属租户标识。 */
    private final String tenantId;

    /** 成员标识。 */
    private final String memberId;

    /** 全局账号标识。 */
    private final String accountId;

    /** 脱敏邮箱，可空。 */
    private final String emailMasked;

    /** 成员类型。 */
    private final String memberType;

    /** 成员状态。 */
    private final String memberStatus;

    /** 账号状态。 */
    private final String accountStatus;

    /** 成员 UTC 创建时间。 */
    private final Instant createdAt;

    /** 成员 UTC 更新时间。 */
    private final Instant updatedAt;
    /** 按明确字段合同构造对象。 */
    @JsonCreator
    public MemberResponseBody(@JsonProperty(value = "tenantId") String tenantId,
        @JsonProperty(value = "memberId") String memberId,
        @JsonProperty(value = "accountId") String accountId,
        @JsonProperty(value = "emailMasked") String emailMasked,
        @JsonProperty(value = "memberType") String memberType,
        @JsonProperty(value = "memberStatus") String memberStatus,
        @JsonProperty(value = "accountStatus") String accountStatus,
        @JsonProperty(value = "createdAt") Instant createdAt,
        @JsonProperty(value = "updatedAt") Instant updatedAt) {
        this.tenantId = tenantId;
        this.memberId = memberId;
        this.accountId = accountId;
        this.emailMasked = emailMasked;
        this.memberType = memberType;
        this.memberStatus = memberStatus;
        this.accountStatus = accountStatus;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    /** 所属租户标识。 */
    public String getTenantId() { return tenantId; }

    /** 成员标识。 */
    public String getMemberId() { return memberId; }

    /** 全局账号标识。 */
    public String getAccountId() { return accountId; }

    /** 脱敏邮箱，可空。 */
    public String getEmailMasked() { return emailMasked; }

    /** 成员类型。 */
    public String getMemberType() { return memberType; }

    /** 成员状态。 */
    public String getMemberStatus() { return memberStatus; }

    /** 账号状态。 */
    public String getAccountStatus() { return accountStatus; }

    /** 成员 UTC 创建时间。 */
    public Instant getCreatedAt() { return createdAt; }

    /** 成员 UTC 更新时间。 */
    public Instant getUpdatedAt() { return updatedAt; }

    /** 按字段比较，保持原传输对象的值语义。 */
    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof MemberResponseBody value)) return false;
        return Objects.equals(tenantId, value.tenantId)
            && Objects.equals(memberId, value.memberId)
            && Objects.equals(accountId, value.accountId)
            && Objects.equals(emailMasked, value.emailMasked)
            && Objects.equals(memberType, value.memberType)
            && Objects.equals(memberStatus, value.memberStatus)
            && Objects.equals(accountStatus, value.accountStatus)
            && Objects.equals(createdAt, value.createdAt)
            && Objects.equals(updatedAt, value.updatedAt);
    }

    @Override
    public int hashCode() { return Objects.hash(tenantId, memberId, accountId, emailMasked, memberType, memberStatus, accountStatus, createdAt, updatedAt); }
}
