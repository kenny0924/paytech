package com.paytech.platform.support.service.processor.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 准确不可变版本的短时下载授权；不持久化。 */
@Getter
@AllArgsConstructor
public class DownloadOnboardingFileResponse {

    /** HTTPS 短时 GET 授权 URL。 */
    private final String url;

    /** 本次授权的实际到期时间，不晚于签发期限。 */
    private final Instant expiresAt;

    /** 临时签名 URL 不进入日志。 */
    @Override
    public String toString() {
        return "DownloadOnboardingFileResponse[REDACTED]";
    }
}
