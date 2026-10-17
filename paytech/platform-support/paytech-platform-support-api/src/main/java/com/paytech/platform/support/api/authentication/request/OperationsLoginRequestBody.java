package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Operations 认证请求。 */
public final class OperationsLoginRequestBody {
    /** Email 登录标识；Bootstrap 固定为 admin。 */
    @NotBlank @Size(max = 320)
    private final String loginIdentifier;

    /** 登录密码，禁止日志。 */
    @NotBlank @Size(max = 512)
    private final String password;

    /** 绑定完整认证合同。 */
    @JsonCreator
    public OperationsLoginRequestBody(@JsonProperty("loginIdentifier") String loginIdentifier,
        @JsonProperty("password") String password) {
        this.loginIdentifier = loginIdentifier;
        this.password = password;
    }

    /** Email 登录标识；Bootstrap 固定为 admin。 */
    public String getLoginIdentifier() { return loginIdentifier; }

    /** 登录密码，禁止日志。 */
    public String getPassword() { return password; }

    /** 不向日志输出认证合同内容。 */
    @Override
    public String toString() { return "OperationsLoginRequestBody[REDACTED]"; }
}
