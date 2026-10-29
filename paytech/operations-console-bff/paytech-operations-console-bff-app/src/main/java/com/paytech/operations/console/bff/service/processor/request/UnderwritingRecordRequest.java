package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按 ID 读取承保（版本详情或 Client 版本历史）的内部请求。 */
@Getter
@AllArgsConstructor
public class UnderwritingRecordRequest {
    /** 入站边界已校验的操作人身份。 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** 承保版本 ID（详情）或 Client ID（历史）；入口已按正整数格式校验。 */
    @NotBlank(message = "ID 不能为空")
    private final String id;
}
