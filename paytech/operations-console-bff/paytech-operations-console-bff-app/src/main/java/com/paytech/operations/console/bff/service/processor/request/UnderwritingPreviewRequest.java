package com.paytech.operations.console.bff.service.processor.request;

import com.paytech.operations.console.bff.service.model.OperatorContext;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 承保预览的内部请求。 */
@Getter
@AllArgsConstructor
public class UnderwritingPreviewRequest {
    /** 入站边界已校验的操作人身份。 */
    @NotNull(message = "操作人身份不能为空")
    private final OperatorContext operator;

    /** Client ID；入口已按正整数格式校验。 */
    @NotBlank(message = "Client ID 不能为空")
    private final String clientId;

    /** 结算周期建议 T+N 的 N；为空按 0 计。 */
    private final Integer suggestedSettlementDays;
}
