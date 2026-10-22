package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 正文词法单元：文本、#{…}、@{…} 或 {{…}} 标签。 */
@Getter
@AllArgsConstructor
final class TemplateToken {

    /** 词法单元种类；只在解析器内部使用，不持久化。 */
    enum Kind {

        /** 正文文本。 */
        TEXT,

        /** 值占位 #{…}。 */
        VALUE,

        /** 片段占位 @{…}。 */
        FRAGMENT,

        /** 标签 {{…}}。 */
        TAG
    }

    /** 种类。 */
    private final Kind kind;

    /** 起始位置（文本首字符，或 #{、@{、{{ 的位置）。 */
    private final SourcePosition position;

    /** 文本内容，或占位、标签括号内的原文。 */
    private final String content;

    /** 括号内原文首字符的位置；文本单元与 position 相同。 */
    private final SourcePosition contentPosition;
}
