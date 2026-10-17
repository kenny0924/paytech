package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Operations 认证请求。 */
public final class ChangePasswordRequestBody {
    /** 当前密码，禁止日志。 */
    @NotBlank @Size(max = 512)
    private final String currentPassword;

    /** 新密码，遵循现行密码策略。 */
    @NotBlank @Size(max = 512)
    private final String newPassword;

    /** 绑定完整认证合同。 */
    @JsonCreator
    public ChangePasswordRequestBody(@JsonProperty("currentPassword") String currentPassword,
        @JsonProperty("newPassword") String newPassword) {
        this.currentPassword = currentPassword;
        this.newPassword = newPassword;
    }

    /** 当前密码，禁止日志。 */
    public String getCurrentPassword() { return currentPassword; }

    /** 新密码，遵循现行密码策略。 */
    public String getNewPassword() { return newPassword; }

    /** 不向日志输出认证合同内容。 */
    @Override
    public String toString() { return "ChangePasswordRequestBody[REDACTED]"; }
}
