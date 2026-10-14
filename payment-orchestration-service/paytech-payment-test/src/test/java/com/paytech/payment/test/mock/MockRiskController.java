package com.paytech.payment.test.mock;

import org.springframework.boot.test.context.TestComponent;
import org.springframework.web.bind.annotation.RestController;

import com.paytech.payment.integration.risk.PaymentRiskAssessmentApi;
import com.paytech.payment.integration.risk.request.PaymentRiskAssessmentRequestBody;
import com.paytech.payment.integration.risk.response.PaymentRiskAssessmentResponseBody;
import com.paytech.payment.test.mocker.RiskMocker;

import lombok.AllArgsConstructor;

/** 仅存在于独立测试 HTTP 服务的 Risk 支付评估（草案）实现。 */
@RestController
@TestComponent
@AllArgsConstructor
public class MockRiskController implements PaymentRiskAssessmentApi {
    private final RiskMocker risk;

    /** 委托 RiskMocker 返回预设结论。 */
    @Override
    public PaymentRiskAssessmentResponseBody assess(PaymentRiskAssessmentRequestBody body) {
        return risk.assess(body);
    }
}
