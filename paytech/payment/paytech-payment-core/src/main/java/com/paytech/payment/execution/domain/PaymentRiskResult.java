package com.paytech.payment.execution.domain;

import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** Risk 已落库的支付风控评估结论。 */
@Getter
@AllArgsConstructor
public class PaymentRiskResult {
    /** Risk 评估 ID（evaluationId） */
    private final Long evaluationId;

    /** 评估结论：ALLOW、REVIEW、DENY 或技术终态 FAILED、TIMED_OUT */
    private final RiskResult riskResult;

    /**
     * 认证要求
     * <li>仅 ALLOW 时有值，其他结论为空。</li>
     */
    private final AuthenticationRequirement authenticationRequirement;
}
