package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 一个语言在当前文案下的翻译覆盖情况。 */
@Getter
@Setter
public class LocaleCoverage {

    /** 语言代码；en 恒为 100%。 */
    private String locale;

    /** 是否为应用必须覆盖的语言。 */
    private boolean required;

    /** ACTIVE key 数。 */
    private int totalKeys;

    /** 已有翻译的 ACTIVE key 数（含基准已变更）。 */
    private int translatedKeys;

    /** 其中基准已变更、沿用旧翻译的 key 数。 */
    private int sourceChangedKeys;

    /** 覆盖率百分比，向下取整；没有 key 时为 100。 */
    private int coveragePercent;

    /** 是否达到应用阈值：translatedKeys × 100 ≥ 阈值 × totalKeys；非必须覆盖语言恒为 true。 */
    private boolean satisfied;
}
