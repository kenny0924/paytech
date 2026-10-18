package com.paytech.platform.support.api.authentication.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Client 私网认证签发响应。 */
public final class AuthenticationResponseBody {
    /** 账号上下文。 */
    private final AccountContextResponseBody context;

    /** 访问令牌；禁止日志。 */
    private final String accessToken;

    /** 访问令牌到期时刻；UTC 六位小数。 */
    private final String expiresAt;

    /** 刷新秘密；仅供 BFF 写入 Cookie。 */
    private final String refreshToken;

    /** 刷新令牌绝对到期时刻；UTC 六位小数。 */
    private final String refreshExpiresAt;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public AuthenticationResponseBody(
        @JsonProperty("context") AccountContextResponseBody context,
        @JsonProperty("accessToken") String accessToken,
        @JsonProperty("expiresAt") String expiresAt,
        @JsonProperty("refreshToken") String refreshToken,
        @JsonProperty("refreshExpiresAt") String refreshExpiresAt) {
        this.context = context;
        this.accessToken = accessToken;
        this.expiresAt = expiresAt;
        this.refreshToken = refreshToken;
        this.refreshExpiresAt = refreshExpiresAt;
    }

    /** 返回账号上下文。 */
    public AccountContextResponseBody getContext() { return context; }

    /** 返回访问令牌；禁止日志。 */
    public String getAccessToken() { return accessToken; }

    /** 返回访问令牌到期时刻；UTC 六位小数。 */
    public String getExpiresAt() { return expiresAt; }

    /** 返回刷新秘密；仅供 BFF 写入 Cookie。 */
    public String getRefreshToken() { return refreshToken; }

    /** 返回刷新令牌绝对到期时刻；UTC 六位小数。 */
    public String getRefreshExpiresAt() { return refreshExpiresAt; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "AuthenticationResponseBody[REDACTED]"; }
}
