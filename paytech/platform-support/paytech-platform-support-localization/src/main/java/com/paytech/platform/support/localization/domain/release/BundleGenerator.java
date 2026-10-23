package com.paytech.platform.support.localization.domain.release;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.Sha256;

/**
 * 语言包生成（技术方案 3.3 第 1 步）：en 由英文基准生成，其他语言只含已填写的翻译（含基准已变更），按命名空间各一份。纯 Java。
 * <li>没有任何文案的语言 × 命名空间不生成文件；结果按语言、命名空间字典序排列，内容与摘要对同一输入稳定。</li>
 */
public final class BundleGenerator {

    private BundleGenerator() {
    }

    /**
     * 生成语言包。
     *
     * @param activeKeys ACTIVE key
     * @param namespaceCodes 命名空间 ID 到编码
     * @param translations 这些 key 的翻译
     * @param locales 参与发布的 ENABLED 非 en 语言
     * @return 语言包，en 在内
     */
    public static List<GeneratedBundle> generate(List<I18nKey> activeKeys, Map<Long, String> namespaceCodes,
        Collection<I18nTranslation> translations, Collection<String> locales) {
        Map<String, Map<String, Map<String, String>>> texts = new TreeMap<>();
        for (I18nKey key : activeKeys) {
            put(texts, LocalizationCodes.BASE_LOCALE, namespaceCodes.get(key.getNamespaceId()), key.getKeyCode(),
                key.getSourceText());
        }
        Map<Long, I18nKey> keys = new TreeMap<>();
        activeKeys.forEach(key -> keys.put(key.getKeyId(), key));
        for (I18nTranslation translation : translations) {
            I18nKey key = keys.get(translation.getKeyId());
            if (key != null && locales.contains(translation.getLocaleCode())) {
                put(texts, translation.getLocaleCode(), namespaceCodes.get(key.getNamespaceId()), key.getKeyCode(),
                    translation.getText());
            }
        }
        List<GeneratedBundle> bundles = new ArrayList<>();
        texts.forEach((locale, namespaces) -> namespaces.forEach((namespace, entries) -> bundles.add(bundle(locale,
            namespace, entries))));
        return bundles;
    }

    /** 放入一条文案。 */
    static void put(Map<String, Map<String, Map<String, String>>> texts, String locale, String namespace, String key,
        String text) {
        texts.computeIfAbsent(locale, ignored -> new TreeMap<>()).computeIfAbsent(namespace, ignored -> new TreeMap<>())
            .put(key, text);
    }

    /** 一个语言 × 命名空间的语言包。 */
    static GeneratedBundle bundle(String locale, String namespace, Map<String, String> entries) {
        byte[] content = BundleContent.write(entries);
        return new GeneratedBundle(locale, namespace, content, Sha256.hex(content), entries.size());
    }
}
