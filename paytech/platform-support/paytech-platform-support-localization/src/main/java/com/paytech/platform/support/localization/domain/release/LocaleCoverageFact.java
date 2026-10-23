package com.paytech.platform.support.localization.domain.release;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** 一个语言的翻译覆盖情况；同时是 t_i18n_release.coverage 数组元素的 JSON 结构。 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class LocaleCoverageFact {

    /** 语言代码。 */
    private String locale;

    /** 是否为必须覆盖语言。 */
    private boolean required;

    /** ACTIVE key 数。 */
    private int totalKeys;

    /** 已有翻译的 ACTIVE key 数（含基准已变更）；en 等于 totalKeys。 */
    private int translatedKeys;

    /** 基准已变更的 key 数。 */
    private int sourceChangedKeys;

    /** 覆盖率百分比，向下取整；没有 key 时为 100。 */
    private int coveragePercent;

    /** 是否达到阈值；非必须覆盖语言恒为 true。 */
    private boolean satisfied;
}
