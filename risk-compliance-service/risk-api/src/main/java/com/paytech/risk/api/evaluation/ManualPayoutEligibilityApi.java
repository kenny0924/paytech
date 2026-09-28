package com.paytech.risk.api.evaluation;

import com.paytech.risk.api.evaluation.ManualPayoutEligibilityModels.EvaluateRequestBody;
import com.paytech.risk.api.evaluation.ManualPayoutEligibilityModels.EvaluateResponseBody;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * RCO-I09 Manual Payout 资格检查内部契约。
 */
@HttpExchange("/manual-payout-eligibility/v1")
public interface ManualPayoutEligibilityApi {

    /**
     * 在指定业务检查点创建或重放一次不可变资格评估。
     *
     * @param request 完整评估范围
     * @return 风控决定及稳定证据坐标
     */
    @PostExchange("/evaluations")
    EvaluateResponseBody evaluate(@Valid @RequestBody EvaluateRequestBody request);
}
