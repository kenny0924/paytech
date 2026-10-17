package com.paytech.risk.decision.domain.rule;

import com.paytech.risk.decision.domain.authoring.DecisionActionRegistry.ActionKey;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.GroupOperator;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import java.util.ArrayList;
import com.paytech.risk.decision.domain.enums.NumericUnit;
import com.paytech.risk.decision.domain.variable.VariableOperand;
import java.util.Collections;
import java.util.List;

/** Rule AST 的纯验证与规范化边界。 */
public interface RuleAstValidator {

    NormalizedRuleSet validateAndNormalize(RuleSetDraft draft);

    /** 复用现有字段目录校验独立条件树，供配置连线使用；不创建规则或执行动作。 */
    NormalizedCondition validateCondition(List<NodeDraft> nodes);

    String normalizeDecimal(String raw);

    String normalizeUtcMicrosecond(String raw);

    String normalizeDurationMillis(String raw);

    sealed interface LiteralValue permits NullLiteral, StringLiteral, BooleanLiteral { }

    enum NullLiteral implements LiteralValue { INSTANCE }

    record StringLiteral(String value) implements LiteralValue { }

    record BooleanLiteral(boolean value) implements LiteralValue { }

    record OperandDraft(OperandRole operandRole, int valueSequence, OperandSource sourceType, String fieldCode,
        DecisionValueType valueType, LiteralValue literalValue, String variableCode, NumericUnit numericUnit,
        String currencyCode, String currencyVariableCode) {
        /** 旧合同保留原六字段，新增元数据为空。 */
        public OperandDraft(OperandRole operandRole, int valueSequence, OperandSource sourceType, String fieldCode,
            DecisionValueType valueType, LiteralValue literalValue) {
            this(operandRole, valueSequence, sourceType, fieldCode, valueType, literalValue, null, null, null, null);
        }
        /** 转换为规则与流程共同的配置操作数。 */
        public VariableOperand variableOperand() {
            return new VariableOperand(operandRole, valueSequence, sourceType, variableCode, fieldCode, valueType,
                literalValue, numericUnit, currencyCode, currencyVariableCode);
        }
    }

    record NodeDraft(String nodeKey, String parentNodeKey, int sequenceNo, NodeType nodeType,
                     GroupOperator groupOperator, ComparisonOperator comparisonOperator,
                     List<OperandDraft> operands) {
        public NodeDraft {
            operands = operands == null ? null : Collections.unmodifiableList(new ArrayList<>(operands));
        }
    }

    record RuleDraft(String ruleCode, String ruleName, int priorityNo, boolean enabled, String reasonCode,
                     List<NodeDraft> conditionNodes, ActionKey action, String reasonMessage,
                     List<AdditionalAction> additionalActions) {
        public RuleDraft(String ruleCode, String ruleName, int priorityNo, boolean enabled, String reasonCode,
            List<NodeDraft> conditionNodes, ActionKey action) {
            this(ruleCode, ruleName, priorityNo, enabled, reasonCode, conditionNodes, action, null, null);
        }

        public RuleDraft {
            conditionNodes = conditionNodes == null ? null
                : Collections.unmodifiableList(new ArrayList<>(conditionNodes));
        }
    }

    record RuleSetDraft(String pointCode, String ruleSetCode, String ruleSetName, Long baseRuleSetVersionId,
                        String baseContentHash, String changeReason, List<RuleDraft> rules, String operandFormat) {
        /** 保留原请求/历史构造方式，旧格式由原 Hash Reader 解释。 */
        public RuleSetDraft(String pointCode, String ruleSetCode, String ruleSetName, Long baseRuleSetVersionId,
            String baseContentHash, String changeReason, List<RuleDraft> rules) {
            this(pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId, baseContentHash, changeReason, rules, null);
        }
        public RuleSetDraft {
            rules = rules == null ? null : Collections.unmodifiableList(new ArrayList<>(rules));
        }
    }

    sealed interface NormalizedCondition permits NormalizedGroup, NormalizedPredicate { }

    record NormalizedGroup(GroupOperator groupOperator, List<NormalizedCondition> children)
        implements NormalizedCondition {
        public NormalizedGroup { children = List.copyOf(children); }
    }

    record NormalizedPredicate(ComparisonOperator comparisonOperator, List<NormalizedOperand> operands)
        implements NormalizedCondition {
        public NormalizedPredicate { operands = List.copyOf(operands); }
    }

    record NormalizedOperand(OperandRole operandRole, int valueSequence, OperandSource sourceType, String fieldCode,
        DecisionValueType valueType, LiteralValue literalValue, String variableCode, NumericUnit numericUnit,
        String currencyCode, String currencyVariableCode) {
        /** 旧合同保留原六字段，新增元数据为空。 */
        public NormalizedOperand(OperandRole operandRole, int valueSequence, OperandSource sourceType, String fieldCode,
            DecisionValueType valueType, LiteralValue literalValue) {
            this(operandRole, valueSequence, sourceType, fieldCode, valueType, literalValue, null, null, null, null);
        }
        /** 转换为规则与流程共同的配置操作数。 */
        public VariableOperand variableOperand() {
            return new VariableOperand(operandRole, valueSequence, sourceType, variableCode, fieldCode, valueType,
                literalValue, numericUnit, currencyCode, currencyVariableCode);
        }
    }

    record NormalizedRule(String ruleCode, String ruleName, int priorityNo, boolean enabled, String reasonCode,
                          NormalizedCondition condition, ActionKey action, String reasonMessage,
                          List<AdditionalAction> additionalActions) {
        public NormalizedRule(String ruleCode, String ruleName, int priorityNo, boolean enabled, String reasonCode,
            NormalizedCondition condition, ActionKey action) {
            this(ruleCode, ruleName, priorityNo, enabled, reasonCode, condition, action, null, null);
        }
    }

    /** 只描述配置，不包含实际主体 ID 或可执行 Handler。 */
    record AdditionalAction(int sequenceNo, ActionKey action, String targetBindingCode,
                            List<ActionParameter> parameters, String subjectVariableCode) {
        /** 旧配置主体继续由原精确绑定合同读取。 */
        public AdditionalAction(int sequenceNo, ActionKey action, String targetBindingCode, List<ActionParameter> parameters) {
            this(sequenceNo, action, targetBindingCode, parameters, null);
        }
        public AdditionalAction { parameters = parameters == null ? null : List.copyOf(parameters); }
    }

    record ActionParameter(String parameterCode, int valueSequence, OperandSource sourceType,
                           String fieldCode, DecisionValueType valueType, LiteralValue literalValue, String variableCode) {
        /** 旧动作参数没有变量编码。 */
        public ActionParameter(String parameterCode, int valueSequence, OperandSource sourceType, String fieldCode,
            DecisionValueType valueType, LiteralValue literalValue) {
            this(parameterCode, valueSequence, sourceType, fieldCode, valueType, literalValue, null);
        }
    }

    record NormalizedRuleSet(String pointCode, String ruleSetCode, String ruleSetName, Long baseRuleSetVersionId,
                             String baseContentHash, String changeReason, List<NormalizedRule> rules, String operandFormat) {
        /** 保留原请求/历史构造方式，旧格式由原 Hash Reader 解释。 */
        public NormalizedRuleSet(String pointCode, String ruleSetCode, String ruleSetName, Long baseRuleSetVersionId,
            String baseContentHash, String changeReason, List<NormalizedRule> rules) {
            this(pointCode, ruleSetCode, ruleSetName, baseRuleSetVersionId, baseContentHash, changeReason, rules, null);
        }
        public NormalizedRuleSet { rules = List.copyOf(rules); }
    }
}
