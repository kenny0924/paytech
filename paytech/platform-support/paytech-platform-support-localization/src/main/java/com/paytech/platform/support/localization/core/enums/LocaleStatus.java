package com.paytech.platform.support.localization.core.enums;

import com.paytech.foundation.common.enums.CodeEnum;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/** 语言状态（t_i18n_locale.status）；停用的语言不能被选为默认或必须覆盖语言，也不出现在 manifest 中。 */
@Getter
@RequiredArgsConstructor
public enum LocaleStatus implements CodeEnum {

    /** 启用。 */
    ENABLED("ENABLED"),

    /** 停用；en 不能停用。 */
    DISABLED("DISABLED");

    private final String code;
}
