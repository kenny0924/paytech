package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 按 Client 读取档位工作台或变更历史的内部请求。 */
@Getter
@AllArgsConstructor
public class LimitTierClientRequest {
    /** 入站边界已校验的操作人身份。 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** Client ID；入口已按正整数格式校验。 */
    @NotBlank(message = "Client ID 不能为空")
    private final String clientId;
}
