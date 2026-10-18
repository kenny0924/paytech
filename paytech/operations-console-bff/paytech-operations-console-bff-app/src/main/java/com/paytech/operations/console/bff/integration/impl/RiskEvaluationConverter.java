package com.paytech.operations.console.bff.integration.impl;

import java.util.List;
import java.util.function.Function;

import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationExplanationResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationPathStep;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationPredicateItem;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationRuleItem;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationShadowItem;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationVariableItem;
import com.paytech.risk.decision.api.evaluation.response.EvaluationExplanationResponseBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluationPathStep;
import com.paytech.risk.decision.api.evaluation.response.EvaluationPredicateItem;
import com.paytech.risk.decision.api.evaluation.response.EvaluationResponseBody;
import com.paytech.risk.decision.api.evaluation.response.EvaluationRuleItem;
import com.paytech.risk.decision.api.evaluation.response.EvaluationShadowItem;
import com.paytech.risk.decision.api.evaluation.response.EvaluationVariableItem;

/** 评估结论与解释的 Owner 合同与页面合同之间的逐字段转换；条件树由组装器另行补充。 */
final class RiskEvaluationConverter {
    private RiskEvaluationConverter() { }

    /** 转换评估结论。 */
    static RiskEvaluationResponseBody local(EvaluationResponseBody r) {
        var value = new RiskEvaluationResponseBody();
        value.setEvaluationId(r.getEvaluationId());
        value.setPointCode(r.getPointCode());
        value.setBusinessKey(r.getBusinessKey());
        value.setMode(r.getMode());
        value.setResult(r.getResult());
        value.setReasonCode(r.getReasonCode());
        value.setReasonMessage(r.getReasonMessage());
        value.setOutputs(r.getOutputs());
        value.setFailureCode(r.getFailureCode());
        value.setReleaseId(r.getReleaseId());
        value.setEnvironment(r.getEnvironment());
        value.setFlowId(r.getFlowId());
        value.setFlowCode(r.getFlowCode());
        value.setFlowVersionId(r.getFlowVersionId());
        value.setFlowVersionNo(r.getFlowVersionNo());
        value.setEvaluationTime(r.getEvaluationTime());
        value.setElapsedMillis(r.getElapsedMillis());
        value.setBaselineEvaluationId(r.getBaselineEvaluationId());
        value.setShadowRunId(r.getShadowRunId());
        value.setPathAvailable(r.isPathAvailable());
        return value;
    }

    /** 转换评估解释；规则的条件树尚未组装（conditionAvailable=false）。 */
    static RiskEvaluationExplanationResponseBody local(EvaluationExplanationResponseBody r) {
        var value = new RiskEvaluationExplanationResponseBody();
        value.setEvaluationId(r.getEvaluationId());
        value.setInputs(r.getInputs());
        value.setPathAvailable(r.isPathAvailable());
        value.setPath(map(r.getPath(), RiskEvaluationConverter::local));
        value.setRules(map(r.getRules(), RiskEvaluationConverter::local));
        value.setVariables(map(r.getVariables(), RiskEvaluationConverter::local));
        value.setShadows(map(r.getShadows(), RiskEvaluationConverter::local));
        return value;
    }

    /** 转换流程路径节点。 */
    static RiskEvaluationPathStep local(EvaluationPathStep r) {
        var value = new RiskEvaluationPathStep();
        value.setStepNo(r.getStepNo());
        value.setNodeKey(r.getNodeKey());
        value.setNodeType(r.getNodeType());
        value.setNodeName(r.getNodeName());
        value.setRuleSetVersionId(r.getRuleSetVersionId());
        value.setRuleSetCode(r.getRuleSetCode());
        value.setRuleSetVersionNo(r.getRuleSetVersionNo());
        value.setMatched(r.getMatched());
        value.setDecision(r.getDecision());
        value.setReused(r.getReused());
        value.setExitEdgeKey(r.getExitEdgeKey());
        value.setExitEdgeName(r.getExitEdgeName());
        return value;
    }

    /** 转换执行过的规则与谓词轨迹。 */
    static RiskEvaluationRuleItem local(EvaluationRuleItem r) {
        var value = new RiskEvaluationRuleItem();
        value.setRuleSetVersionId(r.getRuleSetVersionId());
        value.setRuleId(r.getRuleId());
        value.setRuleCode(r.getRuleCode());
        value.setRuleName(r.getRuleName());
        value.setPriorityNo(r.getPriorityNo());
        value.setMatched(r.isMatched());
        value.setTerminal(r.isTerminal());
        value.setAction(r.getAction());
        value.setPredicates(map(r.getPredicates(), RiskEvaluationConverter::local));
        return value;
    }

    /** 转换谓词结果。 */
    static RiskEvaluationPredicateItem local(EvaluationPredicateItem r) {
        var value = new RiskEvaluationPredicateItem();
        value.setNodeId(r.getNodeId());
        value.setResult(r.isResult());
        return value;
    }

    /** 转换变量取值。 */
    static RiskEvaluationVariableItem local(EvaluationVariableItem r) {
        var value = new RiskEvaluationVariableItem();
        value.setVariableCode(r.getVariableCode());
        value.setVariableName(r.getVariableName());
        value.setSourceType(r.getSourceType());
        value.setValueType(r.getValueType());
        value.setValue(r.getValue());
        value.setValues(r.getValues());
        value.setValueBasis(r.getValueBasis());
        value.setTechnicalStatus(r.getTechnicalStatus());
        value.setFailureCode(r.getFailureCode());
        value.setElapsedMillis(r.getElapsedMillis());
        return value;
    }

    /** 转换影子评估比较。 */
    static RiskEvaluationShadowItem local(EvaluationShadowItem r) {
        var value = new RiskEvaluationShadowItem();
        value.setEvaluationId(r.getEvaluationId());
        value.setShadowRunId(r.getShadowRunId());
        value.setResult(r.getResult());
        value.setDecisionChanged(r.isDecisionChanged());
        value.setOutputChanged(r.isOutputChanged());
        value.setSeverity(r.getSeverity());
        return value;
    }

    /** 逐项转换列表；响应形状已由 Risk 成功响应读取器保证为非空数组。 */
    private static <S, T> List<T> map(List<S> values, Function<S, T> converter) {
        return values.stream().map(converter).toList();
    }
}
