package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 正文文本，原样输出；转义 \#{ \@{ \{{ 已还原为 #{ @{ {{。 */
@Getter
@AllArgsConstructor
public final class TextNode implements TemplateNode {

    /** 起始位置。 */
    private final SourcePosition position;

    /** 输出文本。 */
    private final String text;
}
