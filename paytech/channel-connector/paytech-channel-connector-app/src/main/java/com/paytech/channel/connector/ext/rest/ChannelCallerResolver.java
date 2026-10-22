package com.paytech.channel.connector.ext.rest;

import org.springframework.stereotype.Component;

import com.paytech.channel.connector.api.ChannelConnectorHeaders;
import com.paytech.foundation.common.error.BizCheckFailException;
import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.foundation.internalauth.api.CallerIdentity;
import com.paytech.foundation.internalauth.api.CallerIdentityHolder;
import com.paytech.foundation.internalauth.api.InternalTokenClaims;

/**
 * 通用执行入口的调用方解析（参照 card 的 CardCallerResolver，technique 第 6.2 节）
 * <li>携带已验证内部 Token：以 Token 调用方为准；{@value ChannelConnectorHeaders#CALLER_HEADER} 与之不一致时 400，不允许借合法 Token 冒充其他服务；
 * Token Purpose 不是 GENERAL 时视为调用方缺失。</li>
 * <li>internal-auth 关闭，或 DUAL 未携带 Token：信任 {@value ChannelConnectorHeaders#CALLER_HEADER}（过渡信任，部署须限制为私网；生产须开启 internal-auth）。</li>
 * <li>无法确认调用方（缺失或格式不符）时 400，不发出外部请求。</li>
 */
@Component
public class ChannelCallerResolver {

    /** 调用方服务名格式，与调用授权一致。 */
    private static final String CALLER_SERVICE = "[a-z][a-z0-9-]{1,63}";

    /**
     * 返回本次请求已确认的调用方服务名。
     *
     * @param headerCaller {@value ChannelConnectorHeaders#CALLER_HEADER} 的值，可空
     * @return 调用方服务名
     */
    public String resolve(String headerCaller) {
        String caller = tokenOrHeader(headerCaller);
        if (caller == null || !caller.matches(CALLER_SERVICE)) {
            throw new InvalidParameterException("缺少有效的调用方身份 " + ChannelConnectorHeaders.CALLER_HEADER);
        }
        return caller;
    }

    /** Token 优先；未携带已验证 Token 时取请求头。 */
    String tokenOrHeader(String headerCaller) {
        CallerIdentity identity = CallerIdentityHolder.current();
        if (identity == null || !identity.verified()) {
            return headerCaller;
        }
        if (headerCaller != null && !headerCaller.equals(identity.caller())) {
            throw new BizCheckFailException(ChannelConnectorHeaders.CALLER_HEADER + " 与内部 Token 的调用方不一致: "
                + headerCaller + " / " + identity.caller());
        }
        return InternalTokenClaims.GENERAL_PURPOSE.equals(identity.purpose()) ? identity.caller() : null;
    }
}
