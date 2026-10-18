package com.paytech.card.ext.rest;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.CallerIdentityHolder;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;

/**
 * card 入口的调用方解析（D26，design/05 §4）
 * <li>internal-auth.mode=OFF，或 DUAL 未携带 Token：沿用 X-Caller-Service（过渡信任，部署须限制为私网）。</li>
 * <li>携带已验证 Token：以 Token 调用方为准；X-Caller-Service 与之不一致时拒绝（400），不允许借合法 Token 冒充其他服务。</li>
 * <li>Purpose：各调用方的 typed 合同（api 模块不能依赖 internal-auth，R2）无法声明专用 Purpose，只会发出 GENERAL；其他 Purpose 视为不符，
 * 解析为无调用方，由业务入口按调用方白名单拒绝（404）。</li>
 */
@Component
public class CardCallerResolver {

    /**
     * 返回本次请求的调用方服务名。
     *
     * @param headerCaller X-Caller-Service 的值
     * @return 调用方服务名；Token Purpose 不符时为 null
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
        return InternalTokenClaims.GENERAL_PURPOSE.equals(identity.purpose()) ? identity.caller() : null;
    }
}
