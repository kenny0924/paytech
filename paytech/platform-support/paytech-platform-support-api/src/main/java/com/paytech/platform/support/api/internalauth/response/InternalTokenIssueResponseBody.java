package com.paytech.platform.support.api.internalauth.response;

import lombok.Getter;
import lombok.Setter;

/** Platform 签发的内部调用 Token。 */
@Getter
@Setter
public class InternalTokenIssueResponseBody {

    /**
     * ES256 JWS Compact Token
     * <li>调用方只在内存中使用，放入 {@code Authorization: Bearer}；不得写入日志或持久化。</li>
     */
    private String token;

    /**
     * 过期时间
     * <li>UTC ISO-8601 秒精度，例如 {@code 2026-10-17T10:05:00Z}；与 Token {@code exp} 一致。</li>
     */
    private String expiresAt;

    /** 避免 Token 经默认字符串表示进入日志。 */
    @Override
    public String toString() {
        return "InternalTokenIssueResponseBody[expiresAt=" + expiresAt + ", token=REDACTED]";
    }
}
