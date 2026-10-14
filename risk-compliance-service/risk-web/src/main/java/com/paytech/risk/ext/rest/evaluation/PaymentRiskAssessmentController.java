package com.paytech.risk.ext.rest.evaluation;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.risk.api.enums.PaymentRiskErrorCode;
import com.paytech.risk.api.evaluation.PaymentRiskAssessmentApi;
import com.paytech.risk.api.evaluation.request.PaymentRiskAssessmentRequestBody;
import com.paytech.risk.api.evaluation.response.PaymentRiskAssessmentResponseBody;
import com.paytech.risk.decision.domain.runtime.PaymentRiskInput;
import com.paytech.risk.decision.domainservice.service.PaymentRiskNotConfiguredException;
import com.paytech.risk.decision.service.processor.AssessPaymentRiskProcessor;
import com.paytech.risk.decision.service.processor.request.AssessPaymentRiskRequest;
import com.paytech.risk.decision.service.processor.response.AssessPaymentRiskResponse;

import lombok.AllArgsConstructor;

/** 协议转换后委托 risk-decision 的评估 Processor；没有生效发布映射为 503 PAYMENT_RISK_NOT_CONFIGURED。 */
@RestController
@AllArgsConstructor
public class PaymentRiskAssessmentController implements PaymentRiskAssessmentApi {
    private final AssessPaymentRiskProcessor processor;

    /** 评估或返回原评估。 */
    @Override
    public PaymentRiskAssessmentResponseBody assess(PaymentRiskAssessmentRequestBody request) {
        AssessPaymentRiskResponse response;
        try {
            response = processor.process(new AssessPaymentRiskRequest(toInput(request)));
        } catch (PaymentRiskNotConfiguredException notConfigured) {
            throw new ServiceUnavailableException(PaymentRiskErrorCode.PAYMENT_RISK_NOT_CONFIGURED, notConfigured);
        }
        return toBody(response);
    }

    /** Wire 字段已由注解校验，此处只做类型转换。 */
    static PaymentRiskInput toInput(PaymentRiskAssessmentRequestBody body) {
        return new PaymentRiskInput(Long.parseLong(body.getPaymentAttemptId()),
            Long.parseLong(body.getPaymentIntentId()), Long.parseLong(body.getRiskActionId()),
            Long.parseLong(body.getOrderId()), Long.parseLong(body.getClientId()), body.getEntityNo(),
            body.getPaymentMethodCode(), body.getCaptureMode(), new BigDecimal(body.getAmount()), body.getCurrency(),
            body.getIndustryCode(), body.getMccCode(), body.getConsumerCountryCode(), body.getConsumerReference(),
            body.getClientIpAddress(), body.getUserAgent(), body.getCardFingerprint(), body.getCardBin());
    }

    /** 结果转换；ID 以十进制字符串传输。 */
    static PaymentRiskAssessmentResponseBody toBody(AssessPaymentRiskResponse response) {
        PaymentRiskAssessmentResponseBody body = new PaymentRiskAssessmentResponseBody();
        body.setAssessmentId(Long.toString(response.getAssessmentId()));
        body.setEvaluationId(Long.toString(response.getEvaluationId()));
        body.setRiskResult(response.getRiskResult());
        body.setAuthenticationRequirement(
            response.getAuthenticationRequirement() == null ? null : response.getAuthenticationRequirement().getCode());
        return body;
    }
}
