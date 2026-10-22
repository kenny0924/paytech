package com.paytech.platform.support.dataquery.template;

/** 模板语法树节点；注释在解析时丢弃，不形成节点。 */
public sealed interface TemplateNode permits TextNode, ValueNode, FragmentNode, IfNode, EachNode, JoinNode, PartNode {

    /**
     * 节点在正文中的起始位置。
     *
     * @return 位置
     */
    SourcePosition getPosition();
}
