package com.paytech.risk.ext.rest.evaluation;

import java.util.Map;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.ServiceUnavailableException;
import com.paytech.foundation.httpproblem.api.ProblemFieldError;
import com.paytech.foundation.httpproblem.api.RequestBodyValidationException;
import com.paytech.risk.api.enums.RiskDecisionEvaluationErrorCode;
import com.paytech.risk.api.evaluation.RiskDecisionApi;
import com.paytech.risk.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.api.evaluation.response.EvaluateDecisionResponseBody;
import com.paytech.risk.decision.domainservice.service.DecisionInputViolationException;
import com.paytech.risk.decision.domainservice.service.DecisionNotConfiguredException;
import com.paytech.risk.decision.service.processor.EvaluateDecisionProcessor;
import com.paytech.risk.decision.service.processor.request.EvaluateDecisionRequest;
import com.paytech.risk.decision.service.processor.response.EvaluateDecisionResult;

import lombok.AllArgsConstructor;

/** 协议转换后委托统一评估 Processor；输入合同违规映射为 422 字段错误，没有生效发布映射为 503。 */
@RestController
@AllArgsConstructor
public class RiskDecisionController implements RiskDecisionApi {
    private final EvaluateDecisionProcessor processor;

    /** 评估或返回原评估。 */
    @Override
    public EvaluateDecisionResponseBody evaluate(EvaluateDecisionRequestBody request) {
        EvaluateDecisionResult result;
        try {
            result = processor.process(new EvaluateDecisionRequest(request.getPointCode(), request.getContractVersion(),
                request.getBusinessKey(), request.getInputs()));
        } catch (DecisionInputViolationException violation) {
            throw new RequestBodyValidationException(violation.violations().stream()
                .map(value -> new ProblemFieldError(value.field(), value.message())).toList(), violation);
        } catch (DecisionNotConfiguredException notConfigured) {
            throw new ServiceUnavailableException(RiskDecisionEvaluationErrorCode.RISK_DECISION_NOT_CONFIGURED,
                notConfigured);
        }
        return toBody(result);
    }

    /** 结论转换；ID 以十进制字符串传输。 */
    static EvaluateDecisionResponseBody toBody(EvaluateDecisionResult result) {
        EvaluateDecisionResponseBody body = new EvaluateDecisionResponseBody();
        body.setEvaluationId(Long.toString(result.getEvaluationId()));
        body.setResult(result.getResult());
        body.setOutputs(Map.copyOf(result.getOutputs()));
        return body;
    }
}
