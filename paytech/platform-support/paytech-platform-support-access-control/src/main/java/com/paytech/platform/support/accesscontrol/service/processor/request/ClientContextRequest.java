package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 认证内部请求；敏感字段仅随本次调用存在。 */
@Getter
@AllArgsConstructor
public final class ClientContextRequest {
    /** 可信账号编号。 */
    private final long accountId;
    /** 可信会话编号。 */
    private final long sessionId;

    /** 不允许诊断输出打印邮箱或凭据。 */
    @Override public String toString() { return "ClientContextRequest[REDACTED]"; }
}
