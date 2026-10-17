package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Client 邮件发送请求。 */
public final class EmailRequestBody {
    /** 登录邮箱。 */
    @NotBlank @Size(max = 320)
    private final String email;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public EmailRequestBody(@JsonProperty("email") String email) {
        this.email = email;
    }

    /** 返回登录邮箱。 */
    public String getEmail() { return email; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "EmailRequestBody[REDACTED]"; }
}
