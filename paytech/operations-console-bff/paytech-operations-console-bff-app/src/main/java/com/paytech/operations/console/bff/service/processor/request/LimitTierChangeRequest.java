package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.risklimittier.RequestTierChangePageRequestBody;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 提交档位变更申请（L4）的内部请求；页面 Body 已经严格解析与字段校验。 */
@Getter
@AllArgsConstructor
public class LimitTierChangeRequest {
    /** 入站边界已校验的操作人身份。 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** Client ID；入口已按正整数格式校验。 */
    @NotBlank(message = "Client ID 不能为空")
    private final String clientId;

    /** 页面申请内容。 */
    @NotNull(message = "申请内容不能为空")
    private final RequestTierChangePageRequestBody body;
}
