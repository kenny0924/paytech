package com.paytech.platform.support.localization.auto.release;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.paytech.platform.support.localization.domain.release.ReleaseDiff;
import com.paytech.platform.support.localization.domain.release.TextChange;

/**
 * 两次发布的差异（需求 R6、AC-10）：按语言与 key 的新增、变化与删除，按语言、key 字典序排列；没有基准发布时全部为新增。
 *
 * @see ReleaseDiff#between(Map, Map)
 */
class ReleaseDiffTest {

    /** 语言整体新增或消失也逐条列出。 */
    @Test
    void test_diff() {
        Map<String, Map<String, String>> base = Map.of(
            "en", Map.of("team.a", "A", "team.b", "B", "team.c", "C"),
            "fr", Map.of("team.a", "Un"));
        Map<String, Map<String, String>> target = Map.of(
            "en", Map.of("team.a", "A", "team.b", "Bee", "team.d", "D"),
            "zh-CN", Map.of("team.a", "甲"));

        ReleaseDiff diff = ReleaseDiff.between(base, target);

        assertAll(
            () -> assertEquals(List.of(new TextChange("en", "team.d", null, "D"),
                new TextChange("zh-CN", "team.a", null, "甲")), diff.getAdded()),
            () -> assertEquals(List.of(new TextChange("en", "team.b", "B", "Bee")), diff.getChanged()),
            () -> assertEquals(List.of(new TextChange("en", "team.c", "C", null),
                new TextChange("fr", "team.a", "Un", null)), diff.getRemoved()));
    }

    /** 没有基准发布时全部为新增；相同内容没有差异。 */
    @Test
    void test_noBase() {
        Map<String, Map<String, String>> target = Map.of("en", Map.of("team.b", "B", "team.a", "A"));
        ReleaseDiff first = ReleaseDiff.between(Map.of(), target);
        ReleaseDiff same = ReleaseDiff.between(target, target);
        assertAll(
            () -> assertEquals(List.of("team.a", "team.b"), first.getAdded().stream().map(TextChange::keyCode)
                .toList()),
            () -> assertEquals(List.of(), same.getAdded()), () -> assertEquals(List.of(), same.getChanged()),
            () -> assertEquals(List.of(), same.getRemoved()));
    }
}
