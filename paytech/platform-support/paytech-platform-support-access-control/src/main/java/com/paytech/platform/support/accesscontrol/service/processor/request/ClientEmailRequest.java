package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.platform.support.accesscontrol.domain.authentication.ClientRegistrationModels.EmailIdentity;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 认证内部请求；敏感字段仅随本次调用存在。 */
@Getter
@AllArgsConstructor
public final class ClientEmailRequest {
    /** 邮箱原值。 */
    private final String email;
    /** 注册密码；重发时为空。 */
    private final String password;
    /** 锁前解析的邮箱身份；入口为 null。 */
    private final EmailIdentity identity;

    /** 不允许诊断输出打印邮箱或凭据。 */
    @Override public String toString() { return "ClientEmailRequest[REDACTED]"; }
}
