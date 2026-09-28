package com.paytech.risk.ext.rest.evaluation;

import com.paytech.risk.api.evaluation.ManualPayoutEligibilityApi;
import com.paytech.risk.api.evaluation.ManualPayoutEligibilityModels.EvaluateRequestBody;
import com.paytech.risk.api.evaluation.ManualPayoutEligibilityModels.EvaluateResponseBody;
import com.paytech.risk.decision.domain.ManualPayoutEligibilityCommand;
import com.paytech.risk.decision.domain.ManualPayoutEligibilityResult;
import com.paytech.risk.decision.domainservice.service.ManualPayoutEligibilityService;
import java.math.BigDecimal;
import org.springframework.web.bind.annotation.RestController;

/**
 * RCO-I09 Manual Payout 资格评估内部 HTTP 入口。
 */
@RestController
public class ManualPayoutEligibilityController implements ManualPayoutEligibilityApi {

    private final ManualPayoutEligibilityService eligibilityService;

    public ManualPayoutEligibilityController(ManualPayoutEligibilityService eligibilityService) {
        this.eligibilityService = eligibilityService;
    }

    @Override
    public EvaluateResponseBody evaluate(EvaluateRequestBody request) {
        ManualPayoutEligibilityResult result = eligibilityService.evaluate(toCommand(request));
        return new EvaluateResponseBody(result.assessmentId(), result.evaluationId(), result.decision(),
            result.reasonCodes(), result.matchedControls(), result.requiredAmount(), result.requiredCurrency(),
            result.controlSetHash());
    }

    private ManualPayoutEligibilityCommand toCommand(EvaluateRequestBody request) {
        return new ManualPayoutEligibilityCommand(request.commandKey(), request.checkpointType(), request.roleType(),
            request.roleId(), request.operatingEntityId(), new BigDecimal(request.grossAmount()), request.currency(),
            request.bankCountry(), request.sourceFactType(), request.sourceFactId(), request.sourceFactVersion(),
            request.inputHash());
    }
}
