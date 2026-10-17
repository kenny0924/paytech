package com.paytech.operations.console.bff.integration.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.operations.console.bff.integration.RiskEvaluationClient;
import com.paytech.operations.console.bff.service.RiskEvaluationExplanationAssembler;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.RuleSetDetailResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationExplanationResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationRuleItem;
import com.paytech.risk.decision.api.DecisionRuleSetManagementApi;
import com.paytech.risk.decision.api.evaluation.RiskEvaluationQueryApi;
import com.paytech.risk.decision.api.evaluation.response.EvaluationResponseBody;

import lombok.AllArgsConstructor;

/**
 * 调用 Risk 评估查询接口并核对回执身份；解释再按规则集版本读取同一 Owner 的规则集详情组装条件树
 * <li>评估查询为内部直连，不携带 Gateway 身份头；规则集详情沿用操作者身份头。</li>
 * <li>两类调用都属于 Risk 一个 Owner，读取顺序固定、不并发，BFF 不缓存详情。</li>
 */
@Component
@AllArgsConstructor
public class RiskEvaluationClientImpl implements RiskEvaluationClient {
    private static final Logger LOG = LoggerFactory.getLogger(RiskEvaluationClientImpl.class);
    private static final String PRIMARY = "PRIMARY";

    private final RiskEvaluationQueryApi api;
    private final DecisionRuleSetManagementApi ruleSetApi;

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

    /** 查解释并组装条件树；回执评估 ID 必须与路径一致，解释本身失败时整页失败。 */
    @Override
    public RiskEvaluationExplanationResponseBody getExplanation(OperatorContext context, String evaluationId) {
        var response = api.getExplanation(evaluationId);
        RiskControlResponsePolicy.require(response != null && Objects.equals(evaluationId, response.getEvaluationId()));
        var explanation = RiskEvaluationConverter.local(response);
        RiskEvaluationExplanationAssembler.assemble(explanation, ruleSets(context, explanation.getRules()));
        return explanation;
    }

    /** 对解释中出现的每个规则集版本读取一次详情（同次请求内去重）；读取失败的版本不放入结果。 */
    private Map<String, RuleSetDetailResponseBody> ruleSets(OperatorContext context, List<RiskEvaluationRuleItem> rules) {
        Map<String, RuleSetDetailResponseBody> details = new LinkedHashMap<>();
        for (String ruleSetVersionId : rules.stream().map(RiskEvaluationRuleItem::getRuleSetVersionId).distinct().toList()) {
            RuleSetDetailResponseBody detail = ruleSet(context, ruleSetVersionId);
            if (detail != null) {
                details.put(ruleSetVersionId, detail);
            }
        }
        return details;
    }

    /**
     * 读取并严格校验一个规则集版本详情；失败返回 null
     * <li>取舍：条件树只是解释的展示增强，结论、路径、变量与输入已由解释接口授权返回。详情的任何失败
     * （不存在、上游 403/400、超时、不可用、响应不符合协议）都只让引用它的规则显示“条件不可用”，整页仍返回 200，
     * 避免一个规则集版本的问题挡住排查。操作者会话或页面权限的失效已在入站 PLT-I02 拒绝，不会走到这里；
     * 此处若遇到 Risk 对详情的授权拒绝，同样只降级该规则而不暴露详情内容。</li>
     * <li>只记录规则集版本 ID 与失败类别，不记录响应内容。</li>
     */
    private RuleSetDetailResponseBody ruleSet(OperatorContext context, String ruleSetVersionId) {
        try {
            var response = ruleSetApi.getRuleSet(context.accountId(), context.sessionId(), ruleSetVersionId);
            return RiskDecisionConfigurationClientImpl.ruleSetDetail(response, ruleSetVersionId);
        } catch (FailException | ErrorException failure) {
            LOG.warn("规则集版本详情不可用，相关规则的条件树降级: ruleSetVersionId={}, failure={}", ruleSetVersionId,
                failure.getClass().getSimpleName());
            return null;
        }
    }
}
