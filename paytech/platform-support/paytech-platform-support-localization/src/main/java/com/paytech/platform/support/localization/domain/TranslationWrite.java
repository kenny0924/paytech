package com.paytech.platform.support.localization.domain;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 一条翻译写入：已有翻译按其版本条件更新，没有时插入已准备的新行。 */
@Getter
@AllArgsConstructor
public class TranslationWrite {

    /** 所属 ACTIVE key。 */
    private final I18nKey key;

    /** 事务前读取的已有翻译；新建时为 null。 */
    private final I18nTranslation existing;

    /** 目标值：新建时为完整新行，更新时只取文案、依据修订号、修改人与时间。 */
    private final I18nTranslation next;
}
