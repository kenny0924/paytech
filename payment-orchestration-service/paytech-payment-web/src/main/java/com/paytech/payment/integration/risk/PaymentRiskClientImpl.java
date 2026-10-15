package com.paytech.payment.integration.risk;

import java.util.EnumSet;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.CommonErrorCode;
import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.utils.EnumUtils;
import com.paytech.payment.execution.core.enums.AuthenticationRequirement;
import com.paytech.payment.execution.core.enums.RiskResult;
import com.paytech.payment.execution.domain.PaymentRiskCommand;
import com.paytech.payment.execution.domain.PaymentRiskResult;
import com.paytech.payment.execution.integration.PaymentRiskClient;
import com.paytech.risk.api.evaluation.RiskDecisionApi;
import com.paytech.risk.api.evaluation.request.PaymentAttemptRiskInput;
import com.paytech.risk.api.evaluation.response.EvaluateDecisionResponseBody;

import lombok.AllArgsConstructor;

/** Risk 统一评估端口的 HTTP 实现：按支付风控点输入合同组装请求并校验结论；响应无效时抛出系统异常，由风控动作失败关闭。 */
@Component
@AllArgsConstructor
public class PaymentRiskClientImpl implements PaymentRiskClient {
    /** Risk 对支付风控点放行时可能给出的认证要求 */
    private static final EnumSet<AuthenticationRequirement> ALLOWED_AUTHENTICATIONS =
        EnumSet.of(AuthenticationRequirement.NOT_REQUIRED, AuthenticationRequirement.THREE_DS_REQUIRED);

    private final RiskDecisionApi riskDecisionApi;

    /** 调用统一评估并解析结论；不重试，异常按 Foundation 分类抛出。 */
    @Override
    public PaymentRiskResult evaluate(PaymentRiskCommand command) {
        EvaluateDecisionResponseBody body =
            riskDecisionApi.evaluate(input(command).toRequest(command.getPaymentAttemptId().toString()));
        if (body.getEvaluationId() == null || !body.getEvaluationId().matches("[1-9][0-9]{0,18}")) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, "Risk 评估 ID 无效");
        }
        RiskResult risk = EnumUtils.getEnumByCode(RiskResult.class, body.getResult());
        if (risk == null) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, "Risk 评估结论无效");
        }
        return new PaymentRiskResult(Long.valueOf(body.getEvaluationId()), risk,
            risk == RiskResult.ALLOW ? authentication(body.getOutputs()) : null);
    }

    /** 放行时必须给出 NOT_REQUIRED 或 THREE_DS_REQUIRED，否则不能决定是否认证。 */
    static AuthenticationRequirement authentication(Map<String, String> outputs) {
        String code = outputs == null ? null : outputs.get(PaymentAttemptRiskInput.AUTHENTICATION_REQUIREMENT);
        AuthenticationRequirement authentication = EnumUtils.getEnumByCode(AuthenticationRequirement.class, code);
        if (authentication == null || !ALLOWED_AUTHENTICATIONS.contains(authentication)) {
            throw new ErrorException(CommonErrorCode.UPSTREAM_INVALID_RESPONSE, "Risk 放行结论缺少认证要求");
        }
        return authentication;
    }

    /** ID 为十进制字符串，金额去除尾随零后的主单位字符串；可选值为 null 时由输入类省略。 */
    static PaymentAttemptRiskInput input(PaymentRiskCommand command) {
        return new PaymentAttemptRiskInput(command.getPaymentIntentId().toString(),
            command.getRiskActionId().toString(), command.getOrderId().toString(), command.getClientId().toString(),
            command.getEntityNo(), command.getPaymentMethodCode(), command.getCaptureMode().getCode(),
            command.getAmount().stripTrailingZeros().toPlainString(), command.getCurrency(), command.getIndustryCode(),
            command.getMccCode(), command.getConsumerCountryCode(), command.getConsumerReference(),
            command.getClientIpAddress(), command.getUserAgent(), command.getCardFingerprint(), command.getCardBin());
    }
}
