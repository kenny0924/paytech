package com.paytech.platform.support.service.processor.files.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件原版本的短时只读下载地址。 */
@Getter
@AllArgsConstructor
public class AuthorizeFileDownloadResponse {

    /** 临时 HTTPS 地址，不落库、不进日志。 */
    private final String url;

    /** 到期时间。 */
    private final Instant expiresAt;

    /** 临时地址不可打印。 */
    @Override
    public String toString() {
        return "AuthorizeFileDownloadResponse[REDACTED]";
    }
}
