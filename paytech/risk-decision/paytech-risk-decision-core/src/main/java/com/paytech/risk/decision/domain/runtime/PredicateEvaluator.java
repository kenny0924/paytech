package com.paytech.risk.decision.domain.runtime;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.risk.decision.domain.enums.ComparisonOperator;
import com.paytech.risk.decision.domain.enums.DecisionValueType;
import com.paytech.risk.decision.domain.enums.OperandRole;
import com.paytech.risk.decision.domain.enums.OperandSource;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.BooleanLiteral;
import com.paytech.risk.decision.domain.rule.RuleAstValidator.StringLiteral;
import com.paytech.risk.decision.domain.variable.VariableOperand;

/**
 * 条件谓词求值；规则条件与流程连线共用
 * <li>左值无值时，除 IS_NULL/IS_NOT_NULL 外结果为假；NOT 作用于假为真。</li>
 * <li>金额两侧币种不同时结果为假，不做汇率换算。</li>
 * <li>BETWEEN 为闭区间；字符串只比较相等。</li>
 */
final class PredicateEvaluator {
    /** 纯求值工具。 */
    private PredicateEvaluator() { }

    /**
     * 求值一个谓词
     *
     * @param operator 比较运算符
     * @param operands 已保存的操作数，LEFT 在前、RIGHT 按序号
     * @param variables 变量取值解析
     * @return 谓词结果
     */
    static boolean evaluate(ComparisonOperator operator, List<VariableOperand> operands,
        Function<String, ResolvedValue> variables) {
        List<ResolvedValue> left = resolve(operands, OperandRole.LEFT, variables);
        List<ResolvedValue> right = resolve(operands, OperandRole.RIGHT, variables);
        if (left.size() != 1) {
            throw new ErrorException("谓词左值数量无效");
        }
        ResolvedValue subject = left.getFirst();
        if (operator == ComparisonOperator.IS_NULL || operator == ComparisonOperator.IS_NOT_NULL) {
            return (subject.value() == null) == (operator == ComparisonOperator.IS_NULL);
        }
        if (subject.value() == null) {
            return false;
        }
        return switch (operator) {
            case IN -> contains(subject, right);
            case NOT_IN -> !contains(subject, right);
            case BETWEEN -> between(subject, right);
            default -> compare(operator, subject, single(right));
        };
    }

    /** 按角色解析操作数，保持保存时的序号顺序。 */
    static List<ResolvedValue> resolve(List<VariableOperand> operands, OperandRole role,
        Function<String, ResolvedValue> variables) {
        return operands.stream().filter(operand -> operand.operandRole() == role)
            .sorted((a, b) -> Integer.compare(a.valueSequence(), b.valueSequence()))
            .map(operand -> resolve(operand, variables)).toList();
    }

    /** 变量取当前值，字面量取保存值及其币种坐标。 */
    static ResolvedValue resolve(VariableOperand operand, Function<String, ResolvedValue> variables) {
        if (operand.sourceType() == OperandSource.VARIABLE) {
            return variables.apply(operand.variableCode());
        }
        if (operand.sourceType() != OperandSource.LITERAL) {
            throw new ErrorException("运行期只支持变量与字面量操作数");
        }
        Object value = switch (operand.literalValue()) {
            case StringLiteral text -> text.value();
            case BooleanLiteral bool -> bool.value();
            default -> null;
        };
        String currency = operand.currencyCode() != null ? operand.currencyCode()
            : operand.currencyVariableCode() == null ? null
                : (String)variables.apply(operand.currencyVariableCode()).value();
        return ResolvedValue.scalar(operand.valueType(), value, currency);
    }

    /** 比较类运算符要求恰好一个右值。 */
    static ResolvedValue single(List<ResolvedValue> right) {
        if (right.size() != 1) {
            throw new ErrorException("谓词右值数量无效");
        }
        return right.getFirst();
    }

    /** 右值为单个集合变量时取其成员，否则取字面量列表。 */
    static boolean contains(ResolvedValue subject, List<ResolvedValue> right) {
        List<Object> candidates = right.size() == 1 && right.getFirst().members() != null ? right.getFirst().members()
            : right.stream().map(ResolvedValue::value).toList();
        return candidates.stream().anyMatch(value -> value != null && same(subject.type(), subject.value(), value));
    }

    /** 闭区间。 */
    static boolean between(ResolvedValue subject, List<ResolvedValue> right) {
        if (right.size() != 2) {
            throw new ErrorException("BETWEEN 必须恰好两个右值");
        }
        return compare(ComparisonOperator.GTE, subject, right.get(0)) && compare(ComparisonOperator.LTE, subject, right.get(1));
    }

    /** 单值比较；右值无值或金额币种不同为假。 */
    static boolean compare(ComparisonOperator operator, ResolvedValue left, ResolvedValue right) {
        if (right.value() == null || currencyMismatch(left, right)) {
            return false;
        }
        if (operator == ComparisonOperator.EQ || operator == ComparisonOperator.NE) {
            return same(left.type(), left.value(), right.value()) == (operator == ComparisonOperator.EQ);
        }
        int order = order(left.type(), left.value(), right.value());
        return switch (operator) {
            case GT -> order > 0;
            case GTE -> order >= 0;
            case LT -> order < 0;
            case LTE -> order <= 0;
            default -> throw new ErrorException("运算符不支持该比较: " + operator.getCode());
        };
    }

    /** 两侧都确定币种且不同。 */
    static boolean currencyMismatch(ResolvedValue left, ResolvedValue right) {
        return left.currency() != null && right.currency() != null && !left.currency().equals(right.currency());
    }

    /** 按类型判断相等；数值忽略尾零。 */
    static boolean same(DecisionValueType type, Object left, Object right) {
        if (type == DecisionValueType.NUMBER) {
            return order(type, left, right) == 0;
        }
        return Objects.equals(left.toString(), right.toString());
    }

    /** 有序类型的比较。 */
    static int order(DecisionValueType type, Object left, Object right) {
        return switch (type) {
            case NUMBER -> new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
            case DATETIME -> Instant.parse(left.toString()).compareTo(Instant.parse(right.toString()));
            case DURATION -> Long.compare(Long.parseLong(left.toString()), Long.parseLong(right.toString()));
            default -> throw new ErrorException("该类型不支持大小比较: " + type.getCode());
        };
    }
}
