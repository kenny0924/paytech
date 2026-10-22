package com.paytech.platform.support.service.processor.files.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成上传的回执；重复完成返回同一组值。 */
@Getter
@AllArgsConstructor
public class CompleteFileUploadResponse {

    /** 上传 ID。 */
    private final long uploadId;

    /** 登记的文件 ID。 */
    private final long fileId;

    /** 文件登记时间。 */
    private final Instant completedAt;
}
