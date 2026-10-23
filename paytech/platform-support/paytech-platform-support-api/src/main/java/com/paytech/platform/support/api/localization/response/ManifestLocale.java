package com.paytech.platform.support.api.localization.response;

import lombok.Getter;
import lombok.Setter;

/** manifest 中一个可切换的语言。 */
@Getter
@Setter
public class ManifestLocale {

    /** 语言代码。 */
    private String code;

    /** 本语言的自称。 */
    private String nativeName;

    /** 文字方向：LTR、RTL。 */
    private String direction;
}
