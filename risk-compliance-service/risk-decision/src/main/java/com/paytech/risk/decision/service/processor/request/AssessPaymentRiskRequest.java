package com.paytech.risk.decision.service.processor.request;

import com.paytech.risk.decision.domain.runtime.PaymentRiskInput;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付尝试风控评估的 Owner 输入；字段格式已在 HTTP 入口校验。 */
@Getter
@AllArgsConstructor
public class AssessPaymentRiskRequest {
    /** 冻结输入。 */
    @NotNull
    private final PaymentRiskInput input;
}
