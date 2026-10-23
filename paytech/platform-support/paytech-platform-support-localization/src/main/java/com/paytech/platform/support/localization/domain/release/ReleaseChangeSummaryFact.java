package com.paytech.platform.support.localization.domain.release;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.paytech.platform.support.localization.domain.LocalizationCodes;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * 相对线上发布的变更摘要（主会话第三批第 8、9 项）；同时是 t_i18n_release.report.changeSummary 的 JSON 结构。
 * <li>key 变化按 en 计：新增、废弃（发布中不再出现）、英文基准变化；翻译变化按非 en 语言计新增、修改与移除的条数。各列最多 {@value #SAMPLE_LIMIT}
 * 个示例 key，按字典序。</li>
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ReleaseChangeSummaryFact {

    /** 每类示例 key 的上限。 */
    public static final int SAMPLE_LIMIT = 20;

    /** 比较基准的发布号；没有线上发布时为 null（与空内容比较）。 */
    private Integer baseReleaseNo;

    /** 新增 key 数。 */
    private int addedKeyCount;

    /** 新增 key 示例。 */
    private List<String> addedKeys;

    /** 废弃（不再发布）的 key 数。 */
    private int deprecatedKeyCount;

    /** 废弃 key 示例。 */
    private List<String> deprecatedKeys;

    /** 英文基准变化的 key 数。 */
    private int sourceChangedKeyCount;

    /** 英文基准变化的 key 示例。 */
    private List<String> sourceChangedKeys;

    /** 各非 en 语言的翻译变化，按语言代码排列；没有变化的语言不出现。 */
    private List<LocaleChangeFact> translationChanges;

    /**
     * 由两次内容的差异汇总。
     *
     * @param diff 基准到当前的差异
     * @param baseReleaseNo 基准发布号；没有时为 null
     * @return 摘要
     */
    public static ReleaseChangeSummaryFact of(ReleaseDiff diff, Integer baseReleaseNo) {
        ReleaseChangeSummaryFact fact = new ReleaseChangeSummaryFact();
        fact.baseReleaseNo = baseReleaseNo;
        List<String> added = baseKeys(diff.getAdded());
        List<String> deprecated = baseKeys(diff.getRemoved());
        List<String> changed = baseKeys(diff.getChanged());
        fact.addedKeyCount = added.size();
        fact.addedKeys = sample(added);
        fact.deprecatedKeyCount = deprecated.size();
        fact.deprecatedKeys = sample(deprecated);
        fact.sourceChangedKeyCount = changed.size();
        fact.sourceChangedKeys = sample(changed);
        fact.translationChanges = translationChanges(diff);
        return fact;
    }

    /** en 的变化 key，按字典序。 */
    static List<String> baseKeys(List<TextChange> changes) {
        return changes.stream().filter(change -> LocalizationCodes.BASE_LOCALE.equals(change.locale()))
            .map(TextChange::keyCode).sorted().toList();
    }

    /** 各非 en 语言的新增、修改与移除合计。 */
    static List<LocaleChangeFact> translationChanges(ReleaseDiff diff) {
        Map<String, List<String>> byLocale = new TreeMap<>();
        List<TextChange> all = new ArrayList<>(diff.getAdded());
        all.addAll(diff.getChanged());
        all.addAll(diff.getRemoved());
        for (TextChange change : all) {
            if (!LocalizationCodes.BASE_LOCALE.equals(change.locale())) {
                byLocale.computeIfAbsent(change.locale(), ignored -> new ArrayList<>()).add(change.keyCode());
            }
        }
        List<LocaleChangeFact> result = new ArrayList<>();
        byLocale.forEach((locale, keys) -> result.add(new LocaleChangeFact(locale, keys.size(),
            sample(keys.stream().sorted().toList()))));
        return result;
    }

    /** 前 {@value #SAMPLE_LIMIT} 个。 */
    static List<String> sample(List<String> keys) {
        return List.copyOf(keys.subList(0, Math.min(SAMPLE_LIMIT, keys.size())));
    }

    /** 一个语言的翻译变化。 */
    @Getter
    @Setter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LocaleChangeFact {

        /** 语言代码。 */
        private String locale;

        /** 新增、修改与移除的翻译条数。 */
        private int changedCount;

        /** 变化的 key 示例，按字典序，最多 20 个。 */
        private List<String> sampleKeys;
    }
}
