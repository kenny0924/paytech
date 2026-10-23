package com.paytech.platform.support.localization.auto.release;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.localization.core.dal.dataobject.I18nKey;
import com.paytech.platform.support.localization.core.enums.KeyStatus;
import com.paytech.platform.support.localization.domain.release.ReleaseChangeSummaryFact;
import com.paytech.platform.support.localization.domain.release.ReleaseChangeSummaryFact.LocaleChangeFact;
import com.paytech.platform.support.localization.domain.release.ReleaseContent;
import com.paytech.platform.support.localization.domain.release.ReleaseDiff;
import com.paytech.platform.support.localization.test.builder.LocalizationFixtures;

/**
 * 相对线上发布的变更摘要（主会话第三批第 8、9 项）：当前内容按发布口径展开；key 变化按 en 计新增、废弃与基准变化，翻译变化按非 en 语言计条数；
 * 示例最多 20 个且按字典序；没有线上发布时与空内容比较。
 *
 * @see ReleaseContent#current(List, java.util.Collection, java.util.Collection)
 * @see ReleaseChangeSummaryFact#of(ReleaseDiff, Integer)
 */
class ReleaseChangeSummaryTest {

    /** 当前内容：en 来自 ACTIVE key，翻译只取参与发布的语言，废弃 key 的翻译不计入。 */
    @Test
    void test_currentContent() {
        List<I18nKey> keys = List.of(key(1, "team.a", "A"), key(2, "team.b", "B"));
        Map<String, Map<String, String>> content = ReleaseContent.current(keys, List.of(
            LocalizationFixtures.translation(1, "zh-CN", "甲", 1), LocalizationFixtures.translation(2, "ar", "ب", 1),
            LocalizationFixtures.translation(9, "zh-CN", "废弃", 1)), List.of("zh-CN"));
        assertEquals(Map.of("en", Map.of("team.a", "A", "team.b", "B"), "zh-CN", Map.of("team.a", "甲")), content);
    }

    /** 相对线上发布：新增、废弃、基准变化与各语言翻译变化（含新增、修改与移除）。 */
    @Test
    void test_againstLiveRelease() {
        Map<String, Map<String, String>> live = Map.of(
            "en", Map.of("team.a", "A", "team.b", "B", "team.c", "C"),
            "zh-CN", Map.of("team.a", "甲", "team.c", "丙"));
        Map<String, Map<String, String>> current = Map.of(
            "en", Map.of("team.a", "A", "team.b", "Bee", "team.d", "D"),
            "zh-CN", Map.of("team.a", "甲甲", "team.d", "丁"));

        ReleaseChangeSummaryFact summary = ReleaseChangeSummaryFact.of(ReleaseDiff.between(live, current), 7);

        LocaleChangeFact zh = summary.getTranslationChanges().getFirst();
        assertAll(() -> assertEquals(7, summary.getBaseReleaseNo()),
            () -> assertEquals(1, summary.getAddedKeyCount()), () -> assertEquals(List.of("team.d"), summary.getAddedKeys()),
            () -> assertEquals(1, summary.getDeprecatedKeyCount()),
            () -> assertEquals(List.of("team.c"), summary.getDeprecatedKeys()),
            () -> assertEquals(1, summary.getSourceChangedKeyCount()),
            () -> assertEquals(List.of("team.b"), summary.getSourceChangedKeys()),
            () -> assertEquals(1, summary.getTranslationChanges().size()),
            () -> assertEquals("zh-CN", zh.getLocale()), () -> assertEquals(3, zh.getChangedCount()),
            () -> assertEquals(List.of("team.a", "team.c", "team.d"), zh.getSampleKeys()));
    }

    /** 没有线上发布时全部计为新增；示例最多 20 个，按字典序，计数仍为总数。 */
    @Test
    void test_noLiveReleaseAndSampleLimit() {
        Map<String, String> en = new HashMap<>();
        IntStream.range(0, 25).forEach(index -> en.put(String.format("team.k%02d", index), "T" + index));
        ReleaseChangeSummaryFact summary = ReleaseChangeSummaryFact.of(ReleaseDiff.between(Map.of(), Map.of("en", en)),
            null);
        assertAll(() -> assertNull(summary.getBaseReleaseNo()), () -> assertEquals(25, summary.getAddedKeyCount()),
            () -> assertEquals(ReleaseChangeSummaryFact.SAMPLE_LIMIT, summary.getAddedKeys().size()),
            () -> assertEquals("team.k00", summary.getAddedKeys().getFirst()),
            () -> assertEquals("team.k19", summary.getAddedKeys().getLast()),
            () -> assertEquals(0, summary.getDeprecatedKeyCount()),
            () -> assertEquals(List.of(), summary.getTranslationChanges()));
    }

    /** 测试 key。 */
    static I18nKey key(long id, String code, String text) {
        return LocalizationFixtures.key(id, LocalizationFixtures.TEAM_NAMESPACE_ID, code, text, KeyStatus.ACTIVE);
    }
}
