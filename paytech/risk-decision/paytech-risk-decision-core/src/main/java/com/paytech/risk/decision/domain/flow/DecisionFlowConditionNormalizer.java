package com.paytech.risk.decision.domain.flow;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor;
import com.paytech.risk.decision.domain.authoring.DecisionPointAuthoringDescriptor.InputFieldDescriptor;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.NodeType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.enums.VariableFormat;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Condition;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Expression;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Group;
import com.paytech.risk.decision.domain.flow.DecisionFlow.Predicate;
import com.paytech.risk.decision.domain.rule.RuleAstValidator;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.LiteralValue;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NodeDraft;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedOperand;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NormalizedPredicate;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NullLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.OperandDraft;
import com.paytech.risk.decision.domain.variable.VariableConditionValidator;
import com.paytech.risk.decision.domain.variable.VariableOperand;
import com.paytech.risk.decision.domain.variable.VariableOperandJson;
import com.paytech.risk.decision.domain.variable.VariableValueNormalizer;

/**
 * 决策流条件出口的结构校验与规范化，只做形状、来源与值域检查，不求值
 * <li>schemaVersion=1：旧格式，来源为上一节点 matched（NODE_RESULT）或结账输入字段（CONTEXT）。</li>
 * <li>schemaVersion=2：统一变量操作数格式，按角色与序号排序并规范化字面量。</li>
 * <li>任何不合法都以 IllegalArgumentException 或下层校验异常抛出，由调用方统一转为条件问题。</li>
 */
final class DecisionFlowConditionNormalizer {
    /** 表达式最大嵌套深度。 */
    private static final int MAX_DEPTH = 8;

    /** 单个条件最多表达式节点数。 */
    private static final int MAX_EXPRESSIONS = 32;

    /** 单个谓词最多操作数（左值 + 100 个右值）。 */
    private static final int MAX_OPERANDS = 101;

    /** 条件规范 JSON 的最大字节数。 */
    private static final int MAX_CONDITION_BYTES = 8192;

    private static final String NOT = "NOT";

    private static final Set<String> AND_OR = Set.of("AND", "OR");

    private static final Set<String> NODE_RESULT_OPERATORS = Set.of("EQ", "NE");

    private final RuleAstValidator ruleValidator;
    private final DecisionFlowJsonCodec codec;

    /** 由校验器持有，共用规则 AST 校验与条件编码。 */
    DecisionFlowConditionNormalizer(RuleAstValidator ruleValidator, DecisionFlowJsonCodec codec) {
        this.ruleValidator = ruleValidator;
        this.codec = codec;
    }

    /**
     * 规范化条件
     *
     * @param condition 非空条件
     * @return 规范化后的条件，格式版本不变
     */
    Condition normalize(Condition condition) {
        int schemaVersion = condition.schemaVersion();
        if (schemaVersion != 1 && schemaVersion != 2) {
            throw new IllegalArgumentException();
        }
        return new Condition(schemaVersion, expression(condition.root(), 1, new int[] {0}, schemaVersion));
    }

    /** 规范 JSON 超过大小上限时拒绝。 */
    void requireWithinSize(Condition condition) {
        if (codec.conditionBytes(condition).length > MAX_CONDITION_BYTES) {
            throw new IllegalArgumentException();
        }
    }

    /** 按先序计数递归规范化表达式；深度或节点数超限即拒绝。 */
    private Expression expression(Expression expression, int depth, int[] count, int schemaVersion) {
        if (expression == null || depth > MAX_DEPTH || ++count[0] > MAX_EXPRESSIONS) {
            throw new IllegalArgumentException();
        }
        if (expression instanceof Group group) {
            return group(group, depth, count, schemaVersion);
        }
        Predicate predicate = (Predicate) expression;
        return schemaVersion == 2 ? variablePredicate(predicate) : legacyPredicate(predicate);
    }

    /** AND/OR 至少两个子项，NOT 恰好一个；子项按原顺序规范化。 */
    private Group group(Group group, int depth, int[] count, int schemaVersion) {
        boolean not = NOT.equals(group.operator());
        if (!not && (group.operator() == null || !AND_OR.contains(group.operator()))) {
            throw new IllegalArgumentException();
        }
        if (not ? group.children().size() != 1 : group.children().size() < 2) {
            throw new IllegalArgumentException();
        }
        return new Group(group.operator(),
            group.children().stream().map(child -> expression(child, depth + 1, count, schemaVersion)).toList());
    }

