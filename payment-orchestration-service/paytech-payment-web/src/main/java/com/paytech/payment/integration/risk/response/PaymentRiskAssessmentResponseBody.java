package com.paytech.payment.integration.risk.response;

import lombok.Getter;
import lombok.Setter;

/** Risk 支付评估结论（草案）。 */
@Getter
@Setter
public class PaymentRiskAssessmentResponseBody {
    /** 评估标识，正十进制 long 字符串 */
    private String assessmentId;

    /**
     * 风控结论
     * <li>ALLOW、REVIEW、DENY。</li>
     */
    private String riskResult;

    /**
     * 认证要求
     * <li>NOT_REQUIRED、THREE_DS_REQUIRED、CHALLENGE_REQUIRED。</li>
     */
    private String authenticationRequirement;
}
