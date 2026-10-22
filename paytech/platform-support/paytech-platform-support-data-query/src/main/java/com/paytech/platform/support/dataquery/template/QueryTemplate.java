package com.paytech.platform.support.dataquery.template;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 解析后的模板：原文与语法树，不可变，可在多次渲染间复用。 */
@Getter
@AllArgsConstructor
public final class QueryTemplate {

    /** 模板原文。 */
    private final String body;

    /** 顶层节点。 */
    private final List<TemplateNode> nodes;
}
