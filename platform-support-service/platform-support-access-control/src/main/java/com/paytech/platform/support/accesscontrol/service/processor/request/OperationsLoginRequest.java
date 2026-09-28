package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.platform.support.accesscontrol.domain.authentication.OperationsAuthenticationModels.LoginResolution;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 认证用例内部请求；不承载 HTTP 幂等键。 */
@Getter
@AllArgsConstructor
public class OperationsLoginRequest {
    /** 登录标识。 */
    private final String loginIdentifier;

    /** 登录密码，禁止日志。 */
    private final String password;

    /** 锁前解析结果，仅本次调用使用。 */
    private final LoginResolution resolution;

    /** 认证请求不得进入普通日志。 */
    @Override
    public String toString() { return "OperationsLoginRequest[REDACTED]"; }
}
