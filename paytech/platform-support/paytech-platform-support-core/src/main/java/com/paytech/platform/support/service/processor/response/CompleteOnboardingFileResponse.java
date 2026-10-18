package com.paytech.platform.support.service.processor.response;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成入驻文件上传的内部结果；首次完成与重复完成返回同一组值。 */
@Getter
@AllArgsConstructor
public class CompleteOnboardingFileResponse {

    /** 上传 ID，同时是本次完成操作的身份。 */
    private final long uploadId;

    /** 该上传唯一产生的文件 ID。 */
    private final long fileId;

    /** 首次成功完成的时间。 */
    private final Instant completedAt;
}
