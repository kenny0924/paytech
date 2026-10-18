package com.paytech.platform.support.api.authentication.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 认证响应。 */
public final class AccountCreatedResponseBody {
    /** 账号 ID 字符串。 */
    private final String accountId;

    /** 成员 ID；Bootstrap 上下文为空。 */
    private final String memberId;

    /** 账号初始业务版本。 */
    private final int version;

    /** 绑定完整认证合同。 */
    @JsonCreator
    public AccountCreatedResponseBody(@JsonProperty("accountId") String accountId,
        @JsonProperty("memberId") String memberId,
        @JsonProperty("version") int version) {
        this.accountId = accountId;
        this.memberId = memberId;
        this.version = version;
    }

    /** 账号 ID 字符串。 */
    public String getAccountId() { return accountId; }

    /** 成员 ID；Bootstrap 上下文为空。 */
    public String getMemberId() { return memberId; }

    /** 账号初始业务版本。 */
    public int getVersion() { return version; }

    /** 不向日志输出认证合同内容。 */
    @Override
    public String toString() { return "AccountCreatedResponseBody[REDACTED]"; }
}
