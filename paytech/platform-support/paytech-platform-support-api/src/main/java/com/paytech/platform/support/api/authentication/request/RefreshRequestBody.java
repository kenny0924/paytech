package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Client 私网刷新令牌请求。 */
public final class RefreshRequestBody {
    /** BFF 从 HttpOnly Cookie 取出的刷新令牌；仅私网传递。 */
    @NotBlank @Size(max = 66)
    private final String refreshToken;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public RefreshRequestBody(@JsonProperty("refreshToken") String refreshToken) {
        this.refreshToken = refreshToken;
    }

    /** 返回BFF 从 HttpOnly Cookie 取出的刷新令牌；仅私网传递。 */
    public String getRefreshToken() { return refreshToken; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "RefreshRequestBody[REDACTED]"; }
}
