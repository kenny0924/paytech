package com.paytech.operations.console.bff.service.processor.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件上传页完成一次上传的内部请求。 */
@Getter
@AllArgsConstructor
public class CompleteFileUploadRecordRequest {

    /**
     * 上传 ID
     * <li>入口已按正整数格式校验；完成后的文件不是本入口的公开上传时按不存在处理。</li>
     */
    @NotBlank(message = "上传 ID 不能为空")
    private final String uploadId;
}
