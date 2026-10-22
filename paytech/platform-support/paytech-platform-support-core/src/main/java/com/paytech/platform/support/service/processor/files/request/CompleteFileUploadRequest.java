package com.paytech.platform.support.service.processor.files.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 完成一次上传的内部请求；只有开始上传的来源系统可以完成。 */
@Getter
@AllArgsConstructor
public class CompleteFileUploadRequest {

    /** 来源系统，即内部调用身份的服务名。 */
    @NotNull(message = "来源系统必填")
    @Pattern(regexp = "[a-z][a-z0-9-]{0,63}", message = "来源系统必须为服务名")
    private final String sourceSystem;

    /** 上传 ID。 */
    @Positive(message = "上传 ID 必须为正整数")
    private final long uploadId;
}
