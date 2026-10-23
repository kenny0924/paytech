package com.paytech.platform.support.localization.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 文字方向（t_i18n_locale.text_direction）；本期只登记 LTR 语言，RTL 仅预留。 */
@Getter
@RequiredArgsConstructor
public enum TextDirection implements CodeEnum {

    /** 从左到右。 */
    LTR("LTR"),

    /** 从右到左。 */
    RTL("RTL");

    private final String code;
}
