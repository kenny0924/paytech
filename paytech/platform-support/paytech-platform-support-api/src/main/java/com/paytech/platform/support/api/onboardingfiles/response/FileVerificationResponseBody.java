package com.paytech.platform.support.api.onboardingfiles.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** CCT 关联文件前使用的最小文件证据；不签发下载 URL。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileVerificationResponseBody {

    /** 文件 ID，十进制字符串。 */
    private String fileId;

    /** 原上传 ID，十进制字符串。 */
    private String uploadId;

    /** 实际字节 SHA-256，小写十六进制。 */
    private String contentHash;

    /** 实际字节数，十进制字符串。 */
    private String sizeBytes;

    /** 服务端识别的媒体类型。 */
    private String mimeType;

    /**
     * 当前扫描状态
     * <li>FileScanStatus 稳定代码。</li>
     */
    private String scanStatus;

    /**
     * 当前对象状态
     * <li>FileObjectStatus 稳定代码。</li>
     */
    private String objectStatus;

    /** 本次本库核验时间（UTC 微秒）。 */
    private Instant checkedAt;

    /** 摘要不作为普通日志内容。 */
    @Override
    public String toString() {
        return "FileVerificationResponseBody[REDACTED]";
    }
}
