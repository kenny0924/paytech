package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Client 邮箱密码注册请求。 */
public final class RegisterRequestBody {
    /** 登录邮箱。 */
    @NotBlank @Size(max = 320)
    private final String email;

    /** 注册密码；保留原始空格，由密码策略校验。 */
    @NotBlank @Size(max = 512)
    private final String password;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public RegisterRequestBody(@JsonProperty("email") String email, @JsonProperty("password") String password) {
        this.email = email;
        this.password = password;
    }

    /** 返回登录邮箱。 */
    public String getEmail() { return email; }

    /** 返回注册密码；保留原始空格，由密码策略校验。 */
    public String getPassword() { return password; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "RegisterRequestBody[REDACTED]"; }
}
