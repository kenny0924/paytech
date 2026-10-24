package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 文件上传页查询单个上传（检查状态与地址）的内部请求。 */
@Getter
@AllArgsConstructor
public class GetFileUploadRecordRequest {

    /** 入站边界已校验的操作人身份 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /**
     * 文件 ID
     * <li>入口已按正整数格式校验；不是本入口上传的文件按不存在处理。</li>
     */
    @NotBlank(message = "文件 ID 不能为空")
    private final String fileId;
}
