package com.paytech.platform.support.service.processor.response;

import java.time.Instant;

import com.paytech.platform.support.filedocument.domain.file.UploadModels.Grant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 开始入驻文件上传的内部结果；临时授权只回传给本次调用，不落库。 */
@Getter
@AllArgsConstructor
public class StartOnboardingUploadResponse {

    /** 本次新建的上传 ID。 */
    private final long uploadId;

    /** 存储适配器为原对象目标签发的临时 PUT 授权。 */
    private final Grant authorization;

    /** 原上传的不可延长到期时间。 */
    private final Instant expiresAt;

    /** 临时授权不可进入普通日志。 */
    @Override
    public String toString() {
        return "StartOnboardingUploadResponse[" + uploadId + "]";
    }
}
