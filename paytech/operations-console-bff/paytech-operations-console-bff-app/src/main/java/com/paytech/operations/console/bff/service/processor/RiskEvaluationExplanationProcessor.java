package com.paytech.operations.console.bff.service.processor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.foundation.common.error.FailException;
import com.paytech.foundation.processor.AbstractProcessor;
import com.paytech.operations.console.bff.integration.RiskDecisionConfigurationClient;
import com.paytech.operations.console.bff.integration.RiskEvaluationClient;
import com.paytech.operations.console.bff.service.RiskEvaluationExplanationAssembler;
import com.paytech.operations.console.bff.service.model.OperatorContext;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.RuleSetDetailResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationExplanationResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationRuleItem;
import com.paytech.operations.console.bff.service.processor.request.RiskEvaluationExplanationRequest;

import lombok.AllArgsConstructor;

/**
 * 风控评估解释页：读取 Risk 评估解释，再按规则集版本读取规则集详情，为每条规则组装条件树
 * <li>解释本身失败时整页失败；解释为内部直连查询，规则集详情沿用操作者身份。</li>
 * <li>条件树只是展示增强：规则集详情的任何失败（不存在、上游 403/400、超时、不可用、响应不符合协议）只让引用它的规则
 * 显示“条件不可用”，整页仍返回，避免一个规则集版本的问题挡住排查；操作者会话或页面权限失效已在入站 PLT-I02 拒绝。</li>
 * <li>两类调用同属 Risk，按规则集版本首次出现的顺序逐个读取，同次请求内去重，不并发、不缓存。</li>
 * <li>降级日志只记录规则集版本 ID 与失败类别，不记录响应内容。</li>
 */
@Component
@AllArgsConstructor
public class RiskEvaluationExplanationProcessor
    extends AbstractProcessor<RiskEvaluationExplanationRequest, RiskEvaluationExplanationResponseBody> {
    private static final Logger LOG = LoggerFactory.getLogger(RiskEvaluationExplanationProcessor.class);

    private final RiskEvaluationClient evaluations;
    private final RiskDecisionConfigurationClient ruleSets;

    @Override
    protected RiskEvaluationExplanationResponseBody processRequest(RiskEvaluationExplanationRequest request) {
        var explanation = evaluations.getExplanation(request.getEvaluationId());
        RiskEvaluationExplanationAssembler.assemble(explanation, ruleSets(request.getOperator(), explanation.getRules()));
        return explanation;
    }

    /** 对解释中出现的每个规则集版本读取一次详情；读取失败的版本不放入结果。 */
    Map<String, RuleSetDetailResponseBody> ruleSets(OperatorContext operator, List<RiskEvaluationRuleItem> rules) {
        Map<String, RuleSetDetailResponseBody> details = new LinkedHashMap<>();
        for (String ruleSetVersionId : rules.stream().map(RiskEvaluationRuleItem::getRuleSetVersionId).distinct().toList()) {
            RuleSetDetailResponseBody detail = ruleSet(operator, ruleSetVersionId);
            if (detail != null) {
                details.put(ruleSetVersionId, detail);
            }
        }
        return details;
    }

    /** 读取一个规则集版本详情；失败只降级引用它的规则，返回 null。 */
    RuleSetDetailResponseBody ruleSet(OperatorContext operator, String ruleSetVersionId) {
        try {
            return ruleSets.ruleSet(operator, ruleSetVersionId);
        } catch (FailException | ErrorException failure) {
            LOG.warn("规则集版本详情不可用，相关规则的条件树降级: ruleSetVersionId={}, failure={}", ruleSetVersionId,
                failure.getClass().getSimpleName());
            return null;
        }
    }
}
