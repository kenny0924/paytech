package com.paytech.platform.support.service.processor.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 运营审核下载的展示信息与原版本短时下载授权；不持久化。 */
@Getter
@AllArgsConstructor
public class ReviewFileDownloadResponse {

    /** 文件 ID。 */
    private final long fileId;

    /** 客户上传时的展示文件名。 */
    private final String displayName;

    /** 服务端识别的媒体类型。 */
    private final String mimeType;

    /** 实际字节数。 */
    private final long sizeBytes;

    /** HTTPS 短时 GET 授权 URL。 */
    private final String url;

    /** 本次授权的实际到期时间，不晚于签发期限。 */
    private final Instant expiresAt;

    /** 临时签名 URL 不进入日志。 */
    @Override
    public String toString() {
        return "ReviewFileDownloadResponse[REDACTED]";
    }
}
