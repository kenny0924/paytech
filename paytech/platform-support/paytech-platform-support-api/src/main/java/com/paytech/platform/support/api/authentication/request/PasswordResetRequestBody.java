package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Client 密码重置请求。 */
public final class PasswordResetRequestBody {
    /** 密码恢复挑战编号。 */
    @NotBlank @Size(max = 36)
    private final String challengeNo;

    /** 单次恢复秘密；禁止记录日志。 */
    @NotBlank @Size(max = 43)
    private final String secret;

    /** 新密码；保留原始空格，由密码策略校验。 */
    @NotBlank @Size(max = 512)
    private final String newPassword;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public PasswordResetRequestBody(
        @JsonProperty("challengeNo") String challengeNo,
        @JsonProperty("secret") String secret,
        @JsonProperty("newPassword") String newPassword) {
        this.challengeNo = challengeNo;
        this.secret = secret;
        this.newPassword = newPassword;
    }

    /** 返回密码恢复挑战编号。 */
    public String getChallengeNo() { return challengeNo; }

    /** 返回单次恢复秘密；禁止记录日志。 */
    public String getSecret() { return secret; }

    /** 返回新密码；保留原始空格，由密码策略校验。 */
    public String getNewPassword() { return newPassword; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "PasswordResetRequestBody[REDACTED]"; }
}
