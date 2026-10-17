package com.paytech.operations.console.bff.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.ConditionNodeRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.OperandRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.RuleRequestBody;
import com.paytech.operations.console.bff.service.model.RiskRuleSetModels.RuleSetDetailResponseBody;
import com.paytech.operations.console.bff.service.model.RiskVariableModels.VariableSummary;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationConditionNode;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationExplanationResponseBody;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationOperand;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationPredicateItem;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationRuleItem;
import com.paytech.operations.console.bff.service.model.riskevaluation.RiskEvaluationVariableItem;

/**
 * 把规则集版本详情中的条件结构与 Risk 谓词轨迹合并为页面条件树；纯计算，不访问上游
 * <li>规则按 ruleCode 在所属规则集版本内对应；谓词 nodeId 等于条件节点 nodeKey。</li>
 * <li>详情缺失、规则找不到、谓词节点在详情中不存在或不是谓词：该规则 conditionAvailable=false、condition=null。</li>
 */
public final class RiskEvaluationExplanationAssembler {
    private static final String GROUP = "GROUP";
    private static final String PREDICATE = "PREDICATE";
    private static final String VARIABLE = "VARIABLE";

    private RiskEvaluationExplanationAssembler() { }

    /**
     * 为解释中的每条规则补充条件树
     *
     * @param explanation 已转换的解释页面模型，就地补充 conditionAvailable 与 condition
     * @param ruleSets 可用的规则集版本详情，键为规则集版本 ID；读取失败的版本不在其中
     */
    public static void assemble(RiskEvaluationExplanationResponseBody explanation, Map<String, RuleSetDetailResponseBody> ruleSets) {
        Map<String, String> evaluationNames = evaluationVariableNames(explanation.getVariables());
        for (RiskEvaluationRuleItem rule : explanation.getRules()) {
            RiskEvaluationConditionNode condition = condition(rule, ruleSets.get(rule.getRuleSetVersionId()), evaluationNames);
            rule.setConditionAvailable(condition != null);
            rule.setCondition(condition);
        }
    }

    /** 组装一条规则的条件树；任一对应关系缺失时返回 null。 */
    static RiskEvaluationConditionNode condition(RiskEvaluationRuleItem rule, RuleSetDetailResponseBody detail,
        Map<String, String> evaluationNames) {
        if (detail == null) {
            return null;
        }
        RuleRequestBody definition = detail.rules().stream()
            .filter(candidate -> Objects.equals(candidate.ruleCode(), rule.getRuleCode())).findFirst().orElse(null);
        if (definition == null || definition.conditionNodes().isEmpty()) {
            return null;
        }
        Map<String, Boolean> results = predicateResults(rule.getPredicates(), definition.conditionNodes());
        if (results == null) {
            return null;
        }
        Map<String, String> names = variableNames(evaluationNames, detail.currentVariables());
        return node(definition.conditionNodes().getFirst(), children(definition.conditionNodes()), results, names);
    }

    /** 谓词轨迹按 nodeId 建索引；有节点在详情中不存在或不是谓词时返回 null（对不上）。 */
    static Map<String, Boolean> predicateResults(List<RiskEvaluationPredicateItem> predicates,
        List<ConditionNodeRequestBody> nodes) {
        Map<String, String> types = new HashMap<>();
        for (ConditionNodeRequestBody node : nodes) {
            types.put(node.nodeKey(), node.nodeType());
        }
        Map<String, Boolean> results = new HashMap<>();
        for (RiskEvaluationPredicateItem predicate : predicates) {
            if (!PREDICATE.equals(types.get(predicate.getNodeId()))) {
                return null;
            }
            results.put(predicate.getNodeId(), predicate.isResult());
        }
        return results;
    }

