package com.paytech.platform.support.api.authentication.response;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** Client 邮件请求受理响应。 */
public final class AcceptedResponseBody {
    /** 请求已受理；不保证邮件已送达。 */
    private final boolean accepted;

    /** 重发冷却时间；单位秒，当前为 60。 */
    private final int resendAfterSeconds;

    /** 绑定协议字段，不输出任何凭据。 */
    @JsonCreator
    public AcceptedResponseBody(
        @JsonProperty("accepted") boolean accepted,
        @JsonProperty("resendAfterSeconds") int resendAfterSeconds) {
        this.accepted = accepted;
        this.resendAfterSeconds = resendAfterSeconds;
    }

    /** 返回请求已受理；不保证邮件已送达。 */
    public boolean isAccepted() { return accepted; }

    /** 返回重发冷却时间；单位秒，当前为 60。 */
    public int getResendAfterSeconds() { return resendAfterSeconds; }

    /** 避免诊断输出泄露认证材料。 */
    @Override
    public String toString() { return "AcceptedResponseBody[REDACTED]"; }
}
