package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 渲染结果中某一段输出的来源：从 outputOffset 开始的文字来自模板的 position。 */
@Getter
@AllArgsConstructor
final class SourceMark {

    /** 输出文本中的起始下标。 */
    private final int outputOffset;

    /** 对应的模板位置。 */
    private final SourcePosition position;

    /** true 表示这段输出逐字来自模板正文，定位时按字符前进；false 表示整段都指向同一位置。 */
    private final boolean literal;
}
