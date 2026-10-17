package com.paytech.risk.ext.rest.evaluation;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.web.bind.annotation.RestController;

import com.paytech.foundation.common.error.InvalidParameterException;
import com.paytech.risk.decision.api.evaluation.RiskEvaluationQueryApi;
import com.paytech.risk.decision.api.evaluation.response.*;
import com.paytech.risk.decision.domain.evaluation.EvaluationExplanationView;
import com.paytech.risk.decision.domain.evaluation.EvaluationView;
import com.paytech.risk.decision.service.processor.FindEvaluationProcessor;
import com.paytech.risk.decision.service.processor.GetEvaluationExplanationProcessor;
import com.paytech.risk.decision.service.processor.GetEvaluationProcessor;
import com.paytech.risk.decision.service.processor.request.EvaluationIdRequest;
import com.paytech.risk.decision.service.processor.request.FindEvaluationRequest;

import lombok.AllArgsConstructor;

/** 评估结论与解释查询的协议转换；ID 与时间以字符串传输，缺失或非法参数返回 400。 */
@RestController
@AllArgsConstructor
public class RiskEvaluationQueryController implements RiskEvaluationQueryApi {
    private final GetEvaluationProcessor getProcessor;
    private final FindEvaluationProcessor findProcessor;
    private final GetEvaluationExplanationProcessor explanationProcessor;

    /** 按评估 ID 查询结论。 */
    @Override
    public EvaluationResponseBody getEvaluation(String evaluationId) {
        return toBody(getProcessor.process(new EvaluationIdRequest(parseId(evaluationId))));
    }

    /** 按风控点与业务身份查询正式评估的结论。 */
    @Override
    public EvaluationResponseBody findEvaluation(String pointCode, String businessKey) {
        if (pointCode == null || businessKey == null) {
            throw new InvalidParameterException("pointCode 与 businessKey 必填");
        }
        return toBody(findProcessor.process(new FindEvaluationRequest(pointCode, businessKey)));
    }

    /** 查询评估解释。 */
    @Override
    public EvaluationExplanationResponseBody getExplanation(String evaluationId) {
        return toBody(explanationProcessor.process(new EvaluationIdRequest(parseId(evaluationId))));
    }

