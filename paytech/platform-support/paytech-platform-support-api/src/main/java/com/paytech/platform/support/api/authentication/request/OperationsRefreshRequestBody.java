package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Operations 认证请求。 */
public final class OperationsRefreshRequestBody {
    /** 完整刷新令牌，禁止持久化明文或日志。 */
    @NotBlank @Size(max = 512)
    private final String refreshToken;

    /** 绑定完整认证合同。 */
    @JsonCreator
    public OperationsRefreshRequestBody(@JsonProperty("refreshToken") String refreshToken) {
        this.refreshToken = refreshToken;
    }

    /** 完整刷新令牌，禁止持久化明文或日志。 */
    public String getRefreshToken() { return refreshToken; }

    /** 不向日志输出认证合同内容。 */
    @Override
    public String toString() { return "OperationsRefreshRequestBody[REDACTED]"; }
}
