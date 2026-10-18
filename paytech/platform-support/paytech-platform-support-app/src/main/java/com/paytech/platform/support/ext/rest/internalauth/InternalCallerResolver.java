package com.paytech.platform.support.ext.rest.internalauth;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.CallerIdentityHolder;

/**
 * 内部 HTTP 入口的调用方解析（D26）：Webhook、Notification 与 Token 签发共用。
 * <li>internal-auth.mode=OFF 或 DUAL 未携带 Token：沿用 X-Caller-Service（过渡信任）。</li>
 * <li>携带已验证 Token：以 Token {@code sub} 为调用方；X-Caller-Service 与之不一致时拒绝，不允许借合法 Token 冒充其他服务。</li>
 */
@Component
public class InternalCallerResolver {

    /**
     * 返回本次请求的调用方服务名。
     *
     * @param headerCaller X-Caller-Service 的值
     * @return 调用方服务名；未识别时为 Header 原值
     * @throws BizCheckFailException Header 与已验证 Token 的调用方不一致
     */
    public String resolve(String headerCaller) {
        CallerIdentity identity = CallerIdentityHolder.current();
        if (identity == null || !identity.verified()) {
            return headerCaller;
        }
        if (headerCaller != null && !headerCaller.equals(identity.caller())) {
            throw new BizCheckFailException("X-Caller-Service 与内部 Token 的调用方不一致: " + headerCaller + " / "
                + identity.caller());
        }
        return identity.caller();
    }
}
