package com.paytech.platform.support.dataquery.template;

import com.paytech.platform.support.dataquery.core.enums.TemplateComparisonOperator;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 比较运算；两边类型必须兼容，不做隐式转换。 */
@Getter
@AllArgsConstructor
public final class ComparisonExpression implements TemplateExpression {

    /** 运算符的位置。 */
    private final SourcePosition position;

    /** 运算符。 */
    private final TemplateComparisonOperator operator;

    /** 左操作数。 */
    private final TemplateExpression left;

    /** 右操作数。 */
    private final TemplateExpression right;
}
