package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.authoring.RuleActionConfigurationCatalog;
import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.*;
import com.paytech.risk.decision.domain.rule.DecisionRuleSetAggregate;
import com.paytech.risk.decision.domainservice.service.RiskVariableException;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/** 新规则内容的纯转换；变量当前事实由调用方同一事务锁定后传入。 */
public final class VariableRuleConfiguration {
    public static final String CONTENT_FORMAT = "RISK_RULE_SET_VARIABLE_CONFIGURATION";
    public static final String OPERAND_FORMAT = "VARIABLE_REFERENCE";

    private VariableRuleConfiguration() { }

    /** 收集直接引用及金额字面量的币种引用，依赖闭包由变量库服务展开。 */
    public static Set<String> references(NormalizedRuleSet value) {
        Set<String> codes = new TreeSet<>();
        for (NormalizedRule rule : value.rules()) {
            references(rule.condition(), codes);
            if (rule.additionalActions() != null) rule.additionalActions().forEach(a -> add(codes, a.subjectVariableCode()));
        }
        return Set.copyOf(codes);
    }

    /** 历史查询从保存的结构取引用，不把当前值写入历史。 */
    public static Set<String> references(DecisionRuleSetAggregate value) {
        Set<String> codes = new TreeSet<>();
        for (var rule : value.rules()) {
            rule.conditionNodes().forEach(node -> node.operands().forEach(o -> {
                add(codes, o.variableCode()); add(codes, o.currencyVariableCode());
            }));
            rule.additionalActions().forEach(a -> add(codes, a.configuration().subjectVariableCode()));
        }
        return Set.copyOf(codes);
    }

    /** 新规则的当前资格、两侧类型和动作主体必须同时通过验证。 */
    public static NormalizedRuleSet validate(NormalizedRuleSet value, VariableConditionValidator.Context context) {
        List<NormalizedRule> rules = value.rules().stream().map(rule -> {
            rule.additionalActions().forEach(a -> subject(a, context));
            return new NormalizedRule(rule.ruleCode(), rule.ruleName(), rule.priorityNo(), rule.enabled(),
                rule.reasonCode(), condition(rule.condition(), context), rule.action(), rule.reasonMessage(), rule.additionalActions());
        }).toList();
        return new NormalizedRuleSet(value.pointCode(), value.ruleSetCode(), value.ruleSetName(), value.baseRuleSetVersionId(),
            value.baseContentHash(), value.changeReason(), rules, OPERAND_FORMAT);
    }

    /** 流程引用准确规则版本时，重新检查其当前变量资格，但不改写该版本。 */
    public static void validateReference(DecisionRuleSetAggregate aggregate, VariableConditionValidator.Context context) {
        if (!CONTENT_FORMAT.equals(aggregate.ruleSetProjection().contentFormat())) return;
        for (var rule : aggregate.rules()) {
            rule.conditionNodes().stream().filter(n -> n.nodeType() == NodeType.PREDICATE).forEach(n ->
                VariableConditionValidator.validate(n.comparisonOperator(), n.operands().stream().map(o -> o.variableOperand()).toList(), context));
            rule.additionalActions().forEach(a -> subject(a.configuration(), context));
        }
    }

    /** 组结构已由 AST 入口验证，只在每个谓词应用共用变量校验。 */
    private static NormalizedCondition condition(NormalizedCondition value, VariableConditionValidator.Context context) {
        if (value instanceof NormalizedGroup group) {
            return new NormalizedGroup(group.groupOperator(), group.children().stream().map(child -> condition(child, context)).toList());
        }
        var predicate = (NormalizedPredicate) value;
        return new NormalizedPredicate(predicate.comparisonOperator(), VariableConditionValidator.validate(
            predicate.comparisonOperator(), predicate.operands().stream().map(NormalizedOperand::variableOperand).toList(), context)
            .stream().map(o -> new NormalizedOperand(o.operandRole(), o.valueSequence(), o.sourceType(), o.fieldCode(),
                o.valueType(), o.literalValue(), o.variableCode(), o.numericUnit(), o.currencyCode(), o.currencyVariableCode())).toList());
    }

    /** 动作能力要求真实主体语义，任何映射到该合同的有效变量别名均可使用。 */
    private static void subject(AdditionalAction action, VariableConditionValidator.Context context) {
        var binding = RuleActionConfigurationCatalog.requireTarget(action.targetBindingCode());
        var entry = context.variables().get(action.subjectVariableCode());
        if (entry == null) throw new RiskVariableException(RiskVariableException.Reason.VARIABLE_NOT_FOUND, "动作主体变量不存在");
        var d = entry.definition();
        if (entry.status() != VariableStatus.ACTIVE) throw new RiskVariableException(RiskVariableException.Reason.VARIABLE_DISABLED, "动作主体变量未启用");
        var contract = DecisionSourceContracts.find(d);
        var field = contract == null ? null : contract.fields().stream().filter(f -> f.fieldKey().equals(d.sourceField())).findFirst().orElse(null);
        if (d.sourceType() != VariableSource.INPUT || d.valueType() != DecisionValueType.STRING
            || !context.pointCode().equals(d.pointCode()) || !d.usages().contains(VariableUsage.ACTION_PARAMETER)
            || field == null || !(binding.subjectType() + "_ID").equals(field.semanticRole())) {
            throw new RiskVariableException(RiskVariableException.Reason.VARIABLE_SCOPE_MISMATCH, "动作主体变量与目标能力不一致");
        }
        VariableDefinitionValidator.dependencies(d, context.variables());
    }

    private static void references(NormalizedCondition value, Set<String> codes) {
        if (value instanceof NormalizedGroup group) group.children().forEach(child -> references(child, codes));
        else ((NormalizedPredicate) value).operands().forEach(o -> { add(codes, o.variableCode()); add(codes, o.currencyVariableCode()); });
    }

    private static void add(Set<String> codes, String code) { if (code != null) codes.add(code); }
}
