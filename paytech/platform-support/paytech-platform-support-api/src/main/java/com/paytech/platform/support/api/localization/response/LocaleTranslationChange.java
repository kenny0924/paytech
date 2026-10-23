package com.paytech.platform.support.api.localization.response;

import java.util.List;

import lombok.Getter;
import lombok.Setter;

/** 一个语言相对线上发布的翻译变化。 */
@Getter
@Setter
public class LocaleTranslationChange {

    /** 语言代码。 */
    private String locale;

    /** 新增、修改与移除的翻译条数。 */
    private int changedCount;

    /** 变化的 key 示例，按字典序，最多 20 个。 */
    private List<String> sampleKeys;
}
