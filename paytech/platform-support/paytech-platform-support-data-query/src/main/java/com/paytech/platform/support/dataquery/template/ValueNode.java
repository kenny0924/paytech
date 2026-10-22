package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 值占位 #{path}，由引擎的值绑定器输出，值本身永不拼进正文。 */
@Getter
@AllArgsConstructor
public final class ValueNode implements TemplateNode {

    /** #{ 的位置。 */
    private final SourcePosition position;

    /** 取值路径。 */
    private final TemplatePath path;
}
