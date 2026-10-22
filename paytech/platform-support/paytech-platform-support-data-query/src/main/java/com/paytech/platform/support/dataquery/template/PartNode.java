package com.paytech.platform.support.dataquery.template;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** {{#join}} 内的显式一段 {{#part}} … {{/part}}，内容可以是任意节点。 */
@Getter
@AllArgsConstructor
public final class PartNode implements TemplateNode {

    /** {{#part 的位置。 */
    private final SourcePosition position;

    /** 段内容。 */
    private final List<TemplateNode> children;
}