    /** 按父节点键分组子节点，保持详情中的顺序（Risk 已按同级 sequenceNo 排序）。 */
    static Map<String, List<ConditionNodeRequestBody>> children(List<ConditionNodeRequestBody> nodes) {
        Map<String, List<ConditionNodeRequestBody>> children = new HashMap<>();
        for (ConditionNodeRequestBody node : nodes) {
            if (node.parentNodeKey() != null) {
                children.computeIfAbsent(node.parentNodeKey(), ignored -> new ArrayList<>()).add(node);
            }
        }
        return children;
    }

    /** 递归组装节点；谓词结果取轨迹，未求值为 null；分组结果由子节点计算。 */
    static RiskEvaluationConditionNode node(ConditionNodeRequestBody source, Map<String, List<ConditionNodeRequestBody>> children,
        Map<String, Boolean> results, Map<String, String> names) {
        var node = new RiskEvaluationConditionNode();
        node.setNodeKey(source.nodeKey());
        node.setNodeType(source.nodeType());
        node.setGroupOperator(source.groupOperator());
        node.setComparisonOperator(source.comparisonOperator());
        node.setOperands(source.operands().stream().map(operand -> operand(operand, names)).toList());
        node.setChildren(children.getOrDefault(source.nodeKey(), List.of()).stream()
            .map(child -> node(child, children, results, names)).toList());
        if (GROUP.equals(source.nodeType())) {
            node.setResult(groupResult(source.groupOperator(), node.getChildren()));
        } else {
            node.setResult(results.get(source.nodeKey()));
        }
        return node;
    }

    /**
     * 计算分组结果
     * <li>任一子节点无结果（未求值）则为 null，不按短路推断；没有子节点同样为 null。</li>
     * <li>NOT 只对唯一子节点取反；子节点数不为 1 时为 null，不猜测语义。</li>
     */
    static Boolean groupResult(String operator, List<RiskEvaluationConditionNode> children) {
        if (children.isEmpty() || children.stream().anyMatch(child -> child.getResult() == null)) {
            return null;
        }
        return switch (operator) {
            case "AND" -> children.stream().allMatch(RiskEvaluationConditionNode::getResult);
            case "OR" -> children.stream().anyMatch(RiskEvaluationConditionNode::getResult);
            case "NOT" -> children.size() == 1 ? !children.getFirst().getResult() : null;
            default -> null;
        };
    }

    /** 转换操作数；变量操作数补充变量名称。 */
    static RiskEvaluationOperand operand(OperandRequestBody source, Map<String, String> names) {
        var operand = new RiskEvaluationOperand();
        operand.setOperandRole(source.operandRole());
        operand.setValueSequence(source.valueSequence());
        operand.setSourceType(source.sourceType());
        operand.setValueType(source.valueType());
        operand.setFieldCode(source.fieldCode());
        operand.setVariableCode(source.variableCode());
        operand.setVariableName(VARIABLE.equals(source.sourceType()) ? names.get(source.variableCode()) : null);
        operand.setLiteralValue(source.literalValue());
        operand.setNumericUnit(source.numericUnit());
        operand.setCurrencyCode(source.currencyCode());
        operand.setCurrencyVariableCode(source.currencyVariableCode());
        return operand;
    }

    /** 解释变量表中的变量名称（编码 → 名称），忽略无名称的项。 */
    static Map<String, String> evaluationVariableNames(List<RiskEvaluationVariableItem> variables) {
        Map<String, String> names = new HashMap<>();
        for (RiskEvaluationVariableItem variable : variables) {
            if (variable.getVariableName() != null) {
                names.putIfAbsent(variable.getVariableCode(), variable.getVariableName());
            }
        }
        return names;
    }

    /** 变量名称优先取解释的变量表，缺失再取该规则集详情的当前变量。 */
    static Map<String, String> variableNames(Map<String, String> evaluationNames, List<VariableSummary> currentVariables) {
        Map<String, String> names = new LinkedHashMap<>();
        for (VariableSummary variable : currentVariables) {
            if (variable.variableName() != null) {
                names.put(variable.variableCode(), variable.variableName());
            }
        }
        names.putAll(evaluationNames);
        return names;
    }
}
