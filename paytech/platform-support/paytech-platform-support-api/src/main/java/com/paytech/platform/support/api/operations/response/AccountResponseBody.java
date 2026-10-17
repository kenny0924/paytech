package com.paytech.platform.support.api.operations.response;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 响应字段，保持既有 JSON 合同。 */
public final class AccountResponseBody {
    /**
     * Operations 人员账号 ID。
     * <li>十进制字符串。</li>
     */
    private final String accountId;

    /**
     * 脱敏邮箱。
     * <li>不包含原始邮箱或 Vault Token。</li>
     */
    private final String emailMasked;

    /** 账号状态 Code：ACTIVE、DISABLED 或 CLOSED。 */
    private final String status;

    /** 是否必须先修改初始密码。 */
    private final boolean passwordChangeRequired;

    /**
     * 临时登录锁定截止时刻。
     * <li>UTC。</li>
     * <li>未锁定时为空。</li>
     */
    private final Instant lockedUntil;

    /** 当前账号或角色聚合版本。 */
    private final int version;

    /** 当前 INTERNAL Tenant 成员 ID。 */
    private final String memberId;

    /** 当前成员授权聚合版本。 */
    private final int memberVersion;

    /**
     * 人员当前有效角色。
     * <li>无有效角色时为空列表。</li>
     */
    private final List<RoleSummary> roles;

    /** 绑定协议字段，保留 JSON 名称与校验约束。 */
    @JsonCreator
    public AccountResponseBody(@JsonProperty(value = "accountId") String accountId,
        @JsonProperty(value = "emailMasked") String emailMasked,
        @JsonProperty(value = "status") String status,
        @JsonProperty(value = "passwordChangeRequired", required = true) boolean passwordChangeRequired,
        @JsonProperty(value = "lockedUntil") Instant lockedUntil,
        @JsonProperty(value = "version", required = true) int version,
        @JsonProperty(value = "memberId") String memberId,
        @JsonProperty(value = "memberVersion", required = true) int memberVersion,
        @JsonProperty(value = "roles") List<RoleSummary> roles) {
        this.accountId = accountId;
        this.emailMasked = emailMasked;
        this.status = status;
        this.passwordChangeRequired = passwordChangeRequired;
        this.lockedUntil = lockedUntil;
        this.version = version;
        this.memberId = memberId;
        this.memberVersion = memberVersion;
        this.roles = roles == null ? null : java.util.Collections.unmodifiableList(new java.util.ArrayList<>(roles));
    }

    /** Operations 人员账号 ID，十进制字符串。 */
    public String getAccountId() {
        return accountId;
    }

    /** 脱敏邮箱，不包含原始邮箱或 Vault Token。 */
    public String getEmailMasked() {
        return emailMasked;
    }

    /** 账号状态 Code：ACTIVE、DISABLED 或 CLOSED。 */
    public String getStatus() {
        return status;
    }

    /** 是否必须先修改初始密码。 */
    public boolean getPasswordChangeRequired() {
        return passwordChangeRequired;
    }

    /** 临时登录锁定截止时刻，UTC；未锁定时为空。 */
    public Instant getLockedUntil() {
        return lockedUntil;
    }

    /** 当前账号或角色聚合版本。 */
    public int getVersion() {
        return version;
    }

    /** 当前 INTERNAL Tenant 成员 ID。 */
    public String getMemberId() {
        return memberId;
    }

    /** 当前成员授权聚合版本。 */
    public int getMemberVersion() {
        return memberVersion;
    }

    /** 人员当前有效角色，无有效角色时为空列表。 */
    public List<RoleSummary> getRoles() {
        return roles;
    }

    /** 按协议字段比较值。 */
    @Override
    public boolean equals(Object value) {
        return value instanceof AccountResponseBody other && Objects.equals(accountId, other.accountId)
            && Objects.equals(emailMasked, other.emailMasked)
            && Objects.equals(status, other.status)
            && Objects.equals(passwordChangeRequired, other.passwordChangeRequired)
            && Objects.equals(lockedUntil, other.lockedUntil)
            && Objects.equals(version, other.version)
            && Objects.equals(memberId, other.memberId)
            && Objects.equals(memberVersion, other.memberVersion)
            && Objects.equals(roles, other.roles);
    }

    /** 与字段值相等语义保持一致。 */
    @Override
    public int hashCode() {
        return Objects.hash(accountId, emailMasked, status, passwordChangeRequired, lockedUntil, version, memberId,
            memberVersion, roles);
    }
}
