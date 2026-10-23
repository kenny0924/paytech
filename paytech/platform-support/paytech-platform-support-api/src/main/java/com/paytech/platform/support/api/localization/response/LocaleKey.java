package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** 某语言下的一个 key。 */
@Getter
@Setter
public class LocaleKey {

    /** 语言代码。 */
    private String locale;

    /** 完整 key。 */
    private String keyCode;
}
