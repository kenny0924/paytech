package com.paytech.platform.support.accesscontrol.service.processor.request;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 认证内部请求；敏感字段仅随本次调用存在。 */
@Getter
@AllArgsConstructor
public final class ClientLoginRequest {
    /** 邮箱。 */
    private final String email;
    /** 密码。 */
    private final String password;
    /** 锁前解析的账号编号；入口为 null。 */
    private final Long accountId;

    /** 不允许诊断输出打印邮箱或凭据。 */
    @Override public String toString() { return "ClientLoginRequest[REDACTED]"; }
}
