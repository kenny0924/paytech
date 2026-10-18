package com.paytech.platform.support.api.authentication.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Operations 认证响应。 */
public final class AuthenticationTokensResponseBody {
    /** Access JWT，仅用于认证。 */
    private final String accessToken;

    /** 令牌类型，固定 Bearer。 */
    private final String tokenType;

    /** Access 剩余有效期，单位秒。 */
    private final long expiresIn;

    /** 完整刷新令牌，禁止持久化明文或日志。 */
    private final String refreshToken;

    /** Refresh 剩余有效期，单位秒。 */
    private final long refreshExpiresIn;

    /** 是否必须先修改初始密码。 */
    private final boolean passwordChangeRequired;

    /** 是否必须完成首次管理员设置。 */
    private final boolean bootstrapAdministratorRequired;

    /** 绑定完整认证合同。 */
    @JsonCreator
    public AuthenticationTokensResponseBody(@JsonProperty("accessToken") String accessToken,
        @JsonProperty("tokenType") String tokenType,
        @JsonProperty("expiresIn") long expiresIn,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("refreshExpiresIn") long refreshExpiresIn,
        @JsonProperty("passwordChangeRequired") boolean passwordChangeRequired,
        @JsonProperty("bootstrapAdministratorRequired") boolean bootstrapAdministratorRequired) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.refreshToken = refreshToken;
        this.refreshExpiresIn = refreshExpiresIn;
        this.passwordChangeRequired = passwordChangeRequired;
        this.bootstrapAdministratorRequired = bootstrapAdministratorRequired;
    }

    /** Access JWT，仅用于认证。 */
    public String getAccessToken() { return accessToken; }

    /** 令牌类型，固定 Bearer。 */
    public String getTokenType() { return tokenType; }

    /** Access 剩余有效期，单位秒。 */
    public long getExpiresIn() { return expiresIn; }

    /** 完整刷新令牌，禁止持久化明文或日志。 */
    public String getRefreshToken() { return refreshToken; }

    /** Refresh 剩余有效期，单位秒。 */
    public long getRefreshExpiresIn() { return refreshExpiresIn; }

    /** 是否必须先修改初始密码。 */
    public boolean isPasswordChangeRequired() { return passwordChangeRequired; }

    /** 是否必须完成首次管理员设置。 */
    public boolean isBootstrapAdministratorRequired() { return bootstrapAdministratorRequired; }

    /** 不向日志输出认证合同内容。 */
    @Override
    public String toString() { return "AuthenticationTokensResponseBody[REDACTED]"; }
}
