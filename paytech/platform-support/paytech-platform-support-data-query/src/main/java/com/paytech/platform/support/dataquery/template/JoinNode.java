package com.paytech.platform.support.dataquery.template;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 拼接块 {{#join "分隔符" prefix="…" suffix="…"}} … {{/join}}。
 *
 * <p>直接子节点只能是 part、if、each 和空白；每个子节点是一段，输出去掉首尾空白，空段丢弃，其余用分隔符连接；
 * 全部为空时 prefix 与 suffix 也不输出。</p>
 */
@Getter
@AllArgsConstructor
public final class JoinNode implements TemplateNode {

    /** {{#join 的位置。 */
    private final SourcePosition position;

    /** 段之间的分隔文本。 */
    private final String separator;

    /** 至少一段非空时输出的前缀；未声明时为空串。 */
    private final String prefix;

    /** 至少一段非空时输出的后缀；未声明时为空串。 */
    private final String suffix;

    /** 直接子节点。 */
    private final List<TemplateNode> children;
}
