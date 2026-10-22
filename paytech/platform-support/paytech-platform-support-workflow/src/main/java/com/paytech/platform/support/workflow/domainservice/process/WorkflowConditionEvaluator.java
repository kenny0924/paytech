package com.paytech.platform.support.workflow.domainservice.process;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import com.paytech.foundation.common.error.ErrorException;
import com.paytech.platform.support.workflow.core.common.WorkflowValueFormats;
import com.paytech.platform.support.workflow.core.enums.ConditionOperator;
import com.paytech.platform.support.workflow.core.enums.FormFieldType;
import com.paytech.platform.support.workflow.domain.graph.ConditionClause;
import com.paytech.platform.support.workflow.domain.graph.EdgeCondition;
import com.paytech.platform.support.workflow.domain.graph.WorkflowNode;
import com.paytech.platform.support.workflow.domain.variable.VariableValue;
import com.paytech.platform.support.workflow.domain.variable.WorkflowVariables;

import tools.jackson.databind.JsonNode;

/**
 * 结构化条件求值（规则 B4）：不支持脚本。
 *
 * <p>变量为空（缺失、null、空字符串、空数组）时只有 IS_EMPTY 成立，其余比较符（含 NE）一律不成立。
 * 数字按十进制精确比较，日期按 YYYY-MM-DD 比较，文本与枚举按原文比较。已发布的流程图经过发布校验，
 * 运行中遇到比较符与类型不符或变量值格式非法属于数据不一致，抛出 {@link ErrorException}。</p>
 */
public final class WorkflowConditionEvaluator {

    /**
     * 为排他网关选出生效的出边：按条件配置顺序，第一个子句全部成立的边生效，都不成立走默认边。
     *
     * @param gateway 排他网关
     * @param variables 当前实例变量
     * @return 选中的出边 key
     */
    public String selectEdge(WorkflowNode gateway, WorkflowVariables variables) {
        for (EdgeCondition condition : gateway.conditions()) {
            if (matchesAll(condition.all(), variables)) {
                return condition.edgeKey();
            }
        }
        return gateway.defaultEdgeKey();
    }

    /**
     * 子句全部成立；空子句组视为不成立（发布校验已拒绝空组）。
     *
     * @param clauses 子句
     * @param variables 当前实例变量
     * @return 全部成立返回 true
     */
    public boolean matchesAll(List<ConditionClause> clauses, WorkflowVariables variables) {
        return !clauses.isEmpty() && clauses.stream().allMatch(clause -> matches(clause, variables));
    }

    /**
     * 判断一个子句是否成立。
     *
     * @param clause 子句
     * @param variables 当前实例变量
     * @return 成立返回 true
     */
    public boolean matches(ConditionClause clause, WorkflowVariables variables) {
        VariableValue value = variables.get(clause.variable()).orElse(null);
        boolean empty = value == null || value.isEmpty();
        ConditionOperator operator = clause.operator();
        if (operator == ConditionOperator.IS_EMPTY) {
            return empty;
        }
        if (operator == ConditionOperator.IS_NOT_EMPTY) {
            return !empty;
        }
        if (empty) {
            return false;
        }
        if (!operator.supports(value.type())) {
            throw new ErrorException("条件比较符 " + operator.getCode() + " 不能用于 " + value.type().getCode()
                + " 类型的变量 " + clause.variable());
        }
        return compare(operator, value, clause.value() == null ? List.of() : clause.value().values(),
            clause.variable());
    }

