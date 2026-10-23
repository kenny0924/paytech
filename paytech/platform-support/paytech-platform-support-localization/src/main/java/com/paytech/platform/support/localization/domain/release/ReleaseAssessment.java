package com.paytech.platform.support.localization.domain.release;

import java.util.List;

import com.paytech.platform.support.localization.domain.LocalizationProblem;

import lombok.AllArgsConstructor;
import lombok.Getter;

/** 当前文案的发布评估：覆盖率、发布报告与阻止发布的 ICU 问题；预检与发布共用。 */
@Getter
@AllArgsConstructor
public class ReleaseAssessment {

    /** ACTIVE key 数。 */
    private final int activeKeyCount;

    /** 各语言覆盖率，en 在前，其余按语言排序值。 */
    private final List<LocaleCoverageFact> coverage;

    /** 未翻译而回退英文的 key（全部 ENABLED 非 en 语言）。 */
    private final List<LocaleKeyRef> missing;

    /** 基准已变更仍沿用旧翻译的 key。 */
    private final List<LocaleKeyRef> sourceChanged;

    /** 翻译与当前基准不一致等 ICU 问题。 */
    private final List<LocalizationProblem> problems;

    /** 未达到阈值的必须覆盖语言。 */
    public List<LocaleCoverageFact> insufficient() {
        return coverage.stream().filter(fact -> !fact.isSatisfied()).toList();
    }

    /** 未达到阈值的必须覆盖语言的未翻译 key。 */
    public List<LocaleKeyRef> blockingMissing() {
        List<String> locales = insufficient().stream().map(LocaleCoverageFact::getLocale).toList();
        return missing.stream().filter(ref -> locales.contains(ref.locale())).toList();
    }

    /** 是否可以发布：有 ACTIVE key、必须覆盖语言都达到阈值且没有 ICU 问题。 */
    public boolean publishable() {
        return activeKeyCount > 0 && insufficient().isEmpty() && problems.isEmpty();
    }

    /**
     * 发布报告。
     *
     * @param changeSummary 相对线上发布的变更摘要；无法计算时为 null
     * @return 报告
     */
    public ReleaseReportFact report(ReleaseChangeSummaryFact changeSummary) {
        return new ReleaseReportFact(missing, sourceChanged, changeSummary);
    }
}
