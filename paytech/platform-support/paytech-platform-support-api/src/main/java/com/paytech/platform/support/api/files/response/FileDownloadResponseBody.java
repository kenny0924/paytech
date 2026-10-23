package com.paytech.platform.support.api.files.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 文件某一已核对版本的短时只读下载地址。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileDownloadResponseBody {

    /** 临时 HTTPS 下载地址，返回所签版本的字节（固定存储 versionId）。 */
    private String url;

    /**
     * 到期时间（UTC）
     * <li>签发后最长 60 秒。</li>
     */
    private Instant expiresAt;

    /**
     * 实际生效的下载方式
     * <li>{@code FileDisposition} Code：INLINE、ATTACHMENT；请求 INLINE 但不满足条件时为 ATTACHMENT。</li>
     */
    private String disposition;

    /** 临时地址不可进入普通日志。 */
    @Override
    public String toString() {
        return "FileDownloadResponseBody[REDACTED]";
    }
}
