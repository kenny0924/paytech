package com.paytech.payment.execution.domain;

import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付风控结论。 */
@Getter
@AllArgsConstructor
public class PaymentRiskResult {
    /** 风控评估标识 */
    private final Long transactionAssessmentId;

    /** 风控结论 */
    private final RiskResult riskResult;

    /** 认证要求 */
    private final AuthenticationRequirement authenticationRequirement;
}
