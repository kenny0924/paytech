package com.paytech.platform.support.api.authentication.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 认证响应。 */
public final class OperationsContextResponseBody {
    /** 账号 ID 字符串。 */
    private final String accountId;

    /** 会话 ID 字符串。 */
    private final String sessionId;

    /** 账号类型 Code。 */
    private final String accountType;

    /** 脱敏邮箱；Bootstrap 为空。 */
    private final String emailMasked;

    /** 账号状态 Code。 */
    private final String accountStatus;

    /** 固定 INTERNAL Tenant ID；Bootstrap 为空。 */
    private final String tenantId;

    /** 成员 ID；Bootstrap 上下文为空。 */
    private final String memberId;

    /** 是否必须先修改初始密码。 */
    private final boolean passwordChangeRequired;

    /** 是否必须完成首次管理员设置。 */
    private final boolean bootstrapAdministratorRequired;

    /** 当前集成模式标识。 */
    private final String integrationMode;

    /** 绑定完整认证合同。 */
    @JsonCreator
    public OperationsContextResponseBody(@JsonProperty("accountId") String accountId,
        @JsonProperty("sessionId") String sessionId,
        @JsonProperty("accountType") String accountType,
        @JsonProperty("emailMasked") String emailMasked,
        @JsonProperty("accountStatus") String accountStatus,
        @JsonProperty("tenantId") String tenantId,
        @JsonProperty("memberId") String memberId,
        @JsonProperty("passwordChangeRequired") boolean passwordChangeRequired,
        @JsonProperty("bootstrapAdministratorRequired") boolean bootstrapAdministratorRequired,
        @JsonProperty("integrationMode") String integrationMode) {
        this.accountId = accountId;
        this.sessionId = sessionId;
        this.accountType = accountType;
        this.emailMasked = emailMasked;
        this.accountStatus = accountStatus;
        this.tenantId = tenantId;
        this.memberId = memberId;
        this.passwordChangeRequired = passwordChangeRequired;
        this.bootstrapAdministratorRequired = bootstrapAdministratorRequired;
        this.integrationMode = integrationMode;
    }

    /** 账号 ID 字符串。 */
    public String getAccountId() { return accountId; }

    /** 会话 ID 字符串。 */
    public String getSessionId() { return sessionId; }

    /** 账号类型 Code。 */
    public String getAccountType() { return accountType; }

    /** 脱敏邮箱；Bootstrap 为空。 */
    public String getEmailMasked() { return emailMasked; }

    /** 账号状态 Code。 */
    public String getAccountStatus() { return accountStatus; }

    /** 固定 INTERNAL Tenant ID；Bootstrap 为空。 */
    public String getTenantId() { return tenantId; }

    /** 成员 ID；Bootstrap 上下文为空。 */
    public String getMemberId() { return memberId; }

    /** 是否必须先修改初始密码。 */
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }

    /** 是否必须完成首次管理员设置。 */
    public boolean isBootstrapAdministratorRequired() { return bootstrapAdministratorRequired; }

    /** 当前集成模式标识。 */
    public String getIntegrationMode() { return integrationMode; }

    /** 不向日志输出认证合同内容。 */
    @Override
    public String toString() { return "OperationsContextResponseBody[REDACTED]"; }
}
