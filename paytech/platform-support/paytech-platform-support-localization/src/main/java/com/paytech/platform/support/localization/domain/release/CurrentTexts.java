package com.paytech.platform.support.localization.domain.release;

import java.util.List;
import java.util.Map;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.domain.LocalizationCodes;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 一次请求读取的当前文案：应用的 ACTIVE key、这些 key 的翻译与 ENABLED 语言。预检与发布只读取一次，覆盖率评估、变更摘要、
 * 语言包生成与变化行共用同一份数据。纯 Java。
 */
@Getter
@AllArgsConstructor
public class CurrentTexts {

    /** 应用的 ACTIVE key。 */
    private final List<I18nKey> activeKeys;

    /** 这些 key 的翻译。 */
    private final List<I18nTranslation> translations;

    /** ENABLED 语言代码（含 en），按排序值、代码升序。 */
    private final List<String> enabledLocales;

    /** 参与发布的 ENABLED 非 en 语言。 */
    public List<String> targetLocales() {
        return enabledLocales.stream().filter(locale -> !LocalizationCodes.BASE_LOCALE.equals(locale)).toList();
    }

    /** 按发布口径展开的内容：语言 → key → 文案（见 {@link ReleaseContent#current}）。 */
    public Map<String, Map<String, String>> content() {
        return ReleaseContent.current(activeKeys, translations, targetLocales());
    }
}
