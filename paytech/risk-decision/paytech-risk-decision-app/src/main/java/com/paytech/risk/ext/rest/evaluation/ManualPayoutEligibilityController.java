package com.paytech.risk.ext.rest.evaluation;

import java.math.BigDecimal;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.risk.api.evaluation.ManualPayoutEligibilityApi;
import com.paytech.risk.api.evaluation.request.ManualPayoutEligibilityRequestBody;
import com.paytech.risk.api.evaluation.response.ManualPayoutEligibilityResponseBody;
import com.paytech.risk.control.service.processor.ManualPayoutEligibilityProcessor;
import com.paytech.risk.control.service.processor.request.ManualPayoutEligibilityRequest;
import com.paytech.risk.control.service.processor.response.ManualPayoutEligibilityResponse;

import lombok.AllArgsConstructor;

/** RCO-I09 Manual Payout 资格评估私网 HTTP 入口，只做协议转换并委托 Owner Processor。 */
@RestController
@AllArgsConstructor
public class ManualPayoutEligibilityController implements ManualPayoutEligibilityApi {
    private final ManualPayoutEligibilityProcessor eligibilityProcessor;

    /** 转换 Body 后委托 risk-control 评估。 */
    @Override
    public ManualPayoutEligibilityResponseBody evaluate(ManualPayoutEligibilityRequestBody request) {
        return toResponseBody(eligibilityProcessor.process(toRequest(request)));
    }

    /** 金额按币种主单位十进制字符串精确转换，不经过浮点。 */
    ManualPayoutEligibilityRequest toRequest(ManualPayoutEligibilityRequestBody body) {
        return new ManualPayoutEligibilityRequest(body.getCommandKey(), body.getCheckpointType(), body.getRoleType(),
            body.getRoleId(), body.getOperatingEntityId(), new BigDecimal(body.getGrossAmount()), body.getCurrency(),
            body.getBankCountry(), body.getSourceFactType(), body.getSourceFactId(), body.getSourceFactVersion(),
            body.getInputHash());
    }

    /** 评估结果逐字段投影为响应 Body。 */
    ManualPayoutEligibilityResponseBody toResponseBody(ManualPayoutEligibilityResponse response) {
        ManualPayoutEligibilityResponseBody body = new ManualPayoutEligibilityResponseBody();
        body.setAssessmentId(response.getAssessmentId());
        body.setEvaluationId(response.getEvaluationId());
        body.setDecision(response.getDecision());
        body.setReasonCodes(response.getReasonCodes());
        body.setMatchedControls(response.getMatchedControls());
        body.setRequiredAmount(response.getRequiredAmount());
        body.setRequiredCurrency(response.getRequiredCurrency());
        body.setControlSetHash(response.getControlSetHash());
        return body;
    }
}
