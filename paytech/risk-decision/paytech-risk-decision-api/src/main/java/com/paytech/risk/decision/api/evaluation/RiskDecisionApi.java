package com.paytech.risk.decision.api.evaluation;

import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

import com.paytech.risk.decision.api.evaluation.request.EvaluateDecisionRequestBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluateDecisionResponseBody;

import jakarta.validation.Valid;

/**
 * 同步风控点的统一评估合同；内部服务直连，不经 Gateway，HTTP 请求幂等由业务身份重放保证。
 * <li>按 pointCode 区分风控点，inputs 按该点登记的输入合同校验；新增风控点不新增接口。</li>
 * <li>授权结果、拒付等事件经 MQ，Client 监控等由 Risk 定时任务执行，不走本接口。</li>
 */
@HttpExchange("/risk-decisions/v1")
public interface RiskDecisionApi {

    /**
     * 评估一次业务请求
     *
     * @param requestBody
     *            风控点、输入合同版本、业务身份与输入
     * @return 评估结论与附加输出
     *         <li>同一风控点同一业务身份只做一次首次评估；内容一致的重放返回原结论，内容不一致返回 409。</li>
     *         <li>未登记的风控点或合同版本返回 400；输入缺字段、未知字段或格式不符返回 422 并指出字段。</li>
     *         <li>本环境没有生效发布返回 503 RISK_DECISION_NOT_CONFIGURED，不写评估。</li>
     *         <li>执行失败或超时返回 200 与 FAILED / TIMED_OUT，已落库，调用方不得放行。</li>
     */
    @PostExchange("/evaluations")
    EvaluateDecisionResponseBody evaluate(@Valid @RequestBody EvaluateDecisionRequestBody requestBody);
}
