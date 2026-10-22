package com.paytech.platform.support.dataquery.template;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 循环块 {{#each list as item, index separator="…"}} … {{/each}}。 */
@Getter
@AllArgsConstructor
public final class EachNode implements TemplateNode {

    /** {{#each 的位置。 */
    private final SourcePosition position;

    /** 被遍历的集合参数路径。 */
    private final TemplatePath path;

    /** 元素变量名。 */
    private final String itemName;

    /** 下标变量名，从 0 开始；未声明时为 null。 */
    private final String indexName;

    /** 元素之间的分隔文本；未声明时为空串。 */
    private final String separator;

    /** 循环体。 */
    private final List<TemplateNode> children;
}
