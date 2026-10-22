package com.paytech.platform.support.dataquery.template;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collection;

import com.paytech.platform.support.dataquery.core.enums.QueryParameterType;
import com.paytech.platform.support.dataquery.core.enums.TemplateComparisonOperator;
import com.paytech.platform.support.dataquery.core.enums.TemplateLogicalOperator;

/**
 * 条件求值。只写路径时按“非空”判断；比较两边类型必须兼容（字符串、数字、布尔、日期、日期时间各自成类），
 * 不做隐式转换；大小比较遇到 null 结果为 false。
 */
final class ExpressionEvaluator {

    static final String STRING_KIND = "字符串";

    static final String NUMBER_KIND = "数字";

    static final String BOOLEAN_KIND = "布尔";

    static final String DATE_KIND = "日期";

    static final String DATETIME_KIND = "日期时间";

    static final String OTHER_KIND = "集合或对象";

    /** 工具类，不实例化。 */
    private ExpressionEvaluator() {
    }

    /**
     * 求条件是否成立。
     *
     * @param expression 条件
     * @param scope 作用域
     * @return 是否成立
     */
    static boolean test(TemplateExpression expression, TemplateScope scope) {
        return present(evaluate(expression, scope));
    }

    /**
     * “非空”判断：非 null、字符串非空白、集合非空、布尔为 true。
     *
     * @param value 值
     * @return 是否成立
     */
    static boolean present(Object value) {
        return switch (value) {
            case null -> false;
            case Boolean bool -> bool;
            case String text -> !text.isBlank();
            case Collection<?> collection -> !collection.isEmpty();
            default -> true;
        };
    }

    /**
     * 求表达式的值。
     *
     * @param expression 表达式
     * @param scope 作用域
     * @return 值；片段参数取其键
     */
    private static Object evaluate(TemplateExpression expression, TemplateScope scope) {
        return switch (expression) {
            case PathExpression path -> unwrap(scope.resolve(path.getPath(), path.getPosition()));
            case LiteralExpression literal -> literal.getValue();
            case NotExpression not -> !test(not.getOperand(), scope);
            case LogicalExpression logical -> logical(logical, scope);
            case ComparisonExpression comparison -> compare(comparison, scope);
        };
    }

    /**
     * 短路求值 &amp;&amp; 与 ||。
     *
     * @param logical 表达式
     * @param scope 作用域
     * @return 结果
     */
    private static boolean logical(LogicalExpression logical, TemplateScope scope) {
        boolean left = test(logical.getLeft(), scope);
        if (logical.getOperator() == TemplateLogicalOperator.AND) {
            return left && test(logical.getRight(), scope);
        }
        return left || test(logical.getRight(), scope);
    }

    /**
     * 比较求值。
     *
     * @param comparison 比较表达式
     * @param scope 作用域
     * @return 结果
     */
    private static boolean compare(ComparisonExpression comparison, TemplateScope scope) {
        Object left = evaluate(comparison.getLeft(), scope);
        Object right = evaluate(comparison.getRight(), scope);
        TemplateComparisonOperator operator = comparison.getOperator();
        if (left == null || right == null) {
            return !operator.ordering() && (left == right) == (operator == TemplateComparisonOperator.EQ);
        }
        String leftKind = kind(left);
        if (!leftKind.equals(kind(right)) || leftKind.equals(OTHER_KIND)) {
            throw new TemplateCheckException(comparison.getPosition(),
                "比较两边类型不兼容：" + leftKind + " " + operator.getCode() + " " + kind(right));
        }
        if (operator.ordering() && left instanceof Boolean) {
            throw new TemplateCheckException(comparison.getPosition(), "布尔值不能比较大小");
        }
        int result = compareSameKind(left, right);
        return switch (operator) {
            case EQ -> result == 0;
            case NE -> result != 0;
            case GT -> result > 0;
            case GE -> result >= 0;
            case LT -> result < 0;
            case LE -> result <= 0;
        };
    }

    /**
     * 值的比较类别。
     *
     * @param value 非 null 值
     * @return 类别名，用于兼容判断与报错
     */
    static String kind(Object value) {
        return switch (value) {
            case String ignored -> STRING_KIND;
            case Long ignored -> NUMBER_KIND;
            case BigDecimal ignored -> NUMBER_KIND;
            case Boolean ignored -> BOOLEAN_KIND;
            case LocalDate ignored -> DATE_KIND;
            case LocalDateTime ignored -> DATETIME_KIND;
            default -> OTHER_KIND;
        };
    }

    /**
     * 参数类型的比较类别，供保存时静态检查使用，与运行时 {@link #kind(Object)} 一致。
     *
     * @param type 参数类型
     * @return 类别名
     */
    static String kindOf(QueryParameterType type) {
        return switch (type) {
            case STRING, ENUM, FRAGMENT -> STRING_KIND;
            case INTEGER, DECIMAL -> NUMBER_KIND;
            case BOOLEAN -> BOOLEAN_KIND;
            case DATE -> DATE_KIND;
            case DATETIME -> DATETIME_KIND;
            case LIST, OBJECT -> OTHER_KIND;
        };
    }

    /**
     * 同类值比较；数字按数值比较（1500.5 等于 1500.50）。
     *
     * @param left 左值
     * @param right 右值
     * @return 比较结果
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static int compareSameKind(Object left, Object right) {
        if (left instanceof Number || right instanceof Number) {
            return new BigDecimal(left.toString()).compareTo(new BigDecimal(right.toString()));
        }
        return ((Comparable) left).compareTo(right);
    }

    /**
     * 片段参数在表达式中按其键参与比较。
     *
     * @param value 值
     * @return 片段键或原值
     */
    private static Object unwrap(Object value) {
        return value instanceof SelectedFragment fragment ? fragment.getKey() : value;
    }
}
