package com.paytech.platform.support.api.authentication.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Client 账号上下文响应。 */
public final class AccountContextResponseBody {
    /** 账号编号。 */
    private final String accountId;

    /** 会话编号。 */
    private final String sessionId;

    /** 脱敏邮箱。 */
    private final String emailMasked;

    /** 邮箱是否已验证。 */
    private final boolean emailVerified;

    /** 上下文代码；当前为 ACCOUNT。 */
    private final String contextType;

    /** 租户编号；ACCOUNT 上下文为空。 */
    private final String tenantId;

    /** 成员编号；ACCOUNT 上下文为空。 */
    private final String memberId;

    /** 当前允许的动作代码；不可修改。 */
    private final java.util.List<String> allowedActions;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public AccountContextResponseBody(
        @JsonProperty("accountId") String accountId,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("emailMasked") String emailMasked,
        @JsonProperty("emailVerified") boolean emailVerified,
        @JsonProperty("contextType") String contextType,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("memberId") String memberId,
        @JsonProperty("allowedActions") java.util.List<String> allowedActions) {
        this.accountId = accountId;
        this.sessionId = sessionId;
        this.emailMasked = emailMasked;
        this.emailVerified = emailVerified;
        this.contextType = contextType;
        this.tenantId = tenantId;
        this.memberId = memberId;
        this.allowedActions = java.util.List.copyOf(allowedActions);
    }

    /** 返回账号编号。 */
    public String getAccountId() { return accountId; }

    /** 返回会话编号。 */
    public String getSessionId() { return sessionId; }

    /** 返回脱敏邮箱。 */
    public String getEmailMasked() { return emailMasked; }

    /** 返回邮箱是否已验证。 */
    public boolean isEmailVerified() { return emailVerified; }

    /** 返回上下文代码；当前为 ACCOUNT。 */
    public String getContextType() { return contextType; }

    /** 返回租户编号；ACCOUNT 上下文为空。 */
    public String getTenantId() { return tenantId; }

    /** 返回成员编号；ACCOUNT 上下文为空。 */
    public String getMemberId() { return memberId; }

    /** 返回当前允许的动作代码；不可修改。 */
    public java.util.List<String> getAllowedActions() { return allowedActions; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "AccountContextResponseBody[REDACTED]"; }
}
