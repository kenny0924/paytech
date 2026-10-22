package com.paytech.platform.support.dataquery.template;

import com.paytech.platform.support.dataquery.core.enums.TemplateLogicalOperator;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 逻辑与、或，短路求值，操作数按“非空”判断。 */
@Getter
@AllArgsConstructor
public final class LogicalExpression implements TemplateExpression {

    /** 左操作数的位置。 */
    private final SourcePosition position;

    /** 运算符。 */
    private final TemplateLogicalOperator operator;

    /** 左操作数。 */
    private final TemplateExpression left;

    /** 右操作数。 */
    private final TemplateExpression right;
}
