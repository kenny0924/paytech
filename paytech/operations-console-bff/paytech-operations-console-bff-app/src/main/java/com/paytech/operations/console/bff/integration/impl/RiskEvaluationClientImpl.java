package com.paytech.operations.console.bff.integration.impl;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.paytech.operations.console.bff.integration.RiskEvaluationClient;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationExplanationResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationResponseBody;
import com.paytech.risk.decision.api.evaluation.RiskEvaluationQueryApi;
import com.paytech.risk.decision.api.evaluation.response.EvaluationResponseBody;

import lombok.AllArgsConstructor;

/**
 * 调用 Risk 评估查询接口并核对回执身份
 * <li>评估查询为内部直连，不携带 Gateway 身份头。</li>
 * <li>解释页的规则集详情读取与条件树组装属于页面聚合，由 RiskEvaluationExplanationProcessor 负责。</li>
 */
@Component
@AllArgsConstructor
public class RiskEvaluationClientImpl implements RiskEvaluationClient {
    private static final String PRIMARY = "PRIMARY";

    private final RiskEvaluationQueryApi api;

    /** 按评估 ID 查结论；回执评估 ID 必须与路径一致。 */
    @Override
    public RiskEvaluationResponseBody getEvaluation(String evaluationId) {
        EvaluationResponseBody response = api.getEvaluation(evaluationId);
        RiskControlResponsePolicy.require(response != null && Objects.equals(evaluationId, response.getEvaluationId()));
        return RiskEvaluationConverter.local(response);
    }

    /** 按业务身份查正式评估；回执风控点、业务身份必须与请求一致且为正式评估。 */
    @Override
    public RiskEvaluationResponseBody findEvaluation(String pointCode, String businessKey) {
        EvaluationResponseBody response = api.findEvaluation(pointCode, businessKey);
        RiskControlResponsePolicy.require(response != null && Objects.equals(pointCode, response.getPointCode())
            && Objects.equals(businessKey, response.getBusinessKey()) && PRIMARY.equals(response.getMode()));
        return RiskEvaluationConverter.local(response);
    }

    /** 查解释；回执评估 ID 必须与路径一致，解释本身失败时整页失败。 */
    @Override
    public RiskEvaluationExplanationResponseBody getExplanation(String evaluationId) {
        var response = api.getExplanation(evaluationId);
        RiskControlResponsePolicy.require(response != null && Objects.equals(evaluationId, response.getEvaluationId()));
        return RiskEvaluationConverter.local(response);
    }
}
