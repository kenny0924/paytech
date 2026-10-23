package com.paytech.platform.support.localization.domain.release;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.TreeSet;

import lombok.Getter;

/** 两次发布的语言包内容差异（需求 R6、AC-10）；按语言、key 字典序排列。纯 Java。 */
@Getter
public final class ReleaseDiff {

    /** 新增的文案。 */
    private final List<TextChange> added = new ArrayList<>();

    /** 变化的文案。 */
    private final List<TextChange> changed = new ArrayList<>();

    /** 删除的文案。 */
    private final List<TextChange> removed = new ArrayList<>();

    private ReleaseDiff() {
    }

    /**
     * 比较两次发布。
     *
     * @param base 基准发布：语言 → key → 文案；没有基准发布时传空映射
     * @param target 对比发布：语言 → key → 文案
     * @return 差异
     */
    public static ReleaseDiff between(Map<String, Map<String, String>> base, Map<String, Map<String, String>> target) {
        ReleaseDiff diff = new ReleaseDiff();
        TreeSet<String> locales = new TreeSet<>(base.keySet());
        locales.addAll(target.keySet());
        for (String locale : locales) {
            diff.compare(locale, new TreeMap<>(base.getOrDefault(locale, Map.of())),
                new TreeMap<>(target.getOrDefault(locale, Map.of())));
        }
        return diff;
    }

    /** 比较一个语言。 */
    void compare(String locale, TreeMap<String, String> before, TreeMap<String, String> after) {
        TreeSet<String> keys = new TreeSet<>(before.keySet());
        keys.addAll(after.keySet());
        for (String key : keys) {
            String old = before.get(key);
            String now = after.get(key);
            if (old == null) {
                added.add(new TextChange(locale, key, null, now));
            } else if (now == null) {
                removed.add(new TextChange(locale, key, old, null));
            } else if (!Objects.equals(old, now)) {
                changed.add(new TextChange(locale, key, old, now));
            }
        }
    }
}
