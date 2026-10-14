package com.paytech.payment.integration.risk;

import java.util.EnumSet;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.domain.PaymentRiskCommand;
import com.paytech.payment.execution.domain.PaymentRiskResult;
import com.paytech.payment.execution.integration.PaymentRiskClient;
import com.paytech.payment.integration.risk.request.PaymentRiskAssessmentRequestBody;
import com.paytech.payment.integration.risk.response.PaymentRiskAssessmentResponseBody;

import lombok.AllArgsConstructor;

/** Risk 支付评估端口的 HTTP 实现；响应无效时抛出系统异常，由风控动作失败关闭。 */
@Component
@AllArgsConstructor
public class PaymentRiskClientImpl implements PaymentRiskClient {
    private final PaymentRiskAssessmentApi paymentRiskAssessmentApi;

    /** 调用 Risk 并解析结论；只接受 ALLOW、REVIEW、DENY，未知代码不放行。 */
    @Override
    public PaymentRiskResult assess(PaymentRiskCommand command) {
        PaymentRiskAssessmentResponseBody body = paymentRiskAssessmentApi.assess(request(command));
        if (body == null || body.getAssessmentId() == null || !body.getAssessmentId().matches("[1-9][0-9]{0,18}")) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, "Risk 评估标识无效");
        }
        RiskResult risk = EnumUtils.getEnumByCode(RiskResult.class, body.getRiskResult());
        AuthenticationRequirement authentication =
            EnumUtils.getEnumByCode(AuthenticationRequirement.class, body.getAuthenticationRequirement());
        if (risk == null || !EnumSet.of(RiskResult.ALLOW, RiskResult.REVIEW, RiskResult.DENY).contains(risk)
            || authentication == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, "Risk 评估结论无效");
        }
        return new PaymentRiskResult(Long.valueOf(body.getAssessmentId()), risk, authentication);
    }

    /** 金额与 ID 按第一方 Wire 规则写成字符串。 */
    static PaymentRiskAssessmentRequestBody request(PaymentRiskCommand command) {
        var body = new PaymentRiskAssessmentRequestBody();
        body.setPaymentAttemptId(command.getPaymentAttemptId().toString());
        body.setPaymentActionId(command.getPaymentActionId().toString());
        body.setClientId(command.getClientId().toString());
        body.setEntityNo(command.getEntityNo());
        body.setAmount(command.getAmount().toPlainString());
        body.setCurrency(command.getCurrency());
        body.setPaymentMethodCode(command.getPaymentMethodCode());
        body.setCountryCode(command.getCountryCode());
        return body;
    }
}
