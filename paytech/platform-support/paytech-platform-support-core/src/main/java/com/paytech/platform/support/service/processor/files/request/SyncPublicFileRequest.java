package com.paytech.platform.support.service.processor.files.request;

import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 公开文件替换结束后的同步：比较存储最新版本与文件当前版本，不一致则恢复。 */
@Getter
@AllArgsConstructor
public class SyncPublicFileRequest {

    /** 文件 ID。 */
    @Positive(message = "文件 ID 必须为正整数")
    private final long fileId;

    /** 替换上传 ID。 */
    @Positive(message = "上传 ID 必须为正整数")
    private final long uploadId;
}
