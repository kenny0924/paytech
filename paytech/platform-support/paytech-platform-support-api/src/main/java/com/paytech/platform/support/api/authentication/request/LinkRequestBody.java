package com.paytech.platform.support.api.authentication.request;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Client 邮件验证请求。 */
public final class LinkRequestBody {
    /** 邮件挑战的公开查找编号。 */
    @NotBlank @Size(max = 36)
    private final String challengeNo;

    /** 单次验证秘密；禁止记录日志。 */
    @NotBlank @Size(max = 43)
    private final String secret;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public LinkRequestBody(@JsonProperty("challengeNo") String challengeNo, @JsonProperty("secret") String secret) {
        this.challengeNo = challengeNo;
        this.secret = secret;
    }

    /** 返回邮件挑战的公开查找编号。 */
    public String getChallengeNo() { return challengeNo; }

    /** 返回单次验证秘密；禁止记录日志。 */
    public String getSecret() { return secret; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "LinkRequestBody[REDACTED]"; }
}
