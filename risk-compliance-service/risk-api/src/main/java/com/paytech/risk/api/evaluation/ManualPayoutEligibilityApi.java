package com.paytech.risk.api.evaluation;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.risk.api.evaluation.request.ManualPayoutEligibilityRequestBody;
import com.paytech.risk.api.evaluation.response.ManualPayoutEligibilityResponseBody;

import jakarta.validation.Valid;

/** RCO-I09 Manual Payout 四个检查点的资格评估内部合同。 */
@HttpExchange("/manual-payout-eligibility/v1")
public interface ManualPayoutEligibilityApi {

    /**
     * 在指定检查点创建或重放一次不可变资格评估
     *
     * @param request
     *            完整评估范围
     * @return 风控决定及 assessmentId + evaluationId 证据坐标
     *         <li>不同检查点必须使用新的 commandKey；同一 commandKey 同内容重放返回原结果，改内容返回 409。</li>
     *         <li>检查点与来源事实不匹配、创建类版本不为 1 或 inputHash 不一致返回 400。</li>
     *         <li>正式运行 Release 与评估持久化链接入前一律返回 503，调用方必须 Fail-Closed，不得视为放行。</li>
     */
    @PostExchange("/evaluations")
    ManualPayoutEligibilityResponseBody evaluate(@Valid @RequestBody ManualPayoutEligibilityRequestBody request);
}
