package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 逻辑非：对操作数的“非空”判断取反。 */
@Getter
@AllArgsConstructor
public final class NotExpression implements TemplateExpression {

    /** ! 的位置。 */
    private final SourcePosition position;

    /** 操作数。 */
    private final TemplateExpression operand;
}
