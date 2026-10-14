package com.paytech.risk.decision.service.processor.response;

import com.paytech.risk.decision.domain.enums.AuthenticationRequirement;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 支付尝试风控评估结果；同一支付尝试重放返回原值。 */
@Getter
@AllArgsConstructor
public class AssessPaymentRiskResponse {
    /** 交易评估 ID。 */
    private final long assessmentId;

    /** 正式评估 ID。 */
    private final long evaluationId;

    /** 风控结果 Code：ALLOW、REVIEW、DENY、FAILED 或 TIMED_OUT。 */
    private final String riskResult;

    /** 认证要求；仅 ALLOW 非空。 */
    private final AuthenticationRequirement authenticationRequirement;
}