    /** 统一变量格式：只允许操作数，按角色与序号排序，每个角色的序号从一连续。 */
    private Predicate variablePredicate(Predicate predicate) {
        if (predicate.sourceType() != null || predicate.fieldCode() != null || predicate.values() != null
            || predicate.operands() == null || predicate.operands().isEmpty()
            || predicate.operands().size() > MAX_OPERANDS || predicate.operator() == null) {
            throw new IllegalArgumentException();
        }
        ComparisonOperator.valueOf(predicate.operator());
        List<VariableOperand> operands = predicate.operands().stream().map(DecisionFlowConditionNormalizer::operand)
            .sorted(Comparator.comparing(VariableOperand::operandRole).thenComparingInt(VariableOperand::valueSequence))
            .toList();
        requireConsecutiveSequences(operands);
        VariableOperandJson.validatePredicate(ComparisonOperator.valueOf(predicate.operator()), operands);
        return new Predicate(null, null, predicate.operator(), null, operands);
    }

    /** 校验操作数形状；变量操作数不带字面量，字面量操作数按值类型规范化。 */
    private static VariableOperand operand(VariableOperand operand) {
        VariableOperandJson.validateShape(operand);
        LiteralValue literal = operand.sourceType() == OperandSource.VARIABLE ? NullLiteral.INSTANCE
            : VariableConditionValidator.literal(VariableValueNormalizer.normalize(operand.valueType(),
                operand.valueType() == DecisionValueType.STRING ? VariableFormat.TEXT : VariableFormat.NONE,
                VariableConditionValidator.value(operand.literalValue())));
        return new VariableOperand(operand.operandRole(), operand.valueSequence(), operand.sourceType(),
            operand.variableCode(), operand.fieldCode(), operand.valueType(), literal, operand.numericUnit(),
            operand.currencyCode(), operand.currencyVariableCode());
    }

    /** 已排序操作数中，每个角色的序号必须从一连续。 */
    private static void requireConsecutiveSequences(List<VariableOperand> operands) {
        for (OperandRole role : OperandRole.values()) {
            List<VariableOperand> selected = operands.stream().filter(operand -> operand.operandRole() == role).toList();
            for (int index = 0; index < selected.size(); index++) {
                if (selected.get(index).valueSequence() != index + 1) {
                    throw new IllegalArgumentException();
                }
            }
        }
    }

    /** 旧格式：只允许来源与字面量列表。 */
    private Predicate legacyPredicate(Predicate predicate) {
        if (predicate.operands() != null || predicate.values() == null) {
            throw new IllegalArgumentException();
        }
        if ("NODE_RESULT".equals(predicate.sourceType())) {
            return nodeResultPredicate(predicate);
        }
        if (!"CONTEXT".equals(predicate.sourceType())) {
            throw new IllegalArgumentException();
        }
        return contextPredicate(predicate);
    }

    /** 上一节点 matched 只能与一个布尔字面量做等于或不等于比较，原样保留。 */
    private static Predicate nodeResultPredicate(Predicate predicate) {
        if (!"matched".equals(predicate.fieldCode()) || predicate.operator() == null
            || !NODE_RESULT_OPERATORS.contains(predicate.operator())
            || predicate.values().size() != 1 || !(predicate.values().getFirst() instanceof BooleanLiteral)) {
            throw new IllegalArgumentException();
        }
        return predicate;
    }

    /** 结账输入字段条件交给规则 AST 校验，取其规范化后的右值。 */
    private Predicate contextPredicate(Predicate predicate) {
        InputFieldDescriptor field = DecisionPointAuthoringDescriptor.legacyCheckoutPrepare().fields().stream()
            .filter(value -> value.fieldCode().equals(predicate.fieldCode())).findFirst()
            .orElseThrow(IllegalArgumentException::new);
        ComparisonOperator operator = Arrays.stream(ComparisonOperator.values())
            .filter(value -> value.getCode().equals(predicate.operator())).findFirst()
            .orElseThrow(IllegalArgumentException::new);
        List<OperandDraft> operands = new ArrayList<>();
        operands.add(new OperandDraft(OperandRole.LEFT, 1, OperandSource.CONTEXT, predicate.fieldCode(),
            field.valueType(), NullLiteral.INSTANCE));
        for (int index = 0; index < predicate.values().size(); index++) {
            operands.add(new OperandDraft(OperandRole.RIGHT, index + 1, OperandSource.LITERAL, null, field.valueType(),
                predicate.values().get(index)));
        }
        NormalizedPredicate normalized = (NormalizedPredicate) ruleValidator.validateCondition(
            List.of(new NodeDraft("predicate", null, 1, NodeType.PREDICATE, null, operator, operands)));
        return new Predicate(predicate.sourceType(), predicate.fieldCode(), predicate.operator(),
            normalized.operands().stream().filter(operand -> operand.operandRole() == OperandRole.RIGHT)
                .map(NormalizedOperand::literalValue).toList());
    }
}
