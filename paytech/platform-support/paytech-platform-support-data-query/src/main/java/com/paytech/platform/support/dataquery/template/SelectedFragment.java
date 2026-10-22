package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 片段参数的取值：调用方选择的键及其对应的固定片段原文。 */
@Getter
@AllArgsConstructor
public final class SelectedFragment {

    /** choices 的键；条件表达式中按字符串比较。 */
    private final String key;

    /** 输出到正文的固定片段原文。 */
    private final String text;
}
