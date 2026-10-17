package com.paytech.platform.support.api.authentication.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Client 邮箱验证响应。 */
public final class VerificationResponseBody {
    /** 邮箱验证已成功。 */
    private final boolean verified;

    /** 后续动作代码；当前为 LOGIN，不自动登录。 */
    private final String nextAction;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public VerificationResponseBody(@JsonProperty("verified") boolean verified, @JsonProperty("nextAction") String nextAction) {
        this.verified = verified;
        this.nextAction = nextAction;
    }

    /** 返回邮箱验证已成功。 */
    public boolean isVerified() { return verified; }

    /** 返回后续动作代码；当前为 LOGIN，不自动登录。 */
    public String getNextAction() { return nextAction; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "VerificationResponseBody[REDACTED]"; }
}
