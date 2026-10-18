package com.paytech.platform.support.api.onboardingfiles.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 准确不可变版本的短时下载授权；不持久化。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class DownloadResponseBody {

    /**
     * 短时 GET 授权 URL
     * <li>HTTPS，指向原不可变版本。</li>
     */
    private String url;

    /**
     * 本次授权到期时间（UTC）
     * <li>最长 60 秒。</li>
     */
    private Instant expiresAt;

    /** 临时签名 URL 不进入日志。 */
    @Override
    public String toString() {
        return "DownloadResponseBody[REDACTED]";
    }
}
