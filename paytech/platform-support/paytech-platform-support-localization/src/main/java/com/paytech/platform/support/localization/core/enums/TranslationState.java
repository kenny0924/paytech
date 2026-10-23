package com.paytech.platform.support.localization.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 翻译工作台的填写状态筛选；查询时由翻译与基准修订号计算，不持久化。 */
@Getter
@RequiredArgsConstructor
public enum TranslationState implements CodeEnum {

    /** 该语言没有翻译。 */
    UNTRANSLATED("UNTRANSLATED"),

    /** 有翻译，但翻译依据的基准修订号小于当前基准修订号。 */
    SOURCE_CHANGED("SOURCE_CHANGED"),

    /** 有翻译且依据当前基准。 */
    TRANSLATED("TRANSLATED");

    private final String code;
}
