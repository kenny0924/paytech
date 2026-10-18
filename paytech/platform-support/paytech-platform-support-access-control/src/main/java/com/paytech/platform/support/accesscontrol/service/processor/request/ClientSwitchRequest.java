package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 切换上下文内部请求；memberId 为空表示返回账号上下文，敏感字段仅随本次调用存在。 */
@Getter
@AllArgsConstructor
public final class ClientSwitchRequest {
    /** 可信账号编号。 */
    private final long accountId;
    /** 可信会话编号。 */
    private final long sessionId;
    /** 刷新令牌。 */
    private final String refreshToken;
    /** 目标成员编号；返回账号上下文时为空。 */
    private final Long memberId;

    /** 不允许诊断输出打印凭据。 */
    @Override public String toString() { return "ClientSwitchRequest[REDACTED]"; }
}