    /**
     * 对非空变量执行比较。
     *
     * @param operator 比较符
     * @param value 变量值
     * @param expected 比较值文本
     * @param name 变量名，用于错误信息
     * @return 比较成立返回 true
     */
    private boolean compare(ConditionOperator operator, VariableValue value, List<String> expected, String name) {
        if (expected.isEmpty()) {
            throw new ErrorException("条件缺少比较值：" + name);
        }
        return switch (operator) {
            case EQ -> compareTo(value, expected.get(0), name) == 0;
            case NE -> compareTo(value, expected.get(0), name) != 0;
            case IN -> expected.stream().anyMatch(item -> compareTo(value, item, name) == 0);
            case CONTAINS -> text(value, name).contains(expected.get(0));
            case GT -> compareTo(value, expected.get(0), name) > 0;
            case GTE -> compareTo(value, expected.get(0), name) >= 0;
            case LT -> compareTo(value, expected.get(0), name) < 0;
            case LTE -> compareTo(value, expected.get(0), name) <= 0;
            default -> throw new ErrorException("比较符不需要比较值：" + operator.getCode());
        };
    }

    /**
     * 按变量类型比较变量值与比较值；文本、枚举、布尔只区分相等（0）与不等（非 0）。
     *
     * @param value 变量值
     * @param expected 比较值文本
     * @param name 变量名，用于错误信息
     * @return 小于、等于、大于分别为负数、0、正数
     */
    private int compareTo(VariableValue value, String expected, String name) {
        FormFieldType type = value.type();
        return switch (type) {
            case NUMBER -> decimal(value.value(), name).compareTo(parseDecimal(expected, name));
            case DATE -> LocalDate.parse(date(value.value(), name)).compareTo(LocalDate.parse(checkDate(expected, name)));
            case BOOLEAN -> Boolean.compare(bool(value.value(), name), parseBoolean(expected, name));
            default -> text(value, name).equals(expected) ? 0 : 1;
        };
    }

    /**
     * 取文本型变量值。
     *
     * @param value 变量值
     * @param name 变量名
     * @return 文本
     */
    private String text(VariableValue value, String name) {
        if (!value.value().isString()) {
            throw new ErrorException("变量 " + name + " 的值不是文本");
        }
        return value.value().asString();
    }

    /**
     * 取数字型变量值：十进制字符串或 JSON 数字。
     *
     * @param node 原始值
     * @param name 变量名
     * @return 十进制数
     */
    private BigDecimal decimal(JsonNode node, String name) {
        if (node.isNumber()) {
            return node.decimalValue();
        }
        if (node.isString()) {
            return parseDecimal(node.asString(), name);
        }
        throw new ErrorException("变量 " + name + " 的值不是十进制数");
    }

    /**
     * 解析十进制文本。
     *
     * @param text 文本
     * @param name 变量名
     * @return 十进制数
     */
    private BigDecimal parseDecimal(String text, String name) {
        if (!WorkflowValueFormats.isDecimal(text)) {
            throw new ErrorException("变量 " + name + " 的数字不合法：" + text);
        }
        return new BigDecimal(text);
    }

    /**
     * 取日期型变量值。
     *
     * @param node 原始值
     * @param name 变量名
     * @return YYYY-MM-DD 文本
     */
    private String date(JsonNode node, String name) {
        if (!node.isString()) {
            throw new ErrorException("变量 " + name + " 的值不是日期文本");
        }
        return checkDate(node.asString(), name);
    }

    /**
     * 校验日期文本。
     *
     * @param text 文本
     * @param name 变量名
     * @return 同一文本
     */
    private String checkDate(String text, String name) {
        if (!WorkflowValueFormats.isDate(text)) {
            throw new ErrorException("变量 " + name + " 的日期不合法：" + text);
        }
        return text;
    }

    /**
     * 取布尔型变量值。
     *
     * @param node 原始值
     * @param name 变量名
     * @return 布尔值
     */
    private boolean bool(JsonNode node, String name) {
        if (!node.isBoolean()) {
            throw new ErrorException("变量 " + name + " 的值不是布尔值");
        }
        return node.booleanValue();
    }

    /**
     * 解析布尔文本。
     *
     * @param text 文本
     * @param name 变量名
     * @return 布尔值
     */
    private boolean parseBoolean(String text, String name) {
        if (!WorkflowValueFormats.isBoolean(text)) {
            throw new ErrorException("变量 " + name + " 的布尔比较值不合法：" + text);
        }
        return Boolean.parseBoolean(text);
    }
}
