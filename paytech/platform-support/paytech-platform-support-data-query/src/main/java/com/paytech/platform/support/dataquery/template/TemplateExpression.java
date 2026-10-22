package com.paytech.platform.support.dataquery.template;

/** 条件表达式：路径、字面量、!、&&、||、比较；不支持函数调用。 */
public sealed interface TemplateExpression
    permits PathExpression, LiteralExpression, NotExpression, LogicalExpression, ComparisonExpression {

    /**
     * 表达式起始位置。
     *
     * @return 位置
     */
    SourcePosition getPosition();
}
