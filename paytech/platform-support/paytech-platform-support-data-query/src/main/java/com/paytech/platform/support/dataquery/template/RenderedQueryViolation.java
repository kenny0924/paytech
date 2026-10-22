package com.paytech.platform.support.dataquery.template;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 引擎对渲染结果的一处拒绝：正文下标与原因；保存校验据此定位回模板行列。 */
@Getter
@AllArgsConstructor
public final class RenderedQueryViolation {

    /** 渲染正文中的下标，从 0 开始。 */
    private final int offset;

    /** 中文原因。 */
    private final String message;
}
