package com.paytech.risk.api.evaluation;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.risk.api.evaluation.request.PaymentRiskAssessmentRequestBody;
import com.paytech.risk.api.evaluation.response.PaymentRiskAssessmentResponseBody;

import jakarta.validation.Valid;

/** Payment 支付风控步骤调用的支付尝试评估内部合同（PAYMENT_ATTEMPT_RISK）。 */
@HttpExchange("/payment-risk-assessment/v1")
public interface PaymentRiskAssessmentApi {

    /**
     * 评估一次支付尝试，或返回该支付尝试的原评估
     *
     * @param request
     *            支付尝试身份与评估输入
     * @return 交易评估 ID、正式评估 ID、风控结果与认证要求
     *         <li>同一 paymentAttemptId 只评估一次；riskActionId 与稳定输入均相同的重放返回原结果（含 FAILED/TIMED_OUT），否则 409
     *         CONCURRENT_REQUEST。</li>
     *         <li>clientIpAddress 与 userAgent 不参与重放比较，以首次评估冻结的值为准。</li>
     *         <li>执行失败返回 200 与 FAILED，超过决策点超时返回 200 与 TIMED_OUT，均已落库，调用方不得放行。</li>
     *         <li>本环境没有生效发布返回 503 PAYMENT_RISK_NOT_CONFIGURED，不写评估。</li>
     *         <li>其他 5xx 或读超时结果未知，调用方以原请求重试取回原结果。</li>
     */
    @PostExchange("/assessments")
    PaymentRiskAssessmentResponseBody assess(@Valid @RequestBody PaymentRiskAssessmentRequestBody request);
}
