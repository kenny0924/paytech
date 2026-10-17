package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.RefreshResolution;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 认证用例内部请求；不承载 HTTP 幂等键。 */
@Getter
@AllArgsConstructor
public class OperationsRefreshRequest {
    /** 刷新秘密，禁止日志。 */
    private final String refreshToken;

    /** 锁前解析结果，仅本次调用使用。 */
    private final RefreshResolution resolution;

    /** 认证请求不得进入普通日志。 */
    @Override
    public String toString() { return "OperationsRefreshRequest[REDACTED]"; }
}
