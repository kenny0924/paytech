package com.paytech.platform.support.service.processor.files.response;

import java.time.Instant;

import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 开始上传的结果：上传 ID、临时 PUT 授权与上传到期时间。 */
@Getter
@AllArgsConstructor
public class StartFileUploadResponse {

    /** 上传 ID。 */
    private final long uploadId;

    /** 临时 PUT 授权，不落库、不进日志。 */
    private final Grant grant;

    /** 上传到期时间。 */
    private final Instant expiresAt;

    /** 临时授权不可打印。 */
    @Override
    public String toString() {
        return "StartFileUploadResponse[" + uploadId + "]";
    }
}
