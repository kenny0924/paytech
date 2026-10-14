package com.paytech.payment.test.mocker;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.test.context.TestComponent;

import com.paytech.foundation.testsupport.api.Clear;
import com.paytech.foundation.testsupport.api.MockHttpFault;
import com.paytech.payment.integration.risk.request.PaymentRiskAssessmentRequestBody;
import com.paytech.payment.integration.risk.response.PaymentRiskAssessmentResponseBody;

/** Risk 支付评估（合同草案）的响应 DTO：按用例配置结论或故障，记录评估身份。 */
@TestComponent
public class RiskMocker implements Clear {
    private final List<String> attempts = new ArrayList<>();
    private PaymentRiskAssessmentResponseBody response;
    private MockHttpFault fault;
    private int unexpected;

    /** 配置评估结论。 */
    public synchronized void respond(String riskResult, String authenticationRequirement) {
        response = new PaymentRiskAssessmentResponseBody();
        response.setAssessmentId("7001");
        response.setRiskResult(riskResult);
        response.setAuthenticationRequirement(authenticationRequirement);
        fault = null;
    }

    /** 配置故障。 */
    public synchronized void fail(MockHttpFault failure) {
        fault = failure;
        response = null;
    }

    /** HTTP Mock 实际调用此处。 */
    public synchronized PaymentRiskAssessmentResponseBody assess(PaymentRiskAssessmentRequestBody body) {
        attempts.add(body.getPaymentAttemptId());
        if (fault != null) {
            throw fault;
        }
        if (response == null) {
            unexpected++;
            throw new AssertionError("未配置的 Risk 评估");
        }
        return response;
    }

    /** 本例评估过的支付尝试。 */
    public synchronized List<String> attempts() {
        return List.copyOf(attempts);
    }

    /** 每例结束检查没有未配置的评估。 */
    public synchronized void assertNoUnexpectedCalls() {
        assertEquals(0, unexpected, "存在未配置的 Risk 评估");
    }

    @Override
    public synchronized void clear() {
        attempts.clear();
        response = null;
        fault = null;
        unexpected = 0;
    }
}
