package com.paytech.platform.support.localization.auto.release;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.dal.dataobject.I18nTranslation;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.LocalizationProblem;
import com.paytech.platform.support.localization.domain.release.LocaleCoverageFact;
import com.paytech.platform.support.localization.domain.release.LocaleKeyRef;
import com.paytech.platform.support.localization.domain.release.ReleaseAssessment;
import com.paytech.platform.support.localization.domain.release.ReleaseAssessor;
import com.paytech.platform.support.localization.test.builder.LocalizationFixtures;

/**
 * 发布评估（技术方案 3.3 第 1 步、需求 R5、AC-04）：en 恒为 100%，必须覆盖语言按 translated × 100 ≥ 阈值 × total 判断，
 * 报告列出未翻译与基准已变更的 key，翻译与当前基准不一致时阻止发布。
 *
 * @see ReleaseAssessor#assess(List, java.util.Collection, List, Set, int)
 */
class ReleaseAssessorTest {

    private static final List<I18nKey> KEYS = List.of(
        key(1, "team.a", "A"), key(2, "team.b", "B {name}"), key(3, "team.c", "C"), key(4, "team.d", "D"));

    /** 3/4 = 75%：阈值 75 达标、阈值 76 不达标；非必须覆盖语言恒为 satisfied；基准已变更计入已翻译与报告。 */
    @Test
    void test_coverageThreshold() {
        List<I18nTranslation> translations = List.of(
            LocalizationFixtures.translation(1, "zh-CN", "甲", 1),
            LocalizationFixtures.translation(2, "zh-CN", "乙 {name}", 0),
            LocalizationFixtures.translation(3, "zh-CN", "丙", 1));
        ReleaseAssessment exact = assess(translations, 75);
        ReleaseAssessment above = assess(translations, 76);
        LocaleCoverageFact zh = exact.getCoverage().get(1);

        assertAll(
            () -> assertEquals(List.of("en", "zh-CN", "ar"), exact.getCoverage().stream()
                .map(LocaleCoverageFact::getLocale).toList()),
            () -> assertEquals(100, exact.getCoverage().getFirst().getCoveragePercent()),
            () -> assertEquals(3, zh.getTranslatedKeys()), () -> assertEquals(1, zh.getSourceChangedKeys()),
            () -> assertEquals(75, zh.getCoveragePercent()), () -> assertTrue(zh.isSatisfied()),
            () -> assertTrue(exact.publishable()),
            () -> assertFalse(above.publishable()),
            () -> assertEquals(List.of("zh-CN"), above.insufficient().stream().map(LocaleCoverageFact::getLocale)
                .toList()),
            () -> assertEquals(List.of(new LocaleKeyRef("zh-CN", "team.d")), above.blockingMissing()),
            () -> assertTrue(exact.getCoverage().get(2).isSatisfied()),
            () -> assertEquals(5, exact.getMissing().size()),
            () -> assertEquals(List.of(new LocaleKeyRef("zh-CN", "team.b")), exact.getSourceChanged()));
    }

    /** 翻译参数与当前基准不一致时阻止发布；没有 ACTIVE key 时不可发布但覆盖率为 100%。 */
    @Test
    void test_blockingProblems() {
        ReleaseAssessment mismatch = assess(List.of(LocalizationFixtures.translation(2, "zh-CN", "乙", 1)), 0);
        ReleaseAssessment empty = ReleaseAssessor.assess(List.of(), List.of(), List.of("en", "zh-CN"),
            Set.of("zh-CN"), 100);
        assertAll(
            () -> assertFalse(mismatch.publishable()),
            () -> assertEquals(List.of("translations.zh-CN.team.b"), mismatch.getProblems().stream()
                .map(LocalizationProblem::field).toList()),
            () -> assertFalse(empty.publishable()),
            () -> assertEquals(100, empty.getCoverage().get(1).getCoveragePercent()),
            () -> assertTrue(empty.getCoverage().get(1).isSatisfied()));
    }

    /** en、zh-CN（必须覆盖）、ar 三种语言的评估。 */
    static ReleaseAssessment assess(List<I18nTranslation> translations, int threshold) {
        return ReleaseAssessor.assess(KEYS, translations, List.of("en", "zh-CN", "ar"), Set.of("zh-CN"), threshold);
    }

    /** 测试 key。 */
    static I18nKey key(long id, String code, String text) {
        return LocalizationFixtures.key(id, LocalizationFixtures.TEAM_NAMESPACE_ID, code, text, KeyStatus.ACTIVE);
    }
}
