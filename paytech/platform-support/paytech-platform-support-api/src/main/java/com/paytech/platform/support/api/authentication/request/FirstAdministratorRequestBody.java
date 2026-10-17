package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Operations 认证请求。 */
public final class FirstAdministratorRequestBody {
    /** 首个管理员邮箱。 */
    @NotBlank @Size(max = 320)
    private final String email;

    /** 一次性初始密码，首次登录必须修改。 */
    @NotBlank @Size(max = 512)
    private final String initialPassword;

    /** 绑定完整认证合同。 */
    @JsonCreator
    public FirstAdministratorRequestBody(@JsonProperty("email") String email,
        @JsonProperty("initialPassword") String initialPassword) {
        this.email = email;
        this.initialPassword = initialPassword;
    }

    /** 首个管理员邮箱。 */
    public String getEmail() { return email; }

    /** 一次性初始密码，首次登录必须修改。 */
    public String getInitialPassword() { return initialPassword; }

    /** 不向日志输出认证合同内容。 */
    @Override
    public String toString() { return "FirstAdministratorRequestBody[REDACTED]"; }
}
