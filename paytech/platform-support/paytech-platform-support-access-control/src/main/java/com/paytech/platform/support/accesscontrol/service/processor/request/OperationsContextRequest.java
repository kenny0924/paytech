package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 认证用例内部请求；不承载 HTTP 幂等键。 */
@Getter
@AllArgsConstructor
public class OperationsContextRequest {
    /** 可信账号标识。 */
    private final long accountId;

    /** 可信会话标识。 */
    private final long sessionId;

    /** 认证请求不得进入普通日志。 */
    @Override
    public String toString() { return "OperationsContextRequest[REDACTED]"; }
}
