package com.paytech.platform.support.localization.domain.release;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.domain.LocalizationCodes;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.icu.IcuMessageValidator;

/**
 * 发布评估（技术方案 3.3 第 1 步）：按应用必须覆盖语言与阈值计算覆盖率，列出发布报告与 ICU 问题。纯 Java。
 * <li>en 恒为 100%；覆盖率达标条件为 translated × 100 ≥ 阈值 × total，没有 key 时视为 100%。</li>
 */
public final class ReleaseAssessor {

    private ReleaseAssessor() {
    }

    /**
     * 评估当前文案。
     *
     * @param activeKeys ACTIVE key，按 key 升序
     * @param translations 这些 key 的翻译（任意语言）
     * @param locales 参与评估的 ENABLED 语言，按排序值排列；含 en
     * @param required 必须覆盖语言
     * @param threshold 覆盖率阈值百分比
     * @return 评估结果
     */
    public static ReleaseAssessment assess(List<I18nKey> activeKeys, Collection<I18nTranslation> translations,
        List<String> locales, Set<String> required, int threshold) {
        Map<String, Map<Long, I18nTranslation>> byLocale = byLocale(translations);
        List<LocaleCoverageFact> coverage = new ArrayList<>();
        List<LocaleKeyRef> missing = new ArrayList<>();
        List<LocaleKeyRef> sourceChanged = new ArrayList<>();
        List<LocalizationProblem> problems = new ArrayList<>();
        for (String locale : locales) {
            Map<Long, I18nTranslation> texts = byLocale.getOrDefault(locale, Map.of());
            if (LocalizationCodes.BASE_LOCALE.equals(locale)) {
                coverage.add(new LocaleCoverageFact(locale, required.contains(locale), activeKeys.size(),
                    activeKeys.size(), 0, 100, true));
                continue;
            }
            coverage.add(localeCoverage(locale, activeKeys, texts, required.contains(locale), threshold));
            collect(locale, activeKeys, texts, missing, sourceChanged, problems);
        }
        return new ReleaseAssessment(activeKeys.size(), coverage, missing, sourceChanged, problems);
    }

    /** 按语言与 key 索引翻译。 */
    static Map<String, Map<Long, I18nTranslation>> byLocale(Collection<I18nTranslation> translations) {
        Map<String, Map<Long, I18nTranslation>> result = new HashMap<>();
        for (I18nTranslation translation : translations) {
            result.computeIfAbsent(translation.getLocaleCode(), ignored -> new HashMap<>())
                .put(translation.getKeyId(), translation);
        }
        return result;
    }

    /**
     * 一个非 en 语言的覆盖率。
     *
     * @param locale 语言
     * @param keys ACTIVE key
     * @param texts 该语言翻译
     * @param isRequired 是否必须覆盖
     * @param threshold 阈值
     * @return 覆盖率
     */
    static LocaleCoverageFact localeCoverage(String locale, List<I18nKey> keys, Map<Long, I18nTranslation> texts,
        boolean isRequired, int threshold) {
        int translated = 0;
        int changed = 0;
        for (I18nKey key : keys) {
            I18nTranslation translation = texts.get(key.getKeyId());
            if (translation == null) {
                continue;
            }
            translated++;
            if (translation.getBasedOnSourceRevision() < key.getSourceRevision()) {
                changed++;
            }
        }
        int total = keys.size();
        int percent = total == 0 ? 100 : translated * 100 / total;
        boolean satisfied = !isRequired || (long) translated * 100 >= (long) threshold * total;
        return new LocaleCoverageFact(locale, isRequired, total, translated, changed, percent, satisfied);
    }

    /** 收集一个语言的未翻译、基准已变更与 ICU 问题。 */
    static void collect(String locale, List<I18nKey> keys, Map<Long, I18nTranslation> texts,
        List<LocaleKeyRef> missing, List<LocaleKeyRef> sourceChanged, List<LocalizationProblem> problems) {
        for (I18nKey key : keys) {
            I18nTranslation translation = texts.get(key.getKeyId());
            if (translation == null) {
                missing.add(new LocaleKeyRef(locale, key.getKeyCode()));
                continue;
            }
            if (translation.getBasedOnSourceRevision() < key.getSourceRevision()) {
                sourceChanged.add(new LocaleKeyRef(locale, key.getKeyCode()));
            }
            IcuMessageValidator.validateTranslation(key.getSourceText(), translation.getText())
                .forEach(problem -> problems.add(new LocalizationProblem(
                    "translations." + locale + "." + key.getKeyCode(), problem.message())));
        }
    }
}
