package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 片段占位 @{name}，输出片段参数所选取值对应的固定原文。 */
@Getter
@AllArgsConstructor
public final class FragmentNode implements TemplateNode {

    /** @{ 的位置。 */
    private final SourcePosition position;

    /** 片段参数名。 */
    private final String name;
}
