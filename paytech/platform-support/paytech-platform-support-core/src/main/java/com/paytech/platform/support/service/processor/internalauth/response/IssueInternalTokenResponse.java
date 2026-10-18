package com.paytech.platform.support.service.processor.internalauth.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 签发结果；Token 只在内存中传递。 */
@Getter
@AllArgsConstructor
public class IssueInternalTokenResponse {

    /** ES256 JWS Compact Token。 */
    private final String token;

    /** 过期时间（秒精度），与 Token {@code exp} 一致。 */
    private final Instant expiresAt;

    /** 避免 Token 经默认字符串表示进入日志。 */
    @Override
    public String toString() {
        return "IssueInternalTokenResponse[expiresAt=" + expiresAt + ", token=REDACTED]";
    }
}
