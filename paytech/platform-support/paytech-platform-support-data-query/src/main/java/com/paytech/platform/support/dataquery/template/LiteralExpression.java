package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 字面量：字符串、数字（BigDecimal）、true、false 或 null。 */
@Getter
@AllArgsConstructor
public final class LiteralExpression implements TemplateExpression {

    /** 位置。 */
    private final SourcePosition position;

    /** 字面量值；String、BigDecimal、Boolean 或 null。 */
    private final Object value;
}
