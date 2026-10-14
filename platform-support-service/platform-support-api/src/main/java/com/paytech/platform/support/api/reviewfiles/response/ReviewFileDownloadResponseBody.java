package com.paytech.platform.support.api.reviewfiles.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;

/** 运营审核下载授权；临时地址不进入日志。 */
public class ReviewFileDownloadResponseBody {
    /** 文件标识，十进制字符串。 */
    private final String fileId;

    /** 客户上传时的展示文件名。 */
    private final String displayName;

    /** 服务端识别的媒体类型。 */
    private final String mimeType;

    /** 实际字节数，十进制字符串。 */
    private final String sizeBytes;

    /**
     * 下载地址
     * <li>HTTPS，指向原不可变版本，以附件方式返回</li>
     */
    private final String url;

    /** 下载地址失效时间（UTC），最长 60 秒。 */
    private final Instant expiresAt;

    /** 按 JSON 属性绑定。 */
    @JsonCreator(mode = JsonCreator.Mode.PROPERTIES)
    public ReviewFileDownloadResponseBody(@JsonProperty("fileId") String fileId,
        @JsonProperty("displayName") String displayName, @JsonProperty("mimeType") String mimeType,
        @JsonProperty("sizeBytes") String sizeBytes, @JsonProperty("url") String url,
        @JsonProperty("expiresAt") Instant expiresAt) {
        this.fileId = fileId;
        this.displayName = displayName;
        this.mimeType = mimeType;
        this.sizeBytes = sizeBytes;
        this.url = url;
        this.expiresAt = expiresAt;
    }

    /** 文件标识。 */
    public String getFileId() {
        return fileId;
    }

    /** 展示文件名。 */
    public String getDisplayName() {
        return displayName;
    }

    /** 媒体类型。 */
    public String getMimeType() {
        return mimeType;
    }

    /** 字节数。 */
    public String getSizeBytes() {
        return sizeBytes;
    }

    /** 下载地址。 */
    public String getUrl() {
        return url;
    }

    /** 失效时间。 */
    public Instant getExpiresAt() {
        return expiresAt;
    }

    /** 临时签名地址不进入日志。 */
    @Override
    public String toString() {
        return "ReviewFileDownloadResponseBody[" + fileId + "]";
    }
}
