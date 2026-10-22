package com.paytech.platform.support.workflow.domain.graph;

import com.paytech.platform.support.workflow.core.enums.ConditionOperator;

/**
 * 条件子句：变量、比较符与比较值。
 *
 * @param variable 变量名，如 start.paymentMethod
 * @param operator 比较符
 * @param value 比较值；IS_EMPTY / IS_NOT_EMPTY 时为 null
 */
public record ConditionClause(String variable, ConditionOperator operator, ConditionValue value) {
}
