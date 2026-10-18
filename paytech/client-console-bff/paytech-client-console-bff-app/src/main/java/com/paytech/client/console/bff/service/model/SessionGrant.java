package com.paytech.client.console.bff.service.model;

import com.paytech.platform.support.api.authentication.response.AccountContextResponseBody;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Platform 签发并经 BFF 校验的会话凭据
 * <li>Refresh Token 只写入 host-only Cookie，不进入浏览器 JSON 响应。</li>
 */
@Getter
@AllArgsConstructor
public class SessionGrant {

    /** 当前账号上下文 */
    private final AccountContextResponseBody context;

    /** Access Token，只返回给页面内存 */
    private final String accessToken;

    /** Access Token 到期时间，UTC 六位微秒字符串 */
    private final String expiresAt;

    /** Refresh Token，格式已校验 */
    private final String refreshToken;

    /**
     * Refresh Cookie 的 Max-Age
     * <li>单位秒，大于 0 且不超过 604800。</li>
     */
    private final long refreshMaxAgeSeconds;

    /** 凭据不打印到日志。 */
    @Override
    public String toString() {
        return "SessionGrant[REDACTED]";
    }
}
