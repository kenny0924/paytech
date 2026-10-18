package com.paytech.risk.decision.domain.variable;

import com.paytech.risk.decision.domain.enums.*;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.NullLiteral;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.List;
import tools.jackson.databind.JsonNode;

/** 统一操作数的准确存储投影；不包含变量目录当前值。 */
public final class VariableOperandJson {
    private static final Set<String> KEYS = Set.of("operandRole", "valueSequence", "sourceType", "variableCode", "fieldCode",
        "valueType", "literalValue", "numericUnit", "currencyCode", "currencyVariableCode");

    /** 仅提供固定协议转换。 */
    private VariableOperandJson() { }

    /** 输出包含显式 null 的十字段投影，供规则和流程 JCS 复用。 */
    public static Map<String, Object> projection(VariableOperand value) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("operandRole", value.operandRole().getCode()); result.put("valueSequence", value.valueSequence());
        result.put("sourceType", value.sourceType().getCode()); result.put("variableCode", value.variableCode());
        result.put("fieldCode", value.fieldCode()); result.put("valueType", value.valueType().getCode());
        result.put("literalValue", value.literalValue() instanceof NullLiteral ? null : VariableConditionValidator.value(value.literalValue()));
        result.put("numericUnit", value.numericUnit() == null ? null : value.numericUnit().getCode());
        result.put("currencyCode", value.currencyCode()); result.put("currencyVariableCode", value.currencyVariableCode());
        return result;
    }

    /** 严格读取新格式，不混入旧 source/values，也不读取变量当前值。 */
    public static VariableOperand read(JsonNode node) {
        if (node == null || !node.isObject() || !node.propertyNames().equals(KEYS)) { throw new IllegalArgumentException("操作数字段不完整"); }
        var sequence = node.get("valueSequence");
        if (!sequence.isIntegralNumber() || !sequence.canConvertToInt() || sequence.intValue() < 1 || sequence.intValue() > 100) {
            throw new IllegalArgumentException("操作数顺序无效");
        }
        OperandSource source = OperandSource.valueOf(text(node.get("sourceType"), false));
        JsonNode literal = node.get("literalValue");
        var value = literal.isNull() ? NullLiteral.INSTANCE : VariableConditionValidator.literal(literal.isString()
            ? literal.stringValue() : literal.isBoolean() ? literal.booleanValue() : null);
        var operand = new VariableOperand(OperandRole.valueOf(text(node.get("operandRole"), false)), sequence.intValue(), source,
            text(node.get("variableCode"), true), text(node.get("fieldCode"), true),
            DecisionValueType.valueOf(text(node.get("valueType"), false)), value,
            node.get("numericUnit").isNull() ? null : NumericUnit.valueOf(text(node.get("numericUnit"), false)),
            text(node.get("currencyCode"), true), text(node.get("currencyVariableCode"), true));
        validateShape(operand);
        return operand;
    }

    /** 持久化形状独立于当前目录资格，历史停用仍可读取原结构。 */
    public static void validateShape(VariableOperand operand) {
        if (operand.operandRole() == null || operand.valueType() == null || operand.valueSequence() < 1 || operand.valueSequence() > 100 || operand.fieldCode() != null) { throw new IllegalArgumentException("新格式不允许 fieldCode"); }
        if (operand.sourceType() == OperandSource.VARIABLE) {
            VariableDefinitionValidator.code(operand.variableCode());
            if (!(operand.literalValue() instanceof NullLiteral) || operand.numericUnit() != null
                || operand.currencyCode() != null || operand.currencyVariableCode() != null) { throw new IllegalArgumentException("变量槽位无效"); }
            return;
        }
        if (operand.sourceType() != OperandSource.LITERAL || operand.variableCode() != null || operand.literalValue() instanceof NullLiteral) {
            throw new IllegalArgumentException("字面量槽位无效");
        }
        VariableDefinitionValidator.validate(VariableConditionValidator.literalDefinition(operand, null));
        Object value = VariableConditionValidator.value(operand.literalValue());
        VariableValueNormalizer.normalize(operand.valueType(), operand.valueType() == DecisionValueType.STRING ? VariableFormat.TEXT : VariableFormat.NONE, value);
    }

    /** 不读取目录即可验证的谓词矩阵，用于幂等准备和持久化完整性复核。 */
    public static void validatePredicate(ComparisonOperator operator, List<VariableOperand> operands) {
        if (operator == null || operands == null || operands.isEmpty() || operands.size() > 101) throw new IllegalArgumentException("谓词操作数数量无效");
        operands.forEach(VariableOperandJson::validateShape);
        var left = operands.stream().filter(o -> o.operandRole() == OperandRole.LEFT).toList();
        var right = operands.stream().filter(o -> o.operandRole() == OperandRole.RIGHT).toList();
        if (left.size() != 1 || left.getFirst().valueSequence() != 1) throw new IllegalArgumentException("左侧必须为一个操作数");
        for (int index = 0; index < right.size(); index++) {
            if (right.get(index).valueSequence() != index + 1 || right.get(index).valueType() != left.getFirst().valueType())
                throw new IllegalArgumentException("右侧序号或声明类型无效");
        }
        boolean unary = operator == ComparisonOperator.IS_NULL || operator == ComparisonOperator.IS_NOT_NULL;
        boolean membership = operator == ComparisonOperator.IN || operator == ComparisonOperator.NOT_IN;
        int count = operator == ComparisonOperator.BETWEEN ? 2 : unary ? 0 : 1;
        if (membership) {
            if (right.isEmpty() || right.size() > 100 || right.size() > 1 && right.stream().anyMatch(o -> o.sourceType() != OperandSource.LITERAL))
                throw new IllegalArgumentException("集合比较形状无效");
        } else if (right.size() != count) throw new IllegalArgumentException("比较操作数数量无效");
        if (!unary && !membership && operator != ComparisonOperator.EQ && operator != ComparisonOperator.NE
            && !Set.of(DecisionValueType.NUMBER, DecisionValueType.DATETIME, DecisionValueType.DURATION).contains(left.getFirst().valueType()))
            throw new IllegalArgumentException("当前类型不支持大小或区间判断");
    }

    /** 读取字符串或协议允许的显式空值，不执行数值转字符串。 */
    static String text(JsonNode node, boolean nullable) {
        if (node.isNull() && nullable) { return null; }
        if (!node.isString()) { throw new IllegalArgumentException("操作数字符串类型无效"); }
        return node.stringValue();
    }
}
