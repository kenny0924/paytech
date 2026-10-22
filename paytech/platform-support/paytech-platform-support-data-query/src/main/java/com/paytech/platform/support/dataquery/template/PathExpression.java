package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 路径取值；单独作为条件时按“非空”判断。 */
@Getter
@AllArgsConstructor
public final class PathExpression implements TemplateExpression {

    /** 位置。 */
    private final SourcePosition position;

    /** 取值路径。 */
    private final TemplatePath path;
}
