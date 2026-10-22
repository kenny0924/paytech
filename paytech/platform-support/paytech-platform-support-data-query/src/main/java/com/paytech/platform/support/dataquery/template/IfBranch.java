package com.paytech.platform.support.dataquery.template;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 条件块的一个分支：#if、#elseif 或 #else。 */
@Getter
@AllArgsConstructor
public final class IfBranch {

    /** 分支标签的位置。 */
    private final SourcePosition position;

    /** 分支条件；#else 分支为 null。 */
    private final TemplateExpression condition;

    /** 分支内容。 */
    private final List<TemplateNode> children;
}