    /** 正整数十进制 ID；格式不符视为参数错误。 */
    static long parseId(String value) {
        if (value == null || !value.matches("[1-9][0-9]{0,18}")) {
            throw new InvalidParameterException("评估 ID 格式不正确");
        }
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException overflow) {
            throw new InvalidParameterException("评估 ID 格式不正确", overflow);
        }
    }

    /** 结论转换。 */
    static EvaluationResponseBody toBody(EvaluationView view) {
        EvaluationResponseBody body = new EvaluationResponseBody();
        body.setEvaluationId(Long.toString(view.evaluationId()));
        body.setPointCode(view.pointCode());
        body.setBusinessKey(view.businessKey());
        body.setMode(view.mode().getCode());
        body.setResult(view.result());
        body.setReasonCode(view.reasonCode());
        body.setReasonMessage(view.reasonMessage());
        body.setOutputs(Map.copyOf(view.outputs()));
        body.setFailureCode(view.failureCode());
        body.setReleaseId(Long.toString(view.releaseId()));
        body.setEnvironment(view.environment().getCode());
        body.setFlowId(Long.toString(view.flowId()));
        body.setFlowCode(view.flowCode());
        body.setFlowVersionId(Long.toString(view.flowVersionId()));
        body.setFlowVersionNo(view.flowVersionNo());
        body.setEvaluationTime(text(view.evaluationTime()));
        body.setElapsedMillis(view.elapsedMillis());
        body.setBaselineEvaluationId(text(view.baselineEvaluationId()));
        body.setShadowRunId(text(view.shadowRunId()));
        body.setPathAvailable(view.pathAvailable());
        return body;
    }

    /** 解释转换；输入保持合同字段顺序，未提供的字段为 null。 */
    static EvaluationExplanationResponseBody toBody(EvaluationExplanationView view) {
        EvaluationExplanationResponseBody body = new EvaluationExplanationResponseBody();
        body.setEvaluationId(Long.toString(view.evaluationId()));
        body.setInputs(new LinkedHashMap<>(view.inputs()));
        body.setPathAvailable(view.pathAvailable());
        body.setPath(view.path().stream().map(RiskEvaluationQueryController::toBody).toList());
        body.setRules(view.rules().stream().map(RiskEvaluationQueryController::toBody).toList());
        body.setVariables(view.variables().stream().map(RiskEvaluationQueryController::toBody).toList());
        body.setShadows(view.shadows().stream().map(RiskEvaluationQueryController::toBody).toList());
        return body;
    }

    /** 节点转换。 */
    static EvaluationPathStep toBody(EvaluationExplanationView.Step step) {
        EvaluationPathStep body = new EvaluationPathStep();
        body.setStepNo(step.stepNo());
        body.setNodeKey(step.nodeKey());
        body.setNodeType(step.nodeType());
        body.setNodeName(step.nodeName());
        body.setRuleSetVersionId(text(step.ruleSetVersionId()));
        body.setRuleSetCode(step.ruleSetCode());
        body.setRuleSetVersionNo(step.ruleSetVersionNo());
        body.setMatched(step.matched());
        body.setDecision(step.decision());
        body.setReused(step.reused());
        body.setExitEdgeKey(step.exitEdgeKey());
        body.setExitEdgeName(step.exitEdgeName());
        return body;
    }

    /** 规则转换。 */
    static EvaluationRuleItem toBody(EvaluationExplanationView.Rule rule) {
        EvaluationRuleItem body = new EvaluationRuleItem();
        body.setRuleSetVersionId(Long.toString(rule.ruleSetVersionId()));
        body.setRuleId(Long.toString(rule.ruleId()));
        body.setRuleCode(rule.ruleCode());
        body.setRuleName(rule.ruleName());
        body.setPriorityNo(rule.priorityNo());
        body.setMatched(rule.matched());
        body.setTerminal(rule.terminal());
        body.setAction(rule.action());
        body.setPredicates(rule.predicates().stream().map(predicate -> {
            EvaluationPredicateItem item = new EvaluationPredicateItem();
            item.setNodeId(Long.toString(predicate.nodeId()));
            item.setResult(predicate.result());
            return item;
        }).toList());
        return body;
    }

    /** 变量转换。 */
    static EvaluationVariableItem toBody(EvaluationExplanationView.Variable variable) {
        EvaluationVariableItem body = new EvaluationVariableItem();
        body.setVariableCode(variable.variableCode());
        body.setVariableName(variable.variableName());
        body.setSourceType(variable.sourceType());
        body.setValueType(variable.valueType());
        body.setValue(variable.value());
        body.setValues(variable.values());
        body.setValueBasis(variable.valueBasis());
        body.setTechnicalStatus(variable.technicalStatus());
        body.setFailureCode(variable.failureCode());
        body.setElapsedMillis(variable.elapsedMillis());
        return body;
    }

    /** 影子比较转换。 */
    static EvaluationShadowItem toBody(EvaluationExplanationView.Shadow shadow) {
        EvaluationShadowItem body = new EvaluationShadowItem();
        body.setEvaluationId(Long.toString(shadow.evaluationId()));
        body.setShadowRunId(Long.toString(shadow.shadowRunId()));
        body.setResult(shadow.result());
        body.setDecisionChanged(shadow.decisionChanged());
        body.setOutputChanged(shadow.outputChanged());
        body.setSeverity(shadow.severity());
        return body;
    }

    /** 可空 ID 的十进制文本。 */
    static String text(Long value) {
        return value == null ? null : value.toString();
    }

    /** UTC ISO-8601 时间文本。 */
    static String text(Instant time) {
        return time == null ? null : time.toString();
    }
}
