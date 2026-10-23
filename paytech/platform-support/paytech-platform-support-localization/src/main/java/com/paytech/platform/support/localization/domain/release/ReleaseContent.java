package com.paytech.platform.support.localization.domain.release;

import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.domain.LocalizationCodes;

/**
 * 当前文案按发布口径展开的内容：语言 → 完整 key → 文案。与语言包生成同一口径：en 取 ACTIVE key 的英文基准，其他语言只含已填写的翻译
 * （含基准已变更），停用语言与废弃 key 不计入。用于与线上发布的语言包内容比较。纯 Java。
 */
public final class ReleaseContent {

    private ReleaseContent() {
    }

    /**
     * 展开当前文案。
     *
     * @param activeKeys ACTIVE key
     * @param translations 这些 key 的翻译
     * @param locales 参与发布的 ENABLED 非 en 语言
     * @return 语言 → key → 文案；en 恒在（没有 key 时为空映射）
     */
    public static Map<String, Map<String, String>> current(List<I18nKey> activeKeys,
        Collection<I18nTranslation> translations, Collection<String> locales) {
        Map<String, Map<String, String>> texts = new TreeMap<>();
        Map<Long, I18nKey> keys = new HashMap<>();
        Map<String, String> base = new TreeMap<>();
        for (I18nKey key : activeKeys) {
            keys.put(key.getKeyId(), key);
            base.put(key.getKeyCode(), key.getSourceText());
        }
        texts.put(LocalizationCodes.BASE_LOCALE, base);
        for (I18nTranslation translation : translations) {
            I18nKey key = keys.get(translation.getKeyId());
            if (key != null && locales.contains(translation.getLocaleCode())) {
                texts.computeIfAbsent(translation.getLocaleCode(), ignored -> new TreeMap<>())
                    .put(key.getKeyCode(), translation.getText());
            }
        }
        return texts;
    }
}
