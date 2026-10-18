package com.paytech.platform.support.service.processor.response;

import java.time.Instant;

import com.paytech.platform.support.api.enums.FileObjectStatus;
import com.paytech.platform.support.api.enums.FileScanStatus;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 入驻文件的最小核验证据；不包含存储坐标或下载授权。 */
@Getter
@AllArgsConstructor
public class VerifyOnboardingFileResponse {

    /** 文件 ID。 */
    private final long fileId;

    /** 原上传 ID。 */
    private final long uploadId;

    /** 实际字节 SHA-256，小写十六进制。 */
    private final String contentHash;

    /** 实际字节数。 */
    private final long sizeBytes;

    /** 服务端识别的媒体类型。 */
    private final String mimeType;

    /** 当前扫描状态。 */
    private final FileScanStatus scanStatus;

    /** 当前对象状态。 */
    private final FileObjectStatus objectStatus;

    /** 本次本库核验时间（UTC 微秒）。 */
    private final Instant checkedAt;

    /** 摘要不作为普通日志内容。 */
    @Override
    public String toString() {
        return "VerifyOnboardingFileResponse[" + fileId + "]";
    }
}
