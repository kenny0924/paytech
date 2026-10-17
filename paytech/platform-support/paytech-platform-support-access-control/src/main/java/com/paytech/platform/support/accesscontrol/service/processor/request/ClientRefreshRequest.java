package com.paytech.platform.support.accesscontrol.service.processor.request;

import com.paytech.platform.support.accesscontrol.domain.authentication.ClientAuthenticationPlans.RefreshIdentity;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** Client 认证内部请求；敏感字段仅随本次调用存在。 */
@Getter
@AllArgsConstructor
public final class ClientRefreshRequest {
    /** 刷新令牌。 */
    private final String refreshToken;
    /** 锁前解析的会话坐标；入口为 null。 */
    private final RefreshIdentity identity;

    /** 不允许诊断输出打印邮箱或凭据。 */
    @Override public String toString() { return "ClientRefreshRequest[REDACTED]"; }
}
